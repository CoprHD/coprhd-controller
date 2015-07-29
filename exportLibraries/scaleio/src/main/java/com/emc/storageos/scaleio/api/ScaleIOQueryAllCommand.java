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

import com.google.common.base.Joiner;

import java.util.List;
import java.util.Stack;

public class ScaleIOQueryAllCommand extends AbstractScaleIOQueryCommand<ScaleIOQueryAllResult> {

    public static final String SCALEIO_VERSION = "Version";
    public static final String SCALEIO_CUSTOMER_ID = "CustomerID";
    public static final String SCALEIO_INSTALLATION_ID = "InstallationID";
    public static final String SCALEIO_TOTAL_CAPACITY = "TotalCapacity";
    public static final String SCALEIO_FREE_CAPACITY = "FreeCapacity";
    public static final String SCALEIO_IN_USE_CAPACITY = "InUseCapacity";
    public static final String SCALEIO_PROTECTED_CAPACITY = "ProtectedCapacity";
    public static final String SCALEIO_SNAPSHOT_CAPACITY = "SnapshotCapacity";

    public static final String PROTECTION_DOMAIN = "ProtectionDomain";
    public static final String STORAGE_POOL = "StoragePool";
    public static final String POOL_AVAILABLE_CAPACITY = "AvailableCapacity";
    public static final String POOL_ALLOCATED_VOLUME_COUNT = "AllocatedVolumeCount";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("ScaleIO ECS Version:\\s+[a-zA-Z](.*?)", SCALEIO_VERSION),
            new ParsePattern("\\s+Product:\\s+EMC ScaleIO Version:\\s+[a-zA-Z](.*?)", SCALEIO_VERSION),
            new ParsePattern("Customer\\s+ID:\\s+(\\d+)", SCALEIO_CUSTOMER_ID),
            new ParsePattern("\\s+ID:\\s+(\\w+)", SCALEIO_CUSTOMER_ID),
            new ParsePattern("\\s*Installation ID:\\s+(\\w+)", SCALEIO_INSTALLATION_ID),
            new ParsePattern(ScaleIOContants.REGEX_CAPACITY + " total capacity", SCALEIO_TOTAL_CAPACITY),
            new ParsePattern(ScaleIOContants.REGEX_CAPACITY + " unused capacity", SCALEIO_FREE_CAPACITY),
            new ParsePattern(ScaleIOContants.REGEX_BYTES_CAPACITY + "snapshots capacity", SCALEIO_SNAPSHOT_CAPACITY),
            new ParsePattern(ScaleIOContants.REGEX_BYTES_CAPACITY + "in-use capacity", SCALEIO_IN_USE_CAPACITY),
            new ParsePattern(ScaleIOContants.REGEX_BYTES_CAPACITY + "protected capacity", SCALEIO_PROTECTED_CAPACITY),
            new ParsePattern("Protection [d|D]omain\\s+(.*?)\\s+(?:\\(Id: \\w+\\)\\s+)?has.*?volumes and" + ScaleIOContants.REGEX_CAPACITY +
                    " available for volume allocation", PROTECTION_DOMAIN),
            new ParsePattern("Protection [d|D]omain\\s+(.*?)\\s+(?:\\(Id: \\w+\\)\\s+)?has.*?volumes and"
                    + ScaleIOContants.REGEX_BYTES_CAPACITY +
                    "available for volume allocation", PROTECTION_DOMAIN),
            new ParsePattern("Storage [p|P]ool\\s+(.*?)\\s+(?:\\(Id: \\w+\\)\\s+)?has (\\d+) volumes and" + ScaleIOContants.REGEX_CAPACITY +
                    " available for volume allocation.*", STORAGE_POOL),
            new ParsePattern("Storage [p|P]ool\\s+(.*?)\\s+(?:\\(Id: \\w+\\)\\s+)?has (\\d+) volumes and"
                    + ScaleIOContants.REGEX_BYTES_CAPACITY +
                    "available for volume allocation.*", STORAGE_POOL),
    };

    Stack<String> lastProtectionDomain;

    public ScaleIOQueryAllCommand(ScaleIOCommandSemantics semantics) {
        addArgument("--query_all");
        if (semantics == ScaleIOCommandSemantics.SIO1_2X) {
            // This parameter is only applicable to SIO 1.2X systems
            addArgument("--show_all 1");
        }
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); // No need to check not null condition here
    }

    @Override
    void beforeProcessing() {
        results = new ScaleIOQueryAllResult();
        lastProtectionDomain = new Stack<String>();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        String protectionDomainName;
        switch (spec.getPropertyName()) {
            case PROTECTION_DOMAIN:
                protectionDomainName = capturedStrings.get(0);
                lastProtectionDomain.push(protectionDomainName);
                results.addProtectionDomain(protectionDomainName);
                break;
            case STORAGE_POOL:
                protectionDomainName = lastProtectionDomain.peek();
                String storagePoolName = capturedStrings.get(0);
                String poolAllocatedVolCount = capturedStrings.get(1);
                String poolAvailableCapacity = capturedStrings.get(2);
                results.addProtectionDomainStoragePool(protectionDomainName, storagePoolName);
                results.addStoragePoolProperty(protectionDomainName, storagePoolName, POOL_AVAILABLE_CAPACITY,
                        ScaleIOUtils.convertToBytes(poolAvailableCapacity));
                results.addStoragePoolProperty(protectionDomainName, storagePoolName, POOL_ALLOCATED_VOLUME_COUNT,
                        poolAllocatedVolCount);
                break;
            case SCALEIO_TOTAL_CAPACITY:
                results.setProperty(spec.getPropertyName(), ScaleIOUtils.convertToBytes(capturedStrings.get(0)));
                break;
            default:
                results.setProperty(spec.getPropertyName(), Joiner.on(',').join(capturedStrings));
        }
    }

}
