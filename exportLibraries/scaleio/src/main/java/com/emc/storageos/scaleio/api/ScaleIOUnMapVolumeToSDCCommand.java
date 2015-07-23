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

public class ScaleIOUnMapVolumeToSDCCommand extends AbstractScaleIOQueryCommand<ScaleIOUnMapVolumeToSDCResult> {

    private static final String UNMAPPED_SUCCESSFULLY = "UnmappedSuccessfully";
    // Successfully un-mapped volume with ID e9e5163500000000 from SDC with ID 5509377100000000.
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Successfully un-mapped volume with ID \\w+ from SDC with ID \\w+\\.", UNMAPPED_SUCCESSFULLY)
    };

    public ScaleIOUnMapVolumeToSDCCommand(ScaleIOCommandSemantics commandSemantics, String volumeId, String sdcId) {
        addArgument("--unmap_volume_from_sdc");
        addArgument(String.format("--volume_id %s", volumeId));
        addArgument(String.format("--sdc_id %s", sdcId));
        if (commandSemantics != ScaleIOCommandSemantics.SIO1_2X) {
            // SIO 1.30+ prompts the user for certain operations; this argument gets past the prompt
            addArgument("--i_am_sure");
            // SIO 1.30+ allows you to ignore the iSCSI mappings. If we did not, the unmap would
            // fail against regular SDCs
            addArgument("--ignore_scsi_initiators");
        }
        results = new ScaleIOUnMapVolumeToSDCResult();
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
