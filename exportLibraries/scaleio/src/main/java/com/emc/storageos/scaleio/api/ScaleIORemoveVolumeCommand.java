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

public class ScaleIORemoveVolumeCommand extends AbstractScaleIOQueryCommand<ScaleIORemoveVolumeResult> {
    public static final String REMOVED_VOLUME_SUCCESS = "RemovedVolumeSuccess";
    public static final String REMOVED_VOLUME_FAILED = "RemovedVolumeFailed";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Successfully removed volume ID (\\w+)", REMOVED_VOLUME_SUCCESS)
    };

    public ScaleIORemoveVolumeCommand(ScaleIOCommandSemantics commandSemantics, String volumeId) {
        addArgument("--remove_volume");
        addArgument(String.format("--volume_id %s", volumeId));
        if (commandSemantics != ScaleIOCommandSemantics.SIO1_2X) {
            // SIO 1.30+ prompts the user for certain operations; this argument gets past the prompt
            addArgument("--i_am_sure");
        }
        results = new ScaleIORemoveVolumeResult();
        results.setIsSuccess(true);
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setIsSuccess(false);
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
    }
}
