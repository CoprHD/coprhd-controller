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

public class IBMSVCQueryFCConsistGrpCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryFCConsistGrpResult> {

    public static final String FC_CONSISTGRP_ID = "FCConsistGrpId";
    public static final String FC_CONSISTGRP_NAME = "FCConsistGrpName";
    public static final String FC_CONSISTGRP_STATUS = "FCConsistGrpStatus";
    public static final String FC_MAPPING_ID = "FCMappingId";
    public static final String FC_MAPPING_NAME = "FCMappingName";

    private String fcMappingId;
    private String fcMappingName;

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("id:(.*)", FC_CONSISTGRP_ID),
            new ParsePattern("name:(.*)", FC_CONSISTGRP_NAME),
            new ParsePattern("status:(.*)", FC_CONSISTGRP_STATUS),
            new ParsePattern("FC_mapping_id:(.*)", FC_MAPPING_ID),
            new ParsePattern("FC_mapping_name:(.*)", FC_MAPPING_NAME),
    };

    public IBMSVCQueryFCConsistGrpCommand(String fcConsistGrpId, String consistGrpName) {
        addArgument("svcinfo lsfcconsistgrp -delim :");
        addArgument(String.format("%s", fcConsistGrpId));
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
        results = new IBMSVCQueryFCConsistGrpResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            case FC_CONSISTGRP_ID:
                results.setConsistGrpId(capturedStrings.get(0));
                break;
            case FC_CONSISTGRP_NAME:
                results.setConsistGrpName(capturedStrings.get(0));
                break;
            case FC_CONSISTGRP_STATUS:
                results.setConsistGrpStatus(capturedStrings.get(0));
                break;
            case FC_MAPPING_ID:
                fcMappingId = capturedStrings.get(0);
                break;
            case FC_MAPPING_NAME:
                fcMappingName = capturedStrings.get(0);
                results.addFcMappingData(fcMappingId, fcMappingName);
                break;
        }
        results.setSuccess(true);
    }
}
