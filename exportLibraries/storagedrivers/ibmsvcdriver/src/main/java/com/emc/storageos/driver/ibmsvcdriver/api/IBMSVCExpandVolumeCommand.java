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

public class IBMSVCExpandVolumeCommand extends AbstractIBMSVCQueryCommand<IBMSVCExpandVolumeResult> {

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
    };

    public IBMSVCExpandVolumeCommand(String volumeId, String newVolumeCapacity, boolean formatBeforeUse) {
        addArgument("svctask expandvdisksize");
        addArgument(String.format("-size %s -unit b", newVolumeCapacity));
        addArgument(String.format("%s", volumeId));
        if (formatBeforeUse) {
            addArgument("-fmtdisk");
        }
        results = new IBMSVCExpandVolumeResult();
        results.setId(volumeId);
        results.setRequestedNewSize(newVolumeCapacity);
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
