/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.util.Random;

import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator.PortAllocationContext;

public class PortAllocatorTestContext extends PortAllocationContext {
    public PortAllocatorTestContext() {
    }

    /**
     * Add simulated ports to a Port Allocation Context.
     * 
     * @param port -- StoragePort object
     * @param switchName -- Switch name
     */
    @Override
    public void addPort(StoragePort port, StorageHADomain haDomain,
            StorageSystem.Type arrayType, String switchName, Long usage) {
        port.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
        port.setNetwork(this._initiatorNetwork.getId());
        String portName = port.getPortName();
        String portGroup = port.getPortGroup();
        haDomain = new StorageHADomain();
        StorageSystem.Type type = StorageSystem.Type.vnxblock;
        if (port.getPortName().startsWith("FA-")) {
            haDomain.setNativeGuid("SYMMETRIX+" + portName);
        } else if (portGroup != null && portGroup.startsWith("director-")) {
            haDomain.setNativeGuid("VPLEX+" + port.getPortGroup());
        } else if (portGroup.startsWith("X")) {
            haDomain.setNativeGuid("XTREMIO+" + port.getPortGroup());
        } else {
            haDomain.setNativeGuid("VNX+" + portName);
        }

        if (portName.startsWith("SP_A")) {
            haDomain.setSlotNumber("1");
        } else if (portName.startsWith("SP_B")) {
            haDomain.setSlotNumber("2");
        } else if (portName.startsWith("SP_C")) {
            haDomain.setSlotNumber("3");
        } else if (portName.startsWith("SP_D")) {
            haDomain.setSlotNumber("4");
        }
        else if (portName.startsWith("FA-")) {
            type = StorageSystem.Type.vmax;
            int index;
            for (index = 3; index < portName.length(); index++) {
                // Stop on first non-digit after FA-
                if (Character.isDigit(portName.charAt(index)) == false) {
                    break;
                }
            }
            haDomain.setSlotNumber(portName.substring(3, index));
        } else if (portName.startsWith("X")) {
            haDomain.setAdapterName(portGroup);
            type = StorageSystem.Type.xtremio;
        } else {
            haDomain.setSlotNumber("0");
        }

        if (portGroup != null) {
            if (portGroup.equals("director-1-1-A")) {
                haDomain.setSlotNumber("0");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-1-1-B")) {
                haDomain.setSlotNumber("1");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-1-2-A")) {
                haDomain.setSlotNumber("2");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-1-2-B")) {
                haDomain.setSlotNumber("3");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-2-1-A")) {
                haDomain.setSlotNumber("8");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-2-1-B")) {
                haDomain.setSlotNumber("9");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-2-2-A")) {
                haDomain.setSlotNumber("10");
                type = StorageSystem.Type.vplex;
            } else if (portGroup.equals("director-2-2-B")) {
                haDomain.setSlotNumber("11");
                type = StorageSystem.Type.vplex;
            }
            haDomain.setName(portGroup);
        }

        Random random = new Random();
        usage = (long) random.nextInt(10);

        super.addPort(port, haDomain, type, switchName, usage);
    }
}
