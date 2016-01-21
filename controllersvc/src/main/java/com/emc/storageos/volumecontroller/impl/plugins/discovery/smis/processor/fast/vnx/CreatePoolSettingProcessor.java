/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vnx;

import java.util.List;
import java.util.Map;
import javax.cim.CIMArgument;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * refer Comments in CheckPoolSettingsExistenceProcessor, and get in here
 * 
 * For each successfully created Setting from expected Settings List to be created :
 * this processor gets invoked.
 * Goal :
 * For each Successfully created Setting generate the below mapping
 * 1. CreatedSetting_CIMObjectPath_TierMethodlogy : TierMethodology (2 or 0 or 6 or 7)
 * 2. CreatedSetting_CIMObjectPath : PoolCapabilitiesCIMPath (on which this Setting is created)
 * 3. Add CreatedSettingPath into Map, so that the next SMI-S call getSettinsgsInstance, runs above
 * the list.(runs only above successfully created Settings.)
 * 
 * 
 */
public class CreatePoolSettingProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(CreatePoolSettingProcessor.class);
    private List<Object> _args;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        String tierMethodologyToBeUsedForThisCreatedSetting = null;
        try {
            if (resultObj instanceof CIMArgument<?>[]) {
                CIMArgument<?>[] outputArguments = (CIMArgument<?>[]) resultObj;
                CIMObjectPath path = (CIMObjectPath) outputArguments[0].getValue();
                // always set
                int index = (Integer) _args.get(1);
                @SuppressWarnings("unchecked")
                List<String> poolSettingsList = (List<String>) keyMap
                        .get(Constants.VNXPOOLCAPABILITIES_TIER);
                String poolSettingToTierMethodology = poolSettingsList.get(index);
                tierMethodologyToBeUsedForThisCreatedSetting = poolSettingToTierMethodology.substring(
                        poolSettingToTierMethodology.lastIndexOf(Constants.HYPHEN) + 1, poolSettingToTierMethodology.length());

                String poolCapabilitiesPathAssociatedWiththisSetting = poolSettingToTierMethodology.substring(0,
                        poolSettingToTierMethodology.lastIndexOf(Constants.HYPHEN));

                keyMap.put(path.toString(), poolCapabilitiesPathAssociatedWiththisSetting);
                keyMap.put(path.toString() + Constants.HYPHEN + Constants.TIERMETHODOLOGY,
                        tierMethodologyToBeUsedForThisCreatedSetting);
                addPath(keyMap, operation.getResult(), path);
            }
        } catch (Exception e) {
            _logger.error(
                    "Error processing Create Pool Setting with Initial Storage Tiering Methodology : {} : ",
                    tierMethodologyToBeUsedForThisCreatedSetting, e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
