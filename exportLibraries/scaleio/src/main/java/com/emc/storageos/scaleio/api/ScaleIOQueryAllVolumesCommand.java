/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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

package com.emc.storageos.scaleio.api;

import com.iwave.ext.command.CommandException;

import java.util.List;
import java.util.Stack;

public class ScaleIOQueryAllVolumesCommand extends AbstractScaleIOQueryCommand<ScaleIOQueryAllVolumesResult> {

    private static final String PROTECTION_DOMAIN = "ProtectionDomain";
    private static final String VOLUME_INFO = "VolumeInfo";
    private static final String PROTECTION_DOMAIN_1_30 = "ProtectionDoamin1_30";
    private static final String VOLUME_INFO_1_30 = "VolumeInfo1_30";
    private static final String STORAGE_POOL = "StoragePool";

    //    Query-all-volumes returned 2 Volumes.
//
//    Protection Domain: Name: PD-1 ID: b1a5a2ef00000000
//
//    >> Volume ID e9e515a300000000 Name: ABC Storage pool: Primary Size:8 GB (8192 MB)
//    Creation time: 2014-03-28 10:01:38
//    Volume is unmapped
//    Encryption is disabled
//    >> Volume ID e9e515a400000001 Name: unnamed Storage pool: Primary Size:8 GB (8192 MB)
//    Creation time: 2014-03-28 10:01:56
//    Volume is unmapped
//    Encryption is disabled
//
//
//    Protection Domain: Name: PD-2 ID: b1a5a35300000001
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Protection Domain: Name:\\s+(.*?)\\s+ID:\\s+(\\w+)", PROTECTION_DOMAIN),
            new ParsePattern("Protection Domain\\s+(\\w+)\\s+Name:\\s+(.*)", PROTECTION_DOMAIN_1_30),
            new ParsePattern("\\s*>>\\s+Volume ID\\s+(\\w+)\\s+Name:\\s+(.*?)\\s+Storage pool:\\s+(.*?)\\s+Size:" +
                    ScaleIOContants.REGEX_CAPACITY_NO_SPACE_IN_FRONT + ".*", VOLUME_INFO),
            new ParsePattern("\\s*Volume ID:\\s+(\\w+)\\s+Name:\\s+(.*?)\\s+Size:\\s+" +
                    ScaleIOContants.REGEX_CAPACITY_NO_SPACE_IN_FRONT + ".*", VOLUME_INFO_1_30),
            new ParsePattern("Storage Pool\\s+\\w+\\s+Name:\\s+(.*?)", STORAGE_POOL)
    };

    private Stack<String> lastProtectionDomain;
    private Stack<String> lastStoragePool;

    public ScaleIOQueryAllVolumesCommand() {
        addArgument("--query_all_volumes");
        results = new ScaleIOQueryAllVolumesResult();
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); //No need to check not null condition here
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setIsSuccess(false);
    }

    @Override
    void beforeProcessing() {
        lastProtectionDomain = new Stack<>();
        lastStoragePool = new Stack<>();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        String protectionDomainId;
        String protectionDomainName;
        String id;
        String name;
        String poolName;
        String volumeSizeStr;
        switch (spec.getPropertyName()) {
            case PROTECTION_DOMAIN:
                protectionDomainName = capturedStrings.get(0);
                protectionDomainId = capturedStrings.get(1);
                results.addProtectionDomain(protectionDomainId, protectionDomainName);
                lastProtectionDomain.push(protectionDomainId);
                break;
            case VOLUME_INFO:
                id = capturedStrings.get(0);
                name = capturedStrings.get(1);
                poolName = capturedStrings.get(2);
                volumeSizeStr = capturedStrings.get(3);
                protectionDomainId = lastProtectionDomain.peek();
                results.addVolume(protectionDomainId, id, name, poolName, ScaleIOUtils.convertToBytes(volumeSizeStr));
                break;
            case PROTECTION_DOMAIN_1_30:
                protectionDomainId = capturedStrings.get(0);
                protectionDomainName = capturedStrings.get(1);
                results.addProtectionDomain(protectionDomainId, protectionDomainName);
                lastProtectionDomain.push(protectionDomainId);
                break;
            case STORAGE_POOL:
                lastStoragePool.push(capturedStrings.get(0));
                break;
            case VOLUME_INFO_1_30:
                id = capturedStrings.get(0);
                name = capturedStrings.get(1);
                volumeSizeStr = capturedStrings.get(2);
                poolName = lastStoragePool.peek();
                protectionDomainId = lastProtectionDomain.peek();
                results.addVolume(protectionDomainId, id, name, poolName, ScaleIOUtils.convertToBytes(volumeSizeStr));
                break;
        }
    }

}
