package com.amazonaws.iot.fleetwise.vehiclesimulator.ecs;

import com.amazonaws.iot.fleetwise.vehiclesimulator.SimulationMetaData;
import com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions.EcsTaskManagerException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.CapacityProviderStrategyItem;
import software.amazon.awssdk.services.ecs.model.ContainerOverride;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.StopTaskRequest;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.TaskOverride;

public final class EcsTaskManager {

    private final EcsClient ecsClient;

    private final String arch;

    private final String ecsClusterName;

    public static final int MAX_TASK_PER_RUN_TASK_REQUEST = 10;
    public static final int MAX_TASK_PER_WAIT_TASK = 100;

    private final Logger log= LoggerFactory.getLogger(EcsTaskManager.class);

    public EcsTaskManager( EcsClient ecsClient,  String arch,  String ecsClusterName) {
        this.ecsClient = ecsClient;
        this.arch = arch;
        this.ecsClusterName = ecsClusterName;
    }

    public Map<String, String>  runTasks( List<SimulationMetaData> vehicleSimulationMap,  String ecsTaskDefinition, boolean useCapacityProvider,
                                          String ecsLaunchType,
                                          String ecsCapacityProviderName,
                                          Map<String, String> tags,
                                          Duration waiterTimeout,
                                          int waiterRetries) throws EcsTaskManagerException {
        this.log.info("Run Tasks on ECS Cluster: {} with task definition: {}", this.ecsClusterName , ecsTaskDefinition);
        if (useCapacityProvider) {
            this.log.info("Use Capacity Provider: {}", ecsCapacityProviderName);
        }
        Map<String, String> pendingTaskArnList = new HashMap<>();
        vehicleSimulationMap.forEach( vehicleIDToSimUrl -> {
                    // We pass the vehicle ID and simulation URL through environment variable.
                    List<KeyValuePair> envList = new ArrayList<>();
                    envList.add(KeyValuePair.builder().name("VEHICLE_ID").value(vehicleIDToSimUrl.getVehicleId()).build());
                    envList.add(KeyValuePair.builder().name("S3_BUCKET").value(vehicleIDToSimUrl.getS3().getBucket()).build());
                    envList.add(KeyValuePair.builder().name("S3_KEY").value(vehicleIDToSimUrl.getS3().getKey()).build());
                    tags.forEach((key, value) -> envList.add(KeyValuePair.builder().name(key).value(value).build()));
                    ContainerOverride containerOverride = ContainerOverride.builder()
                            .environment(envList)
                            .name("fwe-" + arch)
                            .build();
                    // Build the task override
                    List<ContainerOverride> containerOverrideList = new ArrayList<>();
                    containerOverrideList.add(containerOverride);
                    TaskOverride taskOverride = TaskOverride.builder()
                            .containerOverrides(containerOverrideList)
                            .build();
                    // Check whether we want to use Capacity Provider. If not, use "EC2" as launch type
                    RunTaskRequest.Builder taskBuilder;
                    if (useCapacityProvider) {
                        taskBuilder = RunTaskRequest.builder().capacityProviderStrategy(
                                CapacityProviderStrategyItem.builder().capacityProvider(ecsCapacityProviderName)
                                        .build());
                    } else {
                        taskBuilder = RunTaskRequest.builder().launchType(ecsLaunchType);
                    }
                    // Build the runTaskRequest
                    tags.put("vehicleID", vehicleIDToSimUrl.getVehicleId());
                    tags.put("s3Bucket", vehicleIDToSimUrl.getS3().getBucket());
                    tags.put("s3Key", vehicleIDToSimUrl.getS3().getKey());

                    List<Tag> tagList = tags.entrySet().stream()
                            .map(it -> Tag.builder().key(it.getKey()).value(it.getValue()).build())
                            .collect(Collectors.toList());
                    RunTaskRequest runTaskRequest = taskBuilder
                            .cluster(ecsClusterName)
                            .count(1)
                            .taskDefinition(ecsTaskDefinition)
                            .overrides(taskOverride)
                            .tags(tagList)
                            .build();
                    List<String> taskArnList = ecsClient.runTask(runTaskRequest).tasks().stream().map(Task::taskArn).collect(Collectors.toList());
                    if (taskArnList.size() == 1) {
                        pendingTaskArnList.put(vehicleIDToSimUrl.getVehicleId(), taskArnList.get(0));
                    } else {
                        log.error("vehicle {} RunTaskResponse contains {} tasks: {}", vehicleIDToSimUrl.getVehicleId(), taskArnList.size(), taskArnList);
                    }
                }
        );
        log.info("Starting Task: {}", pendingTaskArnList);
        log.info("Wait for all tasks running");
        if (pendingTaskArnList.size() != vehicleSimulationMap.size()) {
                throw new EcsTaskManagerException("Failed to create all tasks", pendingTaskArnList);
        }
        // Maximum number of tasks per ecsClient waiter is 100.
        // Hence, we need to chunk the large task lists into smaller lists before requesting wait
        // Note although the waiters runs one by one, the loop latency should be small
        // as all the tasks have already been created by runTask in previous step.
        // We only return the Task Arns that are successfully running
        AtomicInteger counter = new AtomicInteger();
        Map<Integer, List<Map.Entry<String, String>>> listOfChunks = pendingTaskArnList.entrySet().stream()
                .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / MAX_TASK_PER_WAIT_TASK));
        Map<String, String> map = new HashMap<>();
        listOfChunks.forEach((item, value) -> {
            WaiterResponse<DescribeTasksResponse> waiterResponse =
                ecsClient.waiter().waitUntilTasksRunning(
                        DescribeTasksRequest.builder().cluster(ecsClusterName).tasks(value.stream().map(Map.Entry::getValue).collect(Collectors.toList())).build()
                ,WaiterOverrideConfiguration.builder().waitTimeout(waiterTimeout).maxAttempts(waiterRetries).build());
            Map<String, String> taskArnToVehicleIDMap = value.stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
            // We only return the Task Arns that are successfully running
            waiterResponse.matched().response().get().tasks().stream().filter(it ->
                "RUNNING".equals(it.lastStatus())).forEach(i -> {
                    map.put(taskArnToVehicleIDMap.get(i.taskArn()), i.taskArn());
                });
        });
        return map;
        }


    public List<String> stopTasks( List<String> taskIDList,  Duration waiterTimeout, int waiterRetries) {
        List<String> stoppingTaskIDs = new ArrayList<>();
        taskIDList.forEach(
                it -> {
                    ecsClient.stopTask( StopTaskRequest.builder().cluster(ecsClusterName)
                                    .task(it)
                                    .reason("stop the task").build()
                    );
                    stoppingTaskIDs.add(it);
                }
        );
        this.log.info("Waiting for tasks to be stopped: {} ", stoppingTaskIDs);
        // Maximum number of tasks per ecsClient waiter is 100.
        // Hence, we need to chunk the large task lists into smaller lists before requesting wait
        // Note although the waiters runs one by one, the loop latency should be small
        // as the stop task requests have been sent out previously.
        List<String> stoppedTaskIDsAll = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        Map<Integer, List<String>> listOfChunks = stoppingTaskIDs.stream()
                .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / MAX_TASK_PER_WAIT_TASK));
        listOfChunks.forEach((key, value) -> {
            WaiterResponse<DescribeTasksResponse> waiterResponse = ecsClient.waiter().waitUntilTasksStopped(
                    builder -> builder.cluster(ecsClusterName)
                                .tasks(value).build()
                    , builder -> builder.waitTimeout(waiterTimeout).maxAttempts(waiterRetries).build()
            );
            List<String> stoppedTaskIDs = waiterResponse.matched().response().get().tasks().stream()
                    .filter(itt ->
                            // only include task shown last status as STOPPED
                            "STOPPED".equals(itt.lastStatus()) &&
                                    // Filter by target taskIDList in case response contains non target task ID
                                    taskIDList.contains(itt.taskArn().substring(itt.taskArn().lastIndexOf('/') + 1))
                    ).map(i -> i.taskArn().substring(i.taskArn().lastIndexOf('/') + 1))
                    .toList();
            stoppedTaskIDsAll.addAll(stoppedTaskIDs);
            });
        // Check whether we have stopping tasks that fail to end up as STOPPED
        List<String> failToStopTaskIDs = new ArrayList<>(stoppingTaskIDs);
        failToStopTaskIDs.removeAll(stoppedTaskIDsAll);
        List<String> taskIDListCopy = new ArrayList<>(taskIDList);
        taskIDListCopy.removeAll(failToStopTaskIDs);
        return taskIDListCopy;
     }

}
