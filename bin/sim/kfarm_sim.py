#!/usr/bin/python3

import time
import os
import pathlib
import cansim


def playback():
    print("SIMULATION_SCRIPT: start simulation")
    can_sim = cansim.cansim(
        interface="vcan0",
        database_filename=os.path.join(pathlib.Path(
            __file__).parent.absolute(), 'kia-ev6-broadcast-can-v4.dbc'),
        values_filename=os.path.join(pathlib.Path(
            __file__).parent.absolute(), 'kia-ev6-broadcast-default.json')
    )

    simulation_cycle_cnt = 0
    while simulation_cycle_cnt < 999990:
        # Initialize signals
        can_sim.set_sig('hasActiveDTC', 0)
        can_sim.set_sig('StateOfHealth', 100)
        can_sim.set_sig('BatteryMaxTemperature', 20)
        can_sim.set_sig('BatteryMinTemperature', 17)
        can_sim.set_sig('MaxCellVoltage', 4.0)
        can_sim.set_sig('MinCellVoltage', 3.8)

        # 5 seconds MaxCellVoltage, MinCellVoltage toggling between 3.8 and 4.2 V
        for i in range(1, 10):
            if (i % 2) == 0:
                can_sim.set_sig('MaxCellVoltage', 4.1)
                can_sim.set_sig('MinCellVoltage', 3.90)
            else:
                can_sim.set_sig('MaxCellVoltage', 4.15)
                can_sim.set_sig('MinCellVoltage', 3.85)
            time.sleep(0.5)
        can_sim.set_sig('MaxCellVoltage', 4.2)
        can_sim.set_sig('MinCellVoltage', 3.8)

        # 5 seconds BatteryMaxTemperature, BatteryMinTemperature toggling between 14 and 23 degC
        for i in range(1, 10):
            if (i % 2) == 0:
                can_sim.set_sig('BatteryMaxTemperature', 21)
                can_sim.set_sig('BatteryMinTemperature', 16)
            else:
                can_sim.set_sig('BatteryMaxTemperature', 22)
                can_sim.set_sig('BatteryMinTemperature', 15)
            time.sleep(0.5)
        can_sim.set_sig('MaxCellVoltage', 4.2)
        can_sim.set_sig('BatteryMaxTemperature', 23)
        can_sim.set_sig('BatteryMinTemperature', 14)

        # 5 seconds idle
        time.sleep(5)

        # Increment the cycle counter
        simulation_cycle_cnt += 1

    print("SIMULATION_SCRIPT: stop simulation")
    can_sim.stop()


if __name__ == "__main__":
    playback()
