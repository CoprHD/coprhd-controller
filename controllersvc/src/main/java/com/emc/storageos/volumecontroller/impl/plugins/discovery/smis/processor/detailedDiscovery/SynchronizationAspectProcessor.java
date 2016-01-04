/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

/**
 * Process synchronization aspects (EMC_SynchronizationAspectForSource instances).
 */
public class SynchronizationAspectProcessor extends StorageProcessor {
    private final Logger _logger = LoggerFactory
            .getLogger(SynchronizationAspectProcessor.class);
    private static String SOURCE_ELEMENT = "SourceElement";
    private static String SYNC_TYPE = "SyncType";

    private AccessProfile _profile;
    private Map<String, Map<String, String>> _syncAspectMap;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.debug("Calling SynchronizationAspectProcessor");
        _profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        _syncAspectMap = new HashMap<String, Map<String, String>>();

        processResultbyChunk(resultObj, keyMap);
        keyMap.put(Constants.SNAPSHOT_NAMES_SYNCHRONIZATION_ASPECT_MAP,
                _syncAspectMap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int processInstances(Iterator<CIMInstance> instances) {
        return processInstances(instances, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int processInstances(Iterator<CIMInstance> instances, WBEMClient client) {
        int count = 0;
        while (instances.hasNext()) {
            try {
                count++;
                CIMInstance instance = instances.next();
                String srcElement = getCIMPropertyValue(instance, SOURCE_ELEMENT);
                CIMObjectPath srcPath = new CIMObjectPath(srcElement);
                String serialId = srcPath.getKey(Constants.SYSTEMNAME).getValue().toString();

                // skip synchronization aspects belonging to other arrays
                if (!serialId.contains(_profile.getserialID())) {
                    continue;
                }

                String syncType = getCIMPropertyValue(instance, SYNC_TYPE);
                if (!SYNC_TYPE_SNAPSHOT.equals(syncType)) {
                    continue;
                }

                String srcNativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(srcPath);
                String elementName = getCIMPropertyValue(instance, Constants.ELEMENTNAME);

                Map<String, String> aspectsForSource = null;
                if (_syncAspectMap.containsKey(srcNativeGuid)) {
                    aspectsForSource = _syncAspectMap.get(srcNativeGuid);
                } else {
                    aspectsForSource = new HashMap<String, String>();
                    _syncAspectMap.put(srcNativeGuid, aspectsForSource);
                }
                aspectsForSource.put(getSyncAspectMapKey(srcNativeGuid, elementName),
                        instance.getObjectPath().getKeyValue(Constants.INSTANCEID).toString());
            } catch (Exception e) {
                _logger.error("Exception on processing instances", e);
            }
        }

        return count;
    }
}
