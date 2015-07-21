/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

import java.util.List;

public class ScaleIOQueryStoragePoolCommand extends AbstractScaleIOQueryCommand<ScaleIOQueryStoragePoolResult> {

    public static final String STORAGE_POOL_TOTAL_CAPACITY = "StoragePoolTotalCapacity";
    public static final String STORAGE_POOL = "StoragePoolInfo";
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern(ScaleIOContants.REGEX_CAPACITY + " total capacity", STORAGE_POOL_TOTAL_CAPACITY),
            new ParsePattern("Storage pool\\s+(.*?) has (\\d+) volumes and" + ScaleIOContants.REGEX_CAPACITY +
                    " available for volume allocation.*", STORAGE_POOL),
            new ParsePattern("Storage pool\\s+(.*?) has (\\d+) volumes and" + ScaleIOContants.REGEX_BYTES_CAPACITY +
                    "available for volume allocation.*", STORAGE_POOL),
             new ParsePattern("Storage Pool\\s+(.*?)\\s+\\(Id:\\s+\\w+\\)\\s+has (\\d+) volumes and" + ScaleIOContants.REGEX_CAPACITY +
                    " available for volume allocation.*", STORAGE_POOL),
            new ParsePattern("Storage Pool\\s+(.*?)\\s+\\(Id:\\s+\\w+\\)\\s+has (\\d+) volumes and" + ScaleIOContants.REGEX_BYTES_CAPACITY +
                    "available for volume allocation.*", STORAGE_POOL)
    };

    public ScaleIOQueryStoragePoolCommand(String protectionDomainName, String storagePoolName) {
        addArgument("--query_storage_pool");
        addArgument(String.format("--protection_domain_name %s", protectionDomainName));
        addArgument(String.format("--storage_pool_name %s", storagePoolName));
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); //No need to check not null condition here
    }

    @Override
    void beforeProcessing() {
        results = new ScaleIOQueryStoragePoolResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            case STORAGE_POOL:
                String name = capturedStrings.get(0);
                String volumeCount = capturedStrings.get(1);
                String availableCapacity = capturedStrings.get(2);
                results.setName(name);
                results.setAvailableCapacity(ScaleIOUtils.convertToBytes(availableCapacity));
                results.setVolumeCount(volumeCount);
                break;
            case STORAGE_POOL_TOTAL_CAPACITY:
                String totalCapacity = capturedStrings.get(0);
                results.setTotalCapacity(ScaleIOUtils.convertToBytes(totalCapacity));
                break;
        }
    }
}
