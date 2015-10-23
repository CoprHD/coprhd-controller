/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * Processor responsible for processing ReplicationGroup's.
 */
public class ReplicationGroupToVolumeProcessor extends StorageProcessor {
    private static Logger _logger = LoggerFactory.getLogger(ReplicationGroupToVolumeProcessor.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {

        @SuppressWarnings("unchecked")
        final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
        Set<String> volumePaths = new HashSet<String>();
        try {
            while (it.hasNext()) {
                CIMObjectPath path = it.next();
                String volumeNativeGuid = getVolumeNativeGuid(path);

            }

        } catch (Exception e) {
            _logger.error("Discovering Volumes part of Replication Group failed.", e);
        }
    }

}
