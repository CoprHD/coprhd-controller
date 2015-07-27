/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkCollectionException;
import com.emc.storageos.networkcontroller.impl.NetworkDiscoveryWorker;
import com.emc.storageos.networkcontroller.impl.NetworkSystemDevice;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;

public class NetworkCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private NetworkSystemDevice _device;
    private static final Logger _log = LoggerFactory
            .getLogger(NetworkCommunicationInterface.class);

    public void setDevice(NetworkSystemDevice _device) {
        this._device = _device;
    }

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
    public void discover(AccessProfile accessProfile)
            throws BaseCollectionException {
        try {
            _log.info("Access Profile Details :" + accessProfile.toString());
            NetworkDiscoveryWorker worker = new NetworkDiscoveryWorker(_device, _dbClient);
            worker.setCoordinator(_coordinator);
            worker.verifyVersion(accessProfile.getSystemId());
            worker.updatePhysicalInventory(accessProfile.getSystemId());
        } catch(Exception e) {
            throw NetworkDeviceControllerException.exceptions.discoverNetworkSystemFailed(e);
        }
    }    
}
