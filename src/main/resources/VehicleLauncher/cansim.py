#!/usr/bin/python3
import json
from canigen import Canigen
from config import CAN_MAPPING_FILE

class cansim(Canigen):
    def __init__(self, interface, output_filename=None, database_filename=None, values_filename=None, obd_config_filename=None):
        interface = self.check_can_hashing(interface)
        super().__init__(interface,
                        output_filename,
                        database_filename,
                        values_filename,
                        obd_config_filename)

    def check_can_hashing(self, interface):
        try:
            with open(CAN_MAPPING_FILE) as f:
                can_map = json.load(f)
                can_hashed_name = can_map[interface]
                return can_hashed_name
        except:
            print ("File can_mapping.json not found")
            raise FileNotFoundError
