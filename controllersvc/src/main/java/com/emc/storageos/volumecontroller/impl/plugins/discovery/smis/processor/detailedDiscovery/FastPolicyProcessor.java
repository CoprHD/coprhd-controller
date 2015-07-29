/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.FASTPolicyProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This processor is responsible for discovering VNX and VNAX fast policies.
 */
public class FastPolicyProcessor extends Processor {
    private Logger log = LoggerFactory.getLogger(FastPolicyProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;

            while (it.hasNext()) {
                final CIMObjectPath policyObjectPath = it.next();
                String systemName = policyObjectPath.getKey(Constants.SYSTEMNAME).getValue().toString();

                if (!systemName.contains((String) keyMap.get(Constants._serialID))) {
                    continue;
                }
                String[] array = systemName.split(Constants.PATH_DELIMITER_REGEX);
                String policyRuleName = policyObjectPath.getKey(Constants.POLICYRULENAME)
                        .getValue().toString();
                log.info("Policy Name {}", policyRuleName);
                String policyKey = validateFastPolicy(array[0], policyRuleName);
                if (null != policyKey) {
                    log.info("Adding Policy Object Path {}", policyObjectPath);
                    addPath(keyMap, policyKey, policyObjectPath);
                }

            }
        } catch (Exception e) {
            log.error("Fast Policy discovery failed during UnManaged Volume discovery", e);
        }

    }

    public static String validateFastPolicy(String arrayType, String policyRuleName) {
        if (Constants.CLARIION.equalsIgnoreCase(arrayType)) {
            return Constants.VNXFASTPOLICIES;
        }
        if (Constants.SYMMETRIX.equalsIgnoreCase(arrayType)
                && !FASTPolicyProcessor.GlobalVMAXPolicies.contains(policyRuleName)) {
            return Constants.VMAXFASTPOLICIES;
        }
        return null;

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
    }

}
