import os
import json
import hashlib
import subprocess
from .config import CAN_MAPPING_FILE
from .config import FWE_CONFIG

DEFAULT_HASHING_LEN = 8
class can_setup:
    def __init__(self, vehicle_id, sim_loc):
        self.__hash_len = DEFAULT_HASHING_LEN
        self.__vehicle_id = vehicle_id
        self.__sim_loc = sim_loc
        self.__can_mapping  = None
        self.__config = None
        self.__can_channels = set()

    def __save_can_mapping(self, can_mapping):
        file_loc = CAN_MAPPING_FILE
        print ("generate can mapping: " + str(can_mapping))
        try:
            with open(file_loc, 'w') as f:
                json.dump(can_mapping, f)
                print ("Saved the can mapping at {}".format(file_loc))
                self.__can_mapping = can_mapping
        except Exception as e:
            print ("Error in Save can mapping for location {}".format(file_loc))
            raise

    def __save_config_file(self):
        try:
            file_loc = os.path.join(self.__sim_loc, f"{self.__vehicle_id}/{FWE_CONFIG}")
            with open(file_loc, 'w') as cfg_file:
                json.dump(self.__config, cfg_file)
                print ("Saved the modified config at {}".format(file_loc))
        except Exception as e:
            print ("Error Saving Config for {} at {} as {}".format(self.__vehicle_id, file_loc, e))
            raise

    def __generate_channel_name_hash(self, channel_name):
        encoded_name = hashlib.md5(channel_name.encode('utf-8')).hexdigest()
        return encoded_name[:self.__hash_len]

    def __generate_channel_name(self, channel_name : str, nonce : str):
        if channel_name is None or channel_name == '':
            print ("Channel Name is Empty")
            raise TypeError 
        channel_name = self.__generate_channel_name_hash(self.__vehicle_id + channel_name + nonce)
        return channel_name

    def __update_can_channels(self, nonce):
        can_channel_map = {}
        self.__can_channels.clear()
        network_interfaces = self.__config['networkInterfaces']
        for idx, network_interface in enumerate(network_interfaces):
            try:
                if network_interface['type'] == "canInterface":
                    can_channel_name = self.__generate_channel_name(network_interface['canInterface']['interfaceName'], nonce)
                    self.__can_channels.add(can_channel_name)
                    can_channel_map[network_interface['canInterface']['interfaceName']] = can_channel_name
                    self.__config['networkInterfaces'][idx]['canInterface']['interfaceName'] = can_channel_name
                elif network_interface['type'] == "obdInterface":
                    can_channel_name = self.__generate_channel_name(network_interface['obdInterface']['interfaceName'], nonce)
                    self.__can_channels.add(can_channel_name)
                    can_channel_map[network_interface['obdInterface']['interfaceName']] = can_channel_name
                    self.__config['networkInterfaces'][idx]['obdInterface']['interfaceName'] = can_channel_name
                elif network_interface['type'] == "ros2Interface":
                    print("Nothing to do for type ros2Interface")
                else:
                    print ("Unknown network interface type: {}".format(network_interface['type']))
                    raise 
            except KeyError:
                print ("Key Error in config file for Key {} for InterfaceID {}".format("interfaceName", network_interface['interfaceId']))
                raise
            except Exception as e:
                print ("Error in parsing config file for InterfaceID {} as {}".format(network_interface['interfaceId'], e))
                raise
        return can_channel_map

    # We use nonce to generate new can channel to avoid channel name collision as much as possible
    def setup_can(self, nonce = ""):
        self.read_config_file()
        can_channel_map = self.__update_can_channels(nonce)
        self.__save_can_mapping(can_channel_map)
        for can_channel in self.__can_channels:
            try:
                can_sys_cmd = "sudo ip link add dev {} type vcan && sudo ip link set up {}".format(can_channel, can_channel)
                print (can_sys_cmd)
                subprocess.check_output(can_sys_cmd, shell=True)
            except Exception as e:
                print ("Failed to create CAN channel {}  as  {}".format(can_channel, e))
                raise
        self.__save_config_file()

    def cleanup_can(self):
        for can_channel in self.__can_channels:
            try:
                del_can_sys_cmd = "sudo ip link delete {} ".format(can_channel)
                print (del_can_sys_cmd)
                subprocess.check_output(del_can_sys_cmd, shell=True)
            except Exception as e:
                print ("Failed to delete CAN channel {} as {}".format(can_channel))
        print ("Finished Deleting all the CAN channels")

    def get_can_mapping(self):
        return self.__can_mapping

    def read_config_file(self):
        try:
            file_loc = os.path.join(self.__sim_loc, self.__vehicle_id + "/config.json")
            with open(file_loc) as cfg:
                self.__config = json.load(cfg)
        except FileNotFoundError:
            print ("No Config Found for {} at {}".format(self.__vehicle_id, file_loc))
            raise FileNotFoundError
        except Exception as e:
            print ("Error Reading Config for {} at {} as {}".format(self.__vehicle_id, file_loc, e))
            raise 

    def get_config_file(self):
        return self.__config

    def get_can_channels(self):
        return self.__can_channels

    def __del__(self):
        pass

if __name__=='__main__':
    setup_obj = can_setup('car1', "/etc/aws-iot-fleetwise/")
    setup_obj.setup_can()

