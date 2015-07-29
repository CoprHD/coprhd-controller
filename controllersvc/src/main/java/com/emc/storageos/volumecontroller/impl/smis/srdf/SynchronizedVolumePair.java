/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf;

import javax.cim.CIMObjectPath;

import com.emc.storageos.plugins.common.Constants;

import static com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot;

/**
 * Given a StorageSynchronized CIMObjectPath, this class generates ViPR native GUID's for both the
 * source and target volume elements.
 * 
 * Created by bibbyi1 on 4/15/2015.
 */
public class SynchronizedVolumePair {
    private static final String SYSTEM_ELEMENT = "SystemElement";
    private static final String SYNCED_ELEMENT = "SyncedElement";
    private static final String SYSTEM_NAME = "SystemName";
    private static final String DEVICE_ID = "DeviceID";

    private CIMObjectPath storageSynchronized;
    private String sourceGUID;
    private String targetGUID;

    public SynchronizedVolumePair(CIMObjectPath storageSynchronized) {
        this.storageSynchronized = storageSynchronized;
    }

    public String getSourceGUID() {
        if (sourceGUID == null) {
            sourceGUID = generateGUID(SYSTEM_ELEMENT);
        }
        return sourceGUID;
    }

    public String getTargetGUID() {
        if (targetGUID == null) {
            targetGUID = generateGUID(SYNCED_ELEMENT);
        }
        return targetGUID;
    }

    private String generateGUID(String elementType) {
        CIMObjectPath elementPath = (CIMObjectPath) storageSynchronized.getKey(elementType).getValue();
        String systemName = ((String) elementPath.getKey(SYSTEM_NAME).getValue())
                .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
        String deviceID = (String) elementPath.getKey(DEVICE_ID).getValue();

        return generateNativeGuidForVolumeOrBlockSnapShot(systemName, deviceID);
    }
}
