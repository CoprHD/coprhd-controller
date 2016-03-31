/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.eventhandler.connectemc;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.impl.licensing.LicenseInfoExt;
import com.emc.storageos.systemservices.impl.licensing.LicenseManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.vipr.model.sys.eventhandler.Device;

/**
 * 
 * This class is responsible for building the ESRS Configuration File.
 * 
 */
public class BuildEsrsDevice {

    private String _networkIpAddress;
    private LicenseManager _licenseManager;
    // Model prefix required by ESRS.
    private final static String MODEL_NAME_SUFFIX = "-GM";
    @Autowired
    private CoordinatorClientExt _coordinator;

    /**
     * Build the Device object using the ViPR Controller license information.
     * 
     * @return
     * @throws LocalRepositoryException
     * @throws Exception
     */
    public Device build() throws Exception {

        Device device = new Device();
        LicenseInfoExt licenseInfo = _licenseManager.getLicenseInfoFromCoordinator(LicenseType.CONTROLLER);
        buildDevice(licenseInfo, device);
        return device;
    }

    /**
     * Build the Device object using the controller license information.
     * 
     * @param feature
     * @param device
     * @throws LocalRepositoryException
     */
    private void buildDevice(LicenseInfoExt licenseInfo, Device device)
            throws LocalRepositoryException {

        if (licenseInfo != null) {
            LocalRepository localRepository = LocalRepository.getInstance();
            // this is in the format of node1, node2, etc. We need to get the integer portion.
            String nodeId = _coordinator.getPropertyInfo().getProperties().get("node_id");
            String node;
            if (nodeId != null) {
                node = nodeId.substring(4);
            } else {
                node = CallHomeConstants.STANDALONE;
            }
            device.setSerialNumber(licenseInfo.getProductId() + "-" + node);
            device.setModelName(getBaseModelId(licenseInfo.getLicenseType().toString()) + MODEL_NAME_SUFFIX);
            device.setIpAddress(_networkIpAddress);
        }
    }

    /**
     * Truncate from the underscore through the end of the model id.
     * 
     * @param String modelId
     * @return String
     */
    private String getBaseModelId(String modelId) {
        String[] split = modelId.split("_");
        return split[0];
    }

    /**
     * Set Networked eth0 Ip Address.
     * 
     * @param String networkIpAddress
     */
    @Autowired
    public void setNetworkIpAddress(String networkIpAddress) {
        _networkIpAddress = networkIpAddress;
    }

    /**
     * Set LicenseManager.
     * 
     * @param licenseManager
     */
    public void setLicenseManager(LicenseManager licenseManager) {
        _licenseManager = licenseManager;
    }
}