#Vehicle Simulator

This package provides user an easy way to create a fleet of virtual vehicles. 

The following diagram illustrates a high-level architecture of the system.

<img src="./assets/Architectural%20Diagram.png" />

#User Guide

## Build
`brazil-build`

## Simulation Package
simulation package is used to inject virtual ECU data to vehicles. It should contains the following folder structure
```
Top Folder
    |_ car1
        |_ private key
        |_ certificate
        |_ config.json
        |_ sim
            |_ simulation scripts

```
The Top folder url should be supplied to `LaunchVehicles` command

## Init
Refresh AWS credential
```
ada credentials update --account 763496144766 --role Admin --once
```

## Create Vehicles
Use option `LaunchVehicles` with `-s` followed by simulation package url and `-r` followed by region
```
brazil-runtime-exec vehicle-simulator LaunchVehicles -s s3://fwe-simulator-poc/simulation/ -r us-west-2
```

## Stop Vehicles
Use option `StopVehicles` and supply task ID following `-t` and `-r` followed by region. If there's multiple task IDs, use multiple `-t`
```
brazil-runtime-exec vehicle-simulator StopVehicles -t task1 -t task2 -t task3 -r us-west-2
```

