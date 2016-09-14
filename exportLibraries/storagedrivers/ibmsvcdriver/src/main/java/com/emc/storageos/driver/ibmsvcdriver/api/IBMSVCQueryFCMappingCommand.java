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
import com.google.common.base.Joiner;

public class IBMSVCQueryFCMappingCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryFCMappingResult> {

    public static final String FC_MAPPING_ID = "FCMappingId";
    public static final String FC_SOURCE_VOL_ID = "SourceVolId";
    public static final String FC_SOURCE_VOL_NAME = "SourceVolName";
    public static final String FC_TARGET_VOL_ID = "TargetVolId";
    public static final String FC_TARGET_VOL_NAME = "TargetVolName";
    public static final String FC_MAPPING_STATUS = "FCMapStatus";
    public static final String FC_COPY_RATE = "CopyRate";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("id:(.*)", FC_MAPPING_ID),
            new ParsePattern("source_vdisk_id:(.*)", FC_SOURCE_VOL_ID),
            new ParsePattern("source_vdisk_name:(.*)", FC_SOURCE_VOL_NAME),
            new ParsePattern("target_vdisk_id:(.*)", FC_TARGET_VOL_ID),
            new ParsePattern("target_vdisk_name:(.*)", FC_TARGET_VOL_NAME),
            new ParsePattern("status:(.*)", FC_MAPPING_STATUS),
            new ParsePattern("copy_rate:(.*)", FC_COPY_RATE),
    };

    public IBMSVCQueryFCMappingCommand(String fc_map_Id, boolean isFilter, String srcVolName, String tgtVolName) {
        addArgument("svcinfo lsfcmap -delim :");
        if (!isFilter) {
            addArgument(String.format("%s", fc_map_Id));
        } else {
            addArgument(String.format("-filtervalue \"source_vdisk_name=%s\"", srcVolName));
            addArgument(String.format("\"target_vdisk_name=%s\"", tgtVolName));
        }
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
        results = new IBMSVCQueryFCMappingResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        results.setProperty(spec.getPropertyName(), Joiner.on(',').join(capturedStrings));
        results.setSuccess(true);
    }
}
