import os
# This file contains constant used in launch program. Note all file paths are inside the container.
SIM_PKG = "/etc/aws-iot-fleetwise/"
# Path to FleetWise Edge Application Binary
FWE_BINARY = "/usr/bin/aws-iot-fleetwise-edge"
# Path to FleetWise Edge Config file
FWE_CONFIG = "config.json"
# Path to start simulation
SIM_SCRIPT = "sim/kfarm_sim.py"
# can mapping file is stored inside the container to book keeping can mapping for the container
# Note currently it only support one vehicle per container
CAN_MAPPING_FILE = os.path.join(SIM_PKG, "can_mapping.json")