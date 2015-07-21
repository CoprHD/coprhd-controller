/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

import com.iwave.ext.command.CommandException;

import java.util.List;

public class ScaleIOUnMapVolumeFromSCSIInitiatorCommand extends AbstractScaleIOQueryCommand<ScaleIOUnMapVolumeFromSCSIInitiatorResult> {

    private static final String UNMAPPED_SUCCESSFULLY = "UnmappedSuccessfully";
    // Successfully unmapped volume with ID e9e51f7c00000002 from SCSI Initiator with ID 19be524400000000
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Successfully unmapped volume with ID \\w+ from SCSI Initiator with IQN .*", UNMAPPED_SUCCESSFULLY)
    };

    public ScaleIOUnMapVolumeFromSCSIInitiatorCommand(ScaleIOCommandSemantics commandSemantics, String volumeId, String iqn) {
        addArgument("--unmap_volume_from_scsi_initiator");
        addArgument(String.format("--volume_id %s", volumeId));
        addArgument(String.format("--iqn %s", iqn));
        if (commandSemantics != ScaleIOCommandSemantics.SIO1_2X) {
            // SIO 1.30+ prompts the user for certain operations; this argument gets past the prompt
            addArgument("--i_am_sure");
        }
        results = new ScaleIOUnMapVolumeFromSCSIInitiatorResult();
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
        if (spec.getPropertyName().equals(UNMAPPED_SUCCESSFULLY)) {
            results.setIsSuccess(true);
        }
    }
}
