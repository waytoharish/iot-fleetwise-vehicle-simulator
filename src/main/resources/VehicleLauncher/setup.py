import os 
import os.path
from random import random
import sys
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))

from .download_s3 import *
from .can_setup import *

class setup:
    def __init__(self, dst_file_loc):
        self.vehicle_id = self.get_vehicle_ID()
        self.dst_file_loc = dst_file_loc
        # Initialize network setup as None
        self.__nw_stp = None
        self.setup_env()
        
    def get_vehicle_ID(self):
        vehicle_raw = os.environ['VEHICLE_ID']
        vehicle_id = str(vehicle_raw)
        return vehicle_id

    def setup_env(self):
        get_sim_packages(self.vehicle_id, os.environ['S3_BUCKET'], os.environ['S3_KEY'], self.dst_file_loc)
        self.set_network_setup()
    
    def set_network_setup(self):
        self.__nw_stp  = can_setup(self.vehicle_id, self.dst_file_loc)
        try:        
            self.__nw_stp.setup_can()
        except Exception as e:
            self.clean_network_setup()
            try:
                #Trying again after cleaning the network setup. Introduce a random number as nonce
                self.__nw_stp.setup_can(str(random))
            except Exception as e:
                print ("Error setting up the network as {}".format(e))
                raise

    def clean_network_setup(self):
        if self.__nw_stp is not None:
            self.__nw_stp.cleanup_can()
        else:
            print("Didn't perform network cleanup because there's no network setup")

    def __del__(self):
        pass

if __name__=='__main__':
    setup_obj = setup("/etc/aws-iot-fleetwise/")
    setup_obj.setup_env()
