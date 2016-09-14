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

public class IBMSVCDetachCloneMirrorCommand extends AbstractIBMSVCQueryCommand<IBMSVCDetachCloneMirrorResult> {

    public static final String DETACH_CLONEMIRROR_SUCCESS = "DetachCloneMirrorSuccess";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("Virtual Disk, id \\[(\\d+)\\], successfully created", DETACH_CLONEMIRROR_SUCCESS)
    };

    public IBMSVCDetachCloneMirrorCommand(String volumeId, String cloneVolumeName) {
        addArgument("svctask splitvdiskcopy");
        addArgument(String.format("-copy 1"));

        if (cloneVolumeName != null && (!cloneVolumeName.equals(""))) {
            addArgument(String.format("-name %s", cloneVolumeName));
        }

        addArgument(String.format("%s", volumeId));

        results = new IBMSVCDetachCloneMirrorResult();
        results.setVolumeId(volumeId);

        if (cloneVolumeName != null && (!cloneVolumeName.equals(""))) {
            results.setCloneVolumeName(cloneVolumeName);
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
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        if (spec.getPropertyName().equals(DETACH_CLONEMIRROR_SUCCESS)) {
            results.setCloneVolumeId(capturedStrings.get(0));
            results.setSuccess(true);
        }
    }
}
