/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
    private Map<String, Set<String>> _duplicateSyncAspectElementNameMap;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.debug("Calling SynchronizationAspectProcessor");
        _profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        _syncAspectMap = new HashMap<String, Map<String, String>>();
        _duplicateSyncAspectElementNameMap = new HashMap<String, Set<String>>();

        processResultbyChunk(resultObj, keyMap);

        keyMap.put(Constants.SNAPSHOT_NAMES_SYNCHRONIZATION_ASPECT_MAP, _syncAspectMap);
        keyMap.put(Constants.DUPLICATE_SYNC_ASPECT_ELEMENT_NAME_MAP, _duplicateSyncAspectElementNameMap);
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
        Map<String, Set<String>> processedNameMap = new HashMap<String, Set<String>>();
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

                // ViPR currently does not support SnapVx generation names. Generation
                // names allow the user to use the same name for all array snapshots
                // for a given source volume and assign a unique generation number to
                // differentiate them. Therefore, if we encounter a synchronization aspect
                // with the same element name this means this array snapshot is using
                // generation names and we will not ingest it. In this case, make sure
                // the aspect is not placed in the aspect map. This will prevent these
                // aspects from being ingested as BlockSnapshotSession instances in ViPR.
                // Also add the duplicate name to the duplicate aspect element name map.
                // When the replication relationship processor runs we will use the
                // duplicate names map to determine if a snapshot target volume is linked
                // to an invalid aspect, which in turn will prevent that linked target
                // from being ingested as a BlockSnapshot instance in ViPR.
                String srcNativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(srcPath);
                String elementName = getCIMPropertyValue(instance, Constants.ELEMENTNAME);
                boolean isDuplicateElementNameForSrc = false;
                if (processedNameMap.containsKey(srcNativeGuid)) {
                    Set<String> elementNamesForSrc = processedNameMap.get(srcNativeGuid);
                    if (elementNamesForSrc.contains(elementName)) {
                        _logger.info("Processed duplicate synchronization aspect element name {} for source {}",
                                elementName, srcNativeGuid);
                        Set<String> duplicateElementNamesForSrc;
                        if (_duplicateSyncAspectElementNameMap.containsKey(srcNativeGuid)) {
                            duplicateElementNamesForSrc = _duplicateSyncAspectElementNameMap.get(srcNativeGuid);
                        } else {
                            duplicateElementNamesForSrc = new HashSet<String>();
                            _duplicateSyncAspectElementNameMap.put(srcNativeGuid, duplicateElementNamesForSrc);
                        }
                        duplicateElementNamesForSrc.add(elementName);
                        isDuplicateElementNameForSrc = true;
                    } else {
                        elementNamesForSrc.add(elementName);
                    }
                } else {
                    Set<String> elementNamesForSrc = new HashSet<String>();
                    elementNamesForSrc.add(elementName);
                    processedNameMap.put(srcNativeGuid, elementNamesForSrc);
                }

                Map<String, String> aspectsForSource;
                String aspectKey = getSyncAspectMapKey(srcNativeGuid, elementName);
                if (!isDuplicateElementNameForSrc) {
                    if (_syncAspectMap.containsKey(srcNativeGuid)) {
                        aspectsForSource = _syncAspectMap.get(srcNativeGuid);
                    } else {
                        aspectsForSource = new HashMap<String, String>();
                        _syncAspectMap.put(srcNativeGuid, aspectsForSource);
                    }
                    aspectsForSource.put(aspectKey, instance.getObjectPath().getKeyValue(Constants.INSTANCEID).toString());
                } else {
                    aspectsForSource = _syncAspectMap.get(srcNativeGuid);
                    aspectsForSource.remove(aspectKey);
                }
            } catch (Exception e) {
                _logger.error("Exception on processing instances", e);
            }
        }

        return count;
    }
}
