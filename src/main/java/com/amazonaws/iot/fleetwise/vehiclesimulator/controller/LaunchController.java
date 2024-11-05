package com.amazonaws.iot.fleetwise.vehiclesimulator.controller;

import com.amazonaws.iot.fleetwise.vehiclesimulator.bean.LaunchVehicle;
import com.amazonaws.iot.fleetwise.vehiclesimulator.bean.StopVehicles;
import com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions.EcsTaskManagerException;
import com.amazonaws.iot.fleetwise.vehiclesimulator.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LaunchController {

    @Autowired
    VehicleService launchVehicleService;

    @PostMapping("/launchVehicle")
    public void launchVehicle(@RequestBody LaunchVehicle launchVehicle) throws EcsTaskManagerException {
        Integer launchedVehicle =launchVehicleService.launchVehicle(launchVehicle);
        System.out.println(launchedVehicle);

    }

    @PostMapping("/stopVehicle")
    public void stopVehicle(StopVehicles launchVehicle) throws EcsTaskManagerException {
        Integer launchedVehicle =launchVehicleService.stopVehicle(launchVehicle);
        System.out.println(launchedVehicle);

    }
}
