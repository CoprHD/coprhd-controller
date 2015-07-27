/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.util.List;
import java.util.Map;

import javax.cim.CIMArgument;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.SupportedResourceTypes;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * Processor used in calculating the Volume Max and Min sizes for Storage Pool
 * On storagePool instance, call intrinsic Method "getSupportedSizeRange", the result
 * would be passed into this Processor Class, in the form of CIMArgument[].
 * Only Device & Unified & Virtual Pools will be used.
 * 
 */
public class VolumeSizeProcessor extends PoolProcessor {
    private static final String FIVE = "5";
    private static final String THREE = "3";
    private static String MINIMUM_VOLUME_SIZE = "minimumvolumesize";
    private static String MAXIMUM_VOLUME_SIZE = "maximumvolumesize";
    private List<Object> _args;
    private Logger _logger = LoggerFactory.getLogger(VolumeSizeProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        DbClient _dbClient;
        AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        try {
            String minVolSize = null;
            String maxVolSize = null;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            StorageSystem device = getStorageSystem(_dbClient,
                    profile.getSystemId());
            if (resultObj instanceof CIMArgument<?>[]) {
                CIMArgument<?>[] outputArguments = (CIMArgument<?>[]) resultObj;
                for (CIMArgument<?> outArg : outputArguments) {
                    if (null == outArg)
                        continue;
                    if (outArg.getName().equalsIgnoreCase(MINIMUM_VOLUME_SIZE)) {
                        minVolSize = outArg.getValue().toString();
                    } else if (outArg.getName().equalsIgnoreCase(MAXIMUM_VOLUME_SIZE)) {
                        maxVolSize = outArg.getValue().toString();
                    }
                }
                // we are setting at compile time, hence value will be there always.
                CIMObjectPath poolObjectPath = getObjectPathfromCIMArgument();
                String instanceID = poolObjectPath.getKey(Constants.INSTANCEID).getValue().toString();
                
                StoragePool pool = checkStoragePoolExistsInDB(
                        getNativeIDFromInstance(instanceID), _dbClient, device);
                if (null != pool) {
                    Long maxVolumeSize = ControllerUtils.convertBytesToKBytes(maxVolSize);
                    Long minVolumeSize = ControllerUtils.convertBytesToKBytes(minVolSize);
                    if (Type.ibmxiv.name().equals((device.getSystemType()))) {
                        String supportedResourceType = pool.getSupportedResourceTypes();
                        if (SupportedResourceTypes.THIN_ONLY.name().equals(supportedResourceType)) {
                            pool.setMaximumThinVolumeSize(maxVolumeSize);
                            pool.setMinimumThinVolumeSize(minVolumeSize); 
                        }
                        else if (SupportedResourceTypes.THICK_ONLY.name().equals(supportedResourceType)) {
                            pool.setMaximumThickVolumeSize(maxVolumeSize);
                            pool.setMinimumThickVolumeSize(minVolumeSize);    
                        }
                    }
                    else { // TODO - could this be changed to use the same logic as for IBM pool?
                        // if the result is obtained from calling on Thick, use thick volume size else thin
                        String elementType = determineCallType();
                        if (elementType.equalsIgnoreCase(FIVE)) {
                            pool.setMaximumThinVolumeSize(maxVolumeSize);
                            pool.setMinimumThinVolumeSize(minVolumeSize);
                        } else if (elementType.equalsIgnoreCase(THREE)) {
                            pool.setMaximumThickVolumeSize(maxVolumeSize);
                            pool.setMinimumThickVolumeSize(minVolumeSize);
                        }
                    }
                    
                    _logger.info(String.format("Maximum limits for volume capacity in storage pool: %s  %n   max thin volume capacity: %s, max thick volume capacity: %s ",
                            pool.getId(), pool.getMaximumThinVolumeSize(), pool.getMaximumThickVolumeSize()));

                    _dbClient.persistObject(pool);
                }
            }
        } catch (Exception e) {
            _logger.error("Failed while processing Result : ", e);
        }
    }

    /**
     * we are setting these arguments at compile time, hence
     * will be always right
     * return 2nd Argument in inputArguments used to
     * call this SMI-S call.
     * 
     * @return
     */
    private String determineCallType() {
        Object[] arguments = (Object[]) _args.get(0);
        CIMArgument<?>[] argsArray = (CIMArgument<?>[]) arguments[2];
        CIMArgument<?> elementType = argsArray[4];
        return elementType.getValue().toString();
    }

    /**
     * return 1st Argument in inputArguments used to
     * call this SMI-S call.
     * 
     * @return
     */
    private CIMObjectPath getObjectPathfromCIMArgument() {
        Object[] arguments = (Object[]) _args.get(0);
        return (CIMObjectPath) arguments[0];
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
