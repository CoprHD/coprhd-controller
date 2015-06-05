/**
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

public class ScaleIOModifyVolumeCapacityCommand extends AbstractScaleIOQueryCommand<ScaleIOModifyVolumeCapacityResult> {

    private static final String MODIFY_CAPACITY_SUCCESS = "ModifyCapacitySuccess";

    //Rounding up volume size to 56 GB
//Successfully modified volume size to 56 GB
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Successfully modified volume size to (\\d+\\s+\\w+)", MODIFY_CAPACITY_SUCCESS),
    };

    public ScaleIOModifyVolumeCapacityCommand(String volumeId, String sizeGB) {
        addArgument("--modify_volume_capacity");
        addArgument(String.format("--volume_id %s", volumeId));
        addArgument(String.format("--size_gb %s", sizeGB));
        results = new ScaleIOModifyVolumeCapacityResult();
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setIsSuccess(false);
    }

    @Override
    void beforeProcessing() {
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            case MODIFY_CAPACITY_SUCCESS:
                String newCapacity = capturedStrings.get(0);
                results.setNewCapacity(ScaleIOUtils.convertToBytes(newCapacity));
                results.setSuccess(true);
                break;
        }
    }
}
