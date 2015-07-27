/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

import com.iwave.ext.command.CommandException;

import java.util.List;

public class ScaleIOMapVolumeToSCSIInitiatorCommand extends AbstractScaleIOQueryCommand<ScaleIOMapVolumeToSCSIInitiatorResult> {

    private static final String MAPPED_SUCCESSFULLY = "MappedSuccessfully";
    // Successfully mapped volume with ID e9e51f8300000003 to SCSI Initiator with ID 19be524400000000 with LUN number 1
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Successfully mapped volume with ID \\w+ to SCSI Initiator with IQN .*", MAPPED_SUCCESSFULLY)
    };

    public ScaleIOMapVolumeToSCSIInitiatorCommand(ScaleIOCommandSemantics semantics, String volumeId, String iqn) {
        addArgument("--map_volume_to_scsi_initiator");
        addArgument(String.format("--volume_id %s", volumeId));
        addArgument(String.format("--iqn %s", iqn));
        if (semantics != ScaleIOCommandSemantics.SIO1_2X) {
            addArgument("--allow_multi_map");
        }
        results = new ScaleIOMapVolumeToSCSIInitiatorResult();
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
