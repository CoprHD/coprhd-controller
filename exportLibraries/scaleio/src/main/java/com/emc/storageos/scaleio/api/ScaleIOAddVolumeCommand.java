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

public class ScaleIOAddVolumeCommand extends AbstractScaleIOQueryCommand<ScaleIOAddVolumeResult> {
    public static final String ADDED_VOLUME_SUCCESS = "AddedVolumeSuccess";
    public static final String ADDED_VOLUME_FAILED = "AddedVolumeFailed";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("Successfully created volume of size (\\d+) GB. Object ID (\\w+)", ADDED_VOLUME_SUCCESS),
    };

    public ScaleIOAddVolumeCommand(String protectionDomainName, String storagePoolName, String volumeName, String volumeSize) {
        results = new ScaleIOAddVolumeResult();
        results.setRequestedSize(volumeSize);
        results.setName(volumeName);
        results.setProtectionDomainName(protectionDomainName);
        results.setStoragePoolName(storagePoolName);
        addArgument("--add_volume");
        addArgument(String.format("--protection_domain_name %s", protectionDomainName));
        addArgument(String.format("--storage_pool_name %s", storagePoolName));
        addArgument(String.format("--volume_name %s", volumeName));
        addArgument(String.format("--size_gb %s", volumeSize));
    }

    public ScaleIOAddVolumeCommand(ScaleIOCommandSemantics semantics, String protectionDomainName, String storagePoolName,
            String volumeName, String volumeSize, boolean thinProvisioned) {
        results = new ScaleIOAddVolumeResult();
        results.setRequestedSize(volumeSize);
        results.setName(volumeName);
        results.setProtectionDomainName(protectionDomainName);
        results.setStoragePoolName(storagePoolName);
        addArgument("--add_volume");
        addArgument(String.format("--protection_domain_name %s", protectionDomainName));
        addArgument(String.format("--storage_pool_name %s", storagePoolName));
        addArgument(String.format("--volume_name %s", volumeName));
        addArgument(String.format("--size_gb %s", volumeSize));
        if (semantics != ScaleIOCommandSemantics.SIO1_2X && thinProvisioned) {
            addArgument("--thin_provisioned");
            results.setIsThinlyProvisioned(true);
        }
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setIsSuccess(false);
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); // No need to check not null condition here.
    }

    @Override
    void beforeProcessing() {
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        if (spec.getPropertyName().equals(ADDED_VOLUME_SUCCESS)) {
            results.setActualSize(capturedStrings.get(0));
            results.setId(capturedStrings.get(1));
            results.setIsSuccess(true);
        }
    }
}
