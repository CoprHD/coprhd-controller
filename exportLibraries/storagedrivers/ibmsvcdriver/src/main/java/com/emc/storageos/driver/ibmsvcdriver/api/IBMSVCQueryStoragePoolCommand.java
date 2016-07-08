/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.List;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;

public class IBMSVCQueryStoragePoolCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryStoragePoolResult> {

    public static final String IBMSVC_POOLID = "PoolId";
    public static final String IBMSVC_POOLNAME = "PoolName";
    public static final String IBMSVC_POOLTIERTYPE = "PoolTierType";
    public static final String IBMSVC_POOLMDISKCOUNT = "PooLMDiskCount";
    
    public String tierType = "";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("id:(.*)", IBMSVC_POOLID),
            new ParsePattern("name:(.*)", IBMSVC_POOLNAME),
            new ParsePattern("tier:(.*)", IBMSVC_POOLTIERTYPE),
            new ParsePattern("tier_mdisk_count:(.*)", IBMSVC_POOLMDISKCOUNT)
    };

    public IBMSVCQueryStoragePoolCommand(String storagePoolName) {
        addArgument("svcinfo lsmdiskgrp -delim : " + storagePoolName);
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setSuccess(false);
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    void beforeProcessing() {
        results = new IBMSVCQueryStoragePoolResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            
            case IBMSVC_POOLID:
                String poolId = capturedStrings.get(0);
                results.setProperty(IBMSVC_POOLID, poolId);
                break;

            case IBMSVC_POOLNAME:
                String poolName = capturedStrings.get(0);
                results.setProperty(IBMSVC_POOLNAME, poolName);
                break;
                
            case IBMSVC_POOLTIERTYPE:
                tierType = capturedStrings.get(0);
                break;

            case IBMSVC_POOLMDISKCOUNT:
                String tierMDiskCount = capturedStrings.get(0);
                int mdiskCount = Integer.parseInt(tierMDiskCount);
                if (mdiskCount > 0 && tierType != "") {
                    results.addSupportedDriveTypes(tierType);
                }
                break;

        }
        results.setSuccess(true);
    }
}
