/*
 * Copyright (c) 2016 EMC Corporation
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

public class IBMSVCCreateVolumeCommand extends AbstractIBMSVCQueryCommand<IBMSVCCreateVolumeResult> {

    public static final String CREATE_VOLUME_SUCCESS = "CreateVolumeSuccess";
    public static final String CREATE_VOLUME_FAILED = "CreateVolumeFailed";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Virtual Disk, id \\[(\\d+)\\], successfully created", CREATE_VOLUME_SUCCESS),
    };

    public IBMSVCCreateVolumeCommand(String volumeName, String volumeSize, String storagePoolName,
                                     boolean formatBeforeUse, boolean createMirrorCopy, boolean thinlyProvisioned) {
        addArgument("svctask mkvdisk -cache readwrite -vtype striped");
        addArgument("-iogrp io_grp0");

        if (volumeName != null && (!volumeName.equals(""))) {
            addArgument(String.format("-name %s", volumeName));
        }

        addArgument(String.format("-size %s", volumeSize));
        addArgument("-unit b");

        if (formatBeforeUse) {
            addArgument("-fmtdisk");
        }

        if (createMirrorCopy) {
            addArgument("-createsync");
            addArgument("-syncrate 50");
            addArgument(String.format("-copies 2"));
            addArgument(String.format("-mdiskgrp %s", storagePoolName + ":" + storagePoolName));
        } else {
            addArgument(String.format("-copies 1"));
            addArgument(String.format("-mdiskgrp %s", storagePoolName));
        }

        if (thinlyProvisioned) {
            addArgument("-autoexpand");
            addArgument("-grainsize 256");
            addArgument("-rsize 2%");
            addArgument("-warning 80%");
        }
        results = new IBMSVCCreateVolumeResult();
        results.setProvisionedCapacity(volumeSize);
        results.setAllocatedCapacity(volumeSize);
        results.setName(volumeName);
        results.setStoragePoolName(storagePoolName);
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
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        if (spec.getPropertyName().equals(CREATE_VOLUME_SUCCESS)) {
            results.setId(capturedStrings.get(0));
            results.setSuccess(true);
        }
    }
}
