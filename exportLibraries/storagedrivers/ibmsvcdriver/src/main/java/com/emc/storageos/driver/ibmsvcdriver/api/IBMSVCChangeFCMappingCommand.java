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

public class IBMSVCChangeFCMappingCommand extends AbstractIBMSVCQueryCommand<IBMSVCChangeFCMappingResult> {

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
    };

    public IBMSVCChangeFCMappingCommand(String fc_map_Id, String copyRate, String autoDelete, String consistencyGrpId, String cleanRate) {
        addArgument("svctask chfcmap");
        addArgument(String.format("-copyrate %s", copyRate));
        addArgument(String.format("-autodelete %s", autoDelete));

        if (consistencyGrpId != null && (!consistencyGrpId.equals(""))) {
            addArgument(String.format("-consistgrp %s", consistencyGrpId));
        }

        if (cleanRate != null && (!cleanRate.equals(""))) {
            addArgument(String.format("-cleanrate %s", cleanRate));
        }

        /**
         * Don't Change this order as FC Map Id should be the last argument
         * Attention: You must enter the fc_map_id | fc_map_name last on the command line.
         */
        addArgument(String.format("%s", fc_map_Id));

        results = new IBMSVCChangeFCMappingResult();
        results.setFcMappingId(fc_map_Id);
        results.setSuccess(true);
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
    }
}
