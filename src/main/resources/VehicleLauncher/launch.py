import os 
import sys
import subprocess
import signal
import sys
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from .setup import setup
from .config import SIM_PKG
from .config import FWE_BINARY
from .config import FWE_CONFIG
from .config import SIM_SCRIPT

class launch_container():
    #ENABLE_ROS_SCRIPT="source /app/install/setup.bash && export CYCLONEDDS_URI='<CycloneDDS><Domain><General><NetworkInterfaceAddress>lo</></></></>'"

    def __init__(self):
        pass 

    def __run_setup(self):
        rep_cmd = "mkdir -p {}".format(SIM_PKG) 
        res = subprocess.check_output(rep_cmd, shell=True)
        self.__setup = setup(SIM_PKG)
        self.vehicle_id = self.__setup.vehicle_id

    def __run_fleetwise(self):
        print ("[LAUNCH] : Starting FleetWise Edge")
        self.__fw_cmd = f"{FWE_BINARY} {SIM_PKG}{self.vehicle_id}/{FWE_CONFIG} &"
        proc = subprocess.Popen(self.__fw_cmd, shell = True)
        return proc

    def __run_simulation(self):
        print ("[LAUNCH] : Starting Simulation")
        self.__sim_path = os.path.join(SIM_PKG, self.vehicle_id, SIM_SCRIPT)
        self.sim_cmd = "python3 {}".format(self.__sim_path)
        proc = subprocess.Popen(self.sim_cmd,shell=False)
        return proc

    def launch(self):
        self.__run_setup()
        print ("[LAUNCH] : Setup Completed")
        try:
            self.fw_proc = self.__run_fleetwise()
        except Exception as e:
            self.__setup.clean_network_setup()
            raise
        try:
            self.sim_proc = self.__run_simulation()
        except Exception as e:
            self.__setup.clean_network_setup()
            raise            
        while True:
            #check if the simulation has finished
            self.sim_proc.poll()
            if self.sim_proc.returncode is not None:
                print ("[LAUNCH] : Simulation has ended")
                self.__setup.clean_network_setup()
                os.killpg(os.getpgid(self.fw_proc.pid), signal.SIGHUP)
                os.killpg(os.getpgid(self.fw_proc.pid), signal.SIGTERM)
                break

    def __del__(self):
        pass

if __name__=='__main__':
    launch_container_obj = launch_container()
    launch_container_obj.launch()
