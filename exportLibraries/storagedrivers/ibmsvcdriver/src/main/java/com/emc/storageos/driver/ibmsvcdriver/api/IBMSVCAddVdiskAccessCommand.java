package com.emc.storageos.driver.ibmsvcdriver.api;/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;

import java.util.List;

public class IBMSVCAddVdiskAccessCommand extends AbstractIBMSVCQueryCommand<IBMSVCAddVdiskAccessResult>{

    public static final String ADD_VDISK_ACCESS_SUCCESS = "AddVdiskAccessSuccess";
    public static final String ADD_VDISK_ACCESS_FAILED = "AddVdiskAccessFailed";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
    };

    public IBMSVCAddVdiskAccessCommand(String ioGrp, String vdiskId) {
        addArgument("addvdiskaccess -iogrp " + ioGrp);
        addArgument(vdiskId);

        results = new IBMSVCAddVdiskAccessResult();
        results.setSuccess(true);
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setSuccess(false);
    }

    @Override
    void beforeProcessing() {

    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {

    }
}
