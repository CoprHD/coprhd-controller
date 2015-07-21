/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

import com.iwave.ext.command.CommandException;

import java.util.List;

public class ScaleIOMapVolumeToSDCCommand extends AbstractScaleIOQueryCommand<ScaleIOMapVolumeToSDCResult> {

    private static final String MAPPED_SUCCESSFULLY = "MappedSuccessfully";
    //     Successfully mapped volume with ID e9e5163500000000 to SDC with ID 5509377100000000.
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Successfully mapped volume with ID \\w+ to SDC with ID \\w+\\.", MAPPED_SUCCESSFULLY)
    };

    public ScaleIOMapVolumeToSDCCommand(ScaleIOCommandSemantics semantics, String volumeId, String sdcId) {
        addArgument("--map_volume_to_sdc");
        addArgument(String.format("--volume_id %s", volumeId));
        addArgument(String.format("--sdc_id %s", sdcId));
        if (semantics != ScaleIOCommandSemantics.SIO1_2X) {
            addArgument("--allow_multi_map");
        }
        results = new ScaleIOMapVolumeToSDCResult();
        results.setIsSuccess(false);
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

    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        if (spec.getPropertyName().equals(MAPPED_SUCCESSFULLY)) {
            results.setIsSuccess(true);
        }
    }
}
