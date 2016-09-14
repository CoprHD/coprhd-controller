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

public class IBMSVCCreateMirrorVolumeCommand extends AbstractIBMSVCQueryCommand<IBMSVCCreateMirrorVolumeResult> {

    public static final String CREATE_MIRROR_VOLUME_SUCCESS = "CreateMirrorVolumeSuccess";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("Vdisk \\[(\\d+)\\] copy \\[1\\], successfully created", CREATE_MIRROR_VOLUME_SUCCESS)
    };

    public IBMSVCCreateMirrorVolumeCommand(String srcVolumeId, String srcPoolId) {
        addArgument("svctask addvdiskcopy");
        addArgument(String.format("-mdiskgrp %s", srcPoolId));
        addArgument(String.format("%s", srcVolumeId));

        results = new IBMSVCCreateMirrorVolumeResult();
        results.setSrcVolumeId(srcVolumeId);
        results.setSrcPoolId(srcPoolId);
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
        if (spec.getPropertyName().equals(CREATE_MIRROR_VOLUME_SUCCESS)) {
            results.setSrcVolumeId(capturedStrings.get(0));
            results.setSuccess(true);
        }
    }
}
