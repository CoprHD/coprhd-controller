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
package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.List;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;

public class IBMSVCQueryVolumeFCMappingCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryVolumeFCMappingResult> {

    public static final String FC_MAPPING_VOLUME_ID_INFO = "FCMappingData";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", FC_MAPPING_VOLUME_ID_INFO)
    };

    public IBMSVCQueryVolumeFCMappingCommand(String volumeId) {
        addArgument("svcinfo lsvdiskfcmappings -delim :");
        addArgument(String.format("%s", volumeId));
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
        results = new IBMSVCQueryVolumeFCMappingResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {

        switch (spec.getPropertyName()) {

            case FC_MAPPING_VOLUME_ID_INFO:

                String[] fcMappingData = capturedStrings.get(0).split(":");

                String fcMappingId = fcMappingData[0];
                String fcMappingName = fcMappingData[1];

                results.addFCMappingId(Integer.parseInt(fcMappingId));
                results.addFCMappingName(fcMappingName);
                results.setSuccess(true);
                break;
        }
    }
}
