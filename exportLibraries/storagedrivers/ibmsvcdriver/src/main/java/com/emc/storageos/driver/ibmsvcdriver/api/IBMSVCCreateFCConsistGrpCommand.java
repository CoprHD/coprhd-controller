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

public class IBMSVCCreateFCConsistGrpCommand extends AbstractIBMSVCQueryCommand<IBMSVCCreateFCConsistGrpResult> {

    public static final String CREATE_FCCONSISTGRP_SUCCESS = "CreateFCConsistGrpSuccess";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("FlashCopy Consistency Group, id \\[(\\d+)\\], successfully created", CREATE_FCCONSISTGRP_SUCCESS)
    };

    public IBMSVCCreateFCConsistGrpCommand(String consistGrpName) {
        addArgument("svctask mkfcconsistgrp");
        addArgument(String.format("-name %s", consistGrpName));

        // Setting the autodelete attribute. If this attribute is set to on, the FC CG is automatically
        // deleted when the last mapping it contains is deleted
        //addArgument("-autodelete");

        results = new IBMSVCCreateFCConsistGrpResult();
        results.setConsistGrpName(consistGrpName);
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
        if (spec.getPropertyName().equals(CREATE_FCCONSISTGRP_SUCCESS)) {
            results.setConsistGrpId(capturedStrings.get(0));
            results.setSuccess(true);
        }
    }
}
