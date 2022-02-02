#Vehicle Simulator

This package provides user an easy way to create a fleet of virtual vehicles. 

The following diagram illustrates a high-level architecture of the system.

<img src="./assets/Architectural%20Diagram.png" />

#User Guide

## Build
`brazil-build`

## Create Vehicles
1. Refresh AWS credential
`ada credentials update --account 763496144766 --role Admin --once`
2. Run brazil runtime exec
`brazil-runtime-exec vehicle-simulator LaunchVehicles -s s3://fwe-simulator-poc/simulation/`

## Stop Vehicles


