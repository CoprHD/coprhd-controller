/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computecontroller.impl.ComputeDeviceController;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;

public class ComputeCommunicationInterface extends ExtendedCommunicationInterfaceImpl {

    ComputeDeviceController deviceController;

    public void setDeviceController(ComputeDeviceController controller) {
        deviceController = controller;
    }

    private static final Logger _log = LoggerFactory
            .getLogger(ComputeCommunicationInterface.class);

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        // do nothing
    }

    @Override
    public void scan(AccessProfile accessProfile)
            throws BaseCollectionException {
        // do nothing
    }

    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        try {
            _log.info("Access Profile Details :" + accessProfile.toString());

            deviceController.discoverComputeSystem(accessProfile.getSystemId());

        } catch (Exception e) {
            throw ComputeSystemControllerException.exceptions.discoverFailed("", e);
        }
    }
}
