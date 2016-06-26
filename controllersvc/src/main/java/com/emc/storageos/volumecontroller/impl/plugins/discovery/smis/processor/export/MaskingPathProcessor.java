/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * This processor is responsible for discovering VNX and VNAX lun masking paths.
 */
public class MaskingPathProcessor extends Processor {
    private Logger log = LoggerFactory.getLogger(MaskingPathProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            String serialIdsStr = profile.getProps().get(Constants.SYSTEM_SERIAL_IDS);
            String[] serialIds = serialIdsStr.split(Constants.ID_DELIMITER);
            Set<String> serialIdSet = new HashSet<String>(Arrays.asList(serialIds));

            Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            while (it.hasNext()) {
                CIMObjectPath path = it.next();
                String systemName = path.getKeyValue(SmisConstants.CP_SYSTEM_NAME).toString();
                String serialId = systemName.split(Constants.PATH_DELIMITER_REGEX)[1];
                if (serialIdSet.contains(serialId)) {
                    addPath(keyMap, operation.getResult(), path);
                }
            }
        } catch (Exception e) {
            log.error("Fast Policy discovery failed during UnManaged Volume discovery", e);
        }
    }
}
