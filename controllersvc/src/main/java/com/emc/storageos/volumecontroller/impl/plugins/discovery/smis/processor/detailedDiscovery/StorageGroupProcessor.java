/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;

/**
 * This processor responsible for processing of all StorageGroups created on array
 * and find the volumes which are associated.
 * 
 * It also skips the SG's which are already processed in LunMaskingProcessor to avoid
 * duplicate calls to the provider which means this processor responsible to set SLO Name
 * for the unexported volumes.
 * 
 */
public class StorageGroupProcessor extends StorageProcessor {
    private Logger logger = LoggerFactory.getLogger(StorageGroupProcessor.class);
    private List<Object> _args;
    private DbClient dbClient;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        @SuppressWarnings("unchecked")
        final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
        WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
        Map<String, String> volumesWithSLO = null;
        dbClient = (DbClient) keyMap.get(Constants.dbClient);
        AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        URI systemId = profile.getSystemId();
        List<CIMObjectPath> processedSGs = new ArrayList<>();

        if (!keyMap.containsKey(Constants.VOLUMES_WITH_SLOS)) {
            volumesWithSLO = new HashMap<>();
            keyMap.put(Constants.VOLUMES_WITH_SLOS, volumesWithSLO);
        } else {
            volumesWithSLO = (Map<String, String>) keyMap.get(Constants.VOLUMES_WITH_SLOS);
        }

        try {
            StorageSystem device = dbClient.queryObject(StorageSystem.class, systemId);
            // Process these only for VMAX3 Systems.
            if (device.checkIfVmax3()) {

                if (keyMap.containsKey(Constants.STORAGE_GROUPS_PROCESSED)) {
                    processedSGs = (List<CIMObjectPath>) keyMap.get(Constants.STORAGE_GROUPS_PROCESSED);
                }
                while (it.hasNext()) {
                    CIMObjectPath path = it.next();
                    if (null != processedSGs && processedSGs.contains(path)) {
                        logger.info("Skipping the already processed SG. {}", path);
                        continue;
                    }
                    findVolumesSLOFromSGInstance(client, path, volumesWithSLO);
                }
            }
        } catch (Exception e) {
            logger.error("Extracting storageGroup details failed.", e);
        } finally {
            // clean all the processedSGs here.
            processedSGs.clear();
        }
    }

    /**
     * Find the volumes associated with the SG and extract the SLOName and set
     * it in keyMap.
     * 
     * @param client
     *            - WBEMClient
     * @param path
     *            - SG CoP.
     * @param volumesWithSLO
     *            - Volumes with SLO Names.
     */
    private void findVolumesSLOFromSGInstance(WBEMClient client, CIMObjectPath path, Map<String, String> volumesWithSLO) {
        try {
            CIMInstance instance = client.getInstance(path, false, true, SmisConstants.PS_HOST_IO);
            String fastSetting = SmisUtils.getSLOPolicyName(instance);
            CloseableIterator<CIMObjectPath> volPaths = client.associatorNames(path, null, Constants.STORAGE_VOLUME, null, null);
            while (volPaths.hasNext()) {
                CIMObjectPath volPath = volPaths.next();
                String volumeNativeGuid = getVolumeNativeGuid(volPath);
                // Allow overwriting a previous entry if fast setting is non-null.
                if (!volumesWithSLO.containsKey(volumeNativeGuid) || fastSetting != null) {
                    logger.debug("Volume key: {} fastSetting: {}", volumeNativeGuid, fastSetting);
                    volumesWithSLO.put(volumeNativeGuid, fastSetting);
                }
            }
        } catch (Exception e) {
            logger.warn("Finding unexported volume SLOName failed during unmanaged volume discovery", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
