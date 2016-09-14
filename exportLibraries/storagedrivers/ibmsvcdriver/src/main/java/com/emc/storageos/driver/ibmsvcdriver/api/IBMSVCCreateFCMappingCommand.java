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

public class IBMSVCCreateFCMappingCommand extends AbstractIBMSVCQueryCommand<IBMSVCCreateFCMappingResult> {

    public static final String CREATE_FCMAPPING_SUCCESS = "CreateFCMappingSuccess";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("FlashCopy Mapping, id \\[(\\d+)\\], successfully created", CREATE_FCMAPPING_SUCCESS)
    };

    public IBMSVCCreateFCMappingCommand(String srcVolumeName, String tgtVolumeName, String consistGrpName, boolean fullyCopy) {
        addArgument("svctask mkfcmap");
        addArgument(String.format("-source %s", srcVolumeName));
        addArgument(String.format("-target %s", tgtVolumeName));

        // Setting the autodelete attribute. If this attribute is set to on, the mapping is automatically
        // deleted when the mapping reaches the idle_or_copied state and the progress is 100%
        //addArgument("-autodelete");

        if (fullyCopy) {
            //If the copy rate is greater than zero, the unchanged data is copied to the target volume
            // Creating Volume Clone
            addArgument("-copyrate 50");
        } else {
            //If the copy rate is zero, only the data that changes on the source is copied to the target
            // Creating Volume Snapshot
            addArgument("-copyrate 0");
        }
        if (consistGrpName != null) {
            addArgument(String.format("-consistgrp %s", consistGrpName));
        }
        results = new IBMSVCCreateFCMappingResult();
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
        if (spec.getPropertyName().equals(CREATE_FCMAPPING_SUCCESS)) {
            results.setId(capturedStrings.get(0));
            results.setSuccess(true);
        }
    }
}
