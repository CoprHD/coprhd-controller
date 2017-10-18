/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * processor to get Device Type (vnx or vmax, or IBM XIV) for StorageSystem.
 */
public class ModelProcessor extends Processor {
    private static final String TAG = "Tag";
    private static final String VNXBLOCK = "vnxblock";
    private static final String VNX = "vnx";
    private static final String VMAX = "vmax";
    private static final String POWERMAX = "powermax"; // Dell Name for new VMAX
    private static final String MODEL = "Model";
    private static final String TYPE_DESC = "ChassisTypeDescription";

    private Logger _logger = LoggerFactory.getLogger(ModelProcessor.class);

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        CIMInstance modelInstance = null;
        try {
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            Map<String, StorageSystemViewObject> storageSystemsCache = (Map<String, StorageSystemViewObject>) keyMap
                    .get(Constants.SYSTEMCACHE);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            String deviceType = null;
            while (it.hasNext()) {
                modelInstance = it.next();
                String model = modelInstance.getPropertyValue(MODEL).toString();
                // TODO should CIM_Chassis.SerialNumber be used instead of Tag?
                String tag = modelInstance.getPropertyValue(TAG).toString();
                String serialID = null;
                if (isIBMInstance(modelInstance)) {
                    String typeDesc = modelInstance.getPropertyValue(TYPE_DESC).toString();
                    if (Constants.XIV.equalsIgnoreCase(typeDesc)) {
                        deviceType = DiscoveredDataObject.Type.ibmxiv.name();
                        serialID = tag; // e.g., IBM.2810-7825363
                    } else {
                        _logger.warn(
                                "Array {} is of model {} ---> not XIV, hence will not be added to ViPR",
                                tag, model);
                        continue;
                    }
                } else {
                    if (model.toLowerCase().contains(VNX)) {
                        deviceType = VNXBLOCK;
                    } else if (model.toLowerCase().contains(VMAX)) {
                        deviceType = VMAX;
                    } else if (model.toLowerCase().contains(POWERMAX)) {
                        deviceType = VMAX;
                    } else {
                        _logger.warn(
                                "Array {} is of model {} ---> neither VMAX nor VNX, hence will not be added to ViPR",
                                tag, model);
                        continue;
                    }

                    serialID = tag.split(Constants.PATH_DELIMITER_REGEX)[1];
                }

                keyMap.put(Constants.ARRAYTYPE, deviceType);
                StorageSystemViewObject systemVO = null;
                String nativeGuid = NativeGUIDGenerator.generateNativeGuid(deviceType, serialID);

                if (storageSystemsCache.containsKey(nativeGuid)) {
                    systemVO = storageSystemsCache.get(nativeGuid);
                } else {
                    systemVO = new StorageSystemViewObject();
                }
                systemVO.setDeviceType(deviceType);
                systemVO.addprovider(profile.getSystemId().toString());
                systemVO.setProperty(StorageSystemViewObject.MODEL, model);
                systemVO.setProperty(StorageSystemViewObject.SERIAL_NUMBER, serialID);
                systemVO.setProperty(StorageSystemViewObject.STORAGE_NAME, nativeGuid);
                storageSystemsCache.put(nativeGuid, systemVO);
            }
            _logger.info("Found {} systems during scanning for ip {}", storageSystemsCache.size(),
                    profile.getIpAddress());
            resultObj = null;
        } catch (Exception e) {
            _logger.error(e.getMessage(), e);
            _logger.error("Model Extraction failed for {}-->{}",
                    modelInstance.getObjectPath(), getMessage(e));
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
