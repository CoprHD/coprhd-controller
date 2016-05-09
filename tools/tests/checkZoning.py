#!/usr/bin/python
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#


import sys
import subprocess


ini = sys.argv[1]

initiator = ''.join(ini.split(":"))
storagePorts = ["50:00:09:82:C9:9B:97:89","50:00:09:82:8F:52:45:6E",
                "50:00:09:82:3D:31:1B:6F","50:00:09:82:85:DE:6B:5F",
                "50:00:09:82:C9:9B:97:89", "50:00:09:82:B1:99:68:08",
                "50:00:09:82:98:72:33:CB", "50:00:09:82:FD:64:8F:59",
                "50:00:09:82:A4:5F:FC:23", "50:00:09:82:4E:E4:55:AE",
                "50:00:09:82:AD:D1:E5:35", "50:00:09:82:AD:B0:D0:C0",
                "50:00:09:82:3F:63:56:2E", "50:00:09:82:61:8A:C3:36",
                "50:00:09:82:3F:5C:ED:43", "50:00:09:82:28:08:AA:6E",
                "50:00:09:82:35:98:94:DC", "50:00:09:82:18:99:A4:AF",
                "50:00:09:82:AE:70:F6:15", "50:00:09:82:AA:31:97:98",
                "50:00:09:82:DB:68:93:7D"]
# storagePort = ''.join(sto.split(":"))

cmd="sshpass -p dangerous ssh -q -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null root@10.247.96.243 cat /cisco-sim/db/zonesets.db"
batcmd="/opt/storageos/bin/dbutils list FCZoneReference"

result = subprocess.check_output(batcmd, shell=True)
switch = subprocess.check_output(cmd, shell=True)
usedStoragePort =""

try:

    if initiator in result:
        for storagePort in storagePorts:
            tempStoragePORT = storagePort
            if ''.join(tempStoragePORT.split(":")) in result:
                usedStoragePort = storagePort
                print("Initiator: "+ ini + " and Storage Port: "
                        + storagePort + " are in FCZoneReference")
    else:
        raise Exception("No matching Initiator and Storage Port in FCZoneReference")

    if ini in switch and usedStoragePort in switch:
        print("Initiator: " + ini + " and Storage Port: "
              + usedStoragePort + " are in the Switch")
    else:
        raise Exception("No matching Initiator and Storage Port in the switch")


except IOError:
    print("Failed Zoning!")

finally:
    print("Zones created")