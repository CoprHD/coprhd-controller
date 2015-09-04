/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.util.Iterator;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/*
 * Processor responsible for Adding StorageSystem CoP to KeyMap.
 * It also updates the SMISProvider connectionStatus.
 * All systems will be persisted in performBookkeeping only after completion of all provider scanning. 
 */
public class ScannerProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(ScannerProcessor.class);
    private static final String EMC_LOCALITY = "EMCLocality";
    private static final String REMOTE = "3";

    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        AccessProfile profile = null;
        try {
            profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            while (it.hasNext()) {
                try {
                    final CIMInstance instance = it.next();
                    final CIMObjectPath path = instance.getObjectPath();
                    if (isIBMInstance(instance) || path.toString().toLowerCase().contains(Constants.CLARIION) || !isRemoteSystem(instance)) {
                        addPath(keyMap, operation.get_result(), path);
                    } else {
                        _logger.info("Skipping Detection of Remote System {}", instance.getPropertyValue(Constants.NAME));
                    }

                } catch (Exception e) {
                    _logger.error("Scanner Failed to scan Provider : {}-->{}",
                            profile.getserialID(), getMessage(e));
                }
            }
        } catch (Exception e) {
            _logger.error("Scanner Failed to scan Provider : {}-->{}",
                    profile.getIpAddress(), getMessage(e));
        }
    }

    private boolean isRemoteSystem(CIMInstance instance) {
        String locality = instance.getPropertyValue(EMC_LOCALITY).toString();
        if (REMOTE.equalsIgnoreCase(locality)) {
            return true;
        }
        return false;

    }
}
