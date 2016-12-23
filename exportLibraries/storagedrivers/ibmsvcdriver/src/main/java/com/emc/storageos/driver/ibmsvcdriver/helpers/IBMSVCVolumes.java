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
package com.emc.storageos.driver.ibmsvcdriver.helpers;

import com.emc.storageos.driver.ibmsvcdriver.api.IBMSVCCLI;
import com.emc.storageos.driver.ibmsvcdriver.api.IBMSVCCreateVolumeResult;
import com.emc.storageos.driver.ibmsvcdriver.api.IBMSVCGetVolumeResult;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.driver.ibmsvcdriver.exceptions.IBMSVCDriverException;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCStorageDriver;
import com.emc.storageos.storagedriver.model.StorageVolume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IBMSVCVolumes {

    private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);

    /**
     * Query IBM SVC Volume by Volume ID
     * @param connection
     * @param volumeID
     * @return
     * @throws IBMSVCDriverException
     */
    public static IBMSVCGetVolumeResult queryStorageVolume(SSHConnection connection, String volumeID) throws IBMSVCDriverException{
        IBMSVCGetVolumeResult resultGetVolume = IBMSVCCLI.queryStorageVolume(connection,
                volumeID);

        if (resultGetVolume.isSuccess()) {
            _log.info(String.format("Query storage volume Id %s.\n",
                    resultGetVolume.getProperty("VolumeId")));
            return resultGetVolume;
        }else{
            throw new IBMSVCDriverException(String.format("Processing get storage volume Id %s failed %s\n",
                    resultGetVolume.getProperty("VolumeId"), resultGetVolume.getErrorString()));
        }

    }

    /**
     * Create Storage Volume
     * @param connection
     * @param targetStorageVolume
     * @param formatBeforeUse
     * @param createMirrorCopy
     * @return
     * @throws IBMSVCDriverException
     */
    public static IBMSVCCreateVolumeResult createStorageVolumes(SSHConnection connection, StorageVolume targetStorageVolume, boolean formatBeforeUse, boolean createMirrorCopy)throws IBMSVCDriverException{
        // 2. Create a new Clone Volume with details supplied
        IBMSVCCreateVolumeResult resultCreateVol = IBMSVCCLI.createStorageVolumes(connection,
                targetStorageVolume, formatBeforeUse, createMirrorCopy);

        if (resultCreateVol.isSuccess()) {
            _log.info(String.format("Created storage clone volume %s (%s) size %s\n",
                    resultCreateVol.getName(), resultCreateVol.getId(),
                    resultCreateVol.getRequestedCapacity()));
            return resultCreateVol;
        } else {
            throw new IBMSVCDriverException(String.format("Creating storage volume failed %s - %s%n",
                    resultCreateVol.getErrorString(), resultCreateVol.getErrorString()));
        }


    }

}
