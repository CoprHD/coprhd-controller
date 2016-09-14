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

import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;

import java.util.List;

public class IBMSVCCreateHost extends AbstractIBMSVCQueryCommand<IBMSVCCreateHostResult> {

    public static final String CREATE_HOST_SUCCESS = "CreateHostSuccess";
    public static final String CREATE_HOST_FAILED = "CreateHostFailed";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Host, id \\[(\\d+)\\], successfully created", CREATE_HOST_SUCCESS),
    };

    public IBMSVCCreateHost(String hostName, String hbawwpns, String ioGrp) {

        addArgument("svctask mkhost");
        addArgument("-name " + hostName);
        addArgument("-hbawwpn " + hbawwpns);
        addArgument("-iogrp " + ioGrp);
        //Do not validate WWNs
        addArgument("-force");

        results = new IBMSVCCreateHostResult();
        results.setName(hostName);
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
        if (spec.getPropertyName().equals(CREATE_HOST_SUCCESS)) {
            results.setId(capturedStrings.get(0));
            results.setSuccess(true);
        }
    }
}
