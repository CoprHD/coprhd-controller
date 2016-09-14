/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.List;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StorageVolume;

public class IBMSVCQueryAllStorageVolumeCommand extends
        AbstractIBMSVCQueryCommand<IBMSVCQueryAllStorageVolumeResult> {

    public static final String STORAGE_VOLUME_PARAMS_INFO = "StorageVolumeInfo";
    
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", STORAGE_VOLUME_PARAMS_INFO)
    };

    public IBMSVCQueryAllStorageVolumeCommand() {
        addArgument("svcinfo lsvdisk -delim : -nohdr");
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setSuccess(false);
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    void beforeProcessing() {
        results = new IBMSVCQueryAllStorageVolumeResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        
        switch (spec.getPropertyName()) {

            case STORAGE_VOLUME_PARAMS_INFO:
                String[] volumeData = capturedStrings.get(0).split(":");
                StorageVolume storageVolume = new StorageVolume();
                storageVolume.setStoragePoolId(volumeData[5]);
                storageVolume.setNativeId(volumeData[0]);
                storageVolume.setDisplayName(volumeData[1]);
                storageVolume.setDeviceLabel(volumeData[1]);
                storageVolume.setAllocatedCapacity(IBMSVCDriverUtils.extractFloat(volumeData[7]));
                storageVolume.setAccessStatus(AccessStatus.READ_WRITE);
                storageVolume.setProvisionedCapacity(IBMSVCDriverUtils.extractFloat(volumeData[7]));
                storageVolume.setRequestedCapacity(IBMSVCDriverUtils.extractFloat(volumeData[7]));
                storageVolume.setWwn(volumeData[13]);
                int spaceEfficientCount = Integer.parseInt(volumeData[17]);
                if (spaceEfficientCount > 0)
                    storageVolume.setThinlyProvisioned(true);
                else
                    storageVolume.setThinlyProvisioned(false);
                
                results.addStorageVolume(storageVolume);
                results.setSuccess(true);
                break;
        }
    }
}
