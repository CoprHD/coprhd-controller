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

public class IBMSVCQueryHostIOGrpCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryHostIOGrpResult> {

    public static final String HOST_IOGRP_PARAMS_INFO = "HostIOGrpInfo";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", HOST_IOGRP_PARAMS_INFO)
    };

    public IBMSVCQueryHostIOGrpCommand(String hostId) {
        addArgument("svcinfo lshostiogrp -delim : -nohdr " + hostId);
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    void beforeProcessing() {
        results = new IBMSVCQueryHostIOGrpResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {

            case HOST_IOGRP_PARAMS_INFO:

                String[] iogrpData = capturedStrings.get(0).split(":");

                IBMSVCIOgrp iogrp = new IBMSVCIOgrp();

                iogrp.setIogrpId(iogrpData[0]);
                iogrp.setIogrpName(iogrpData[1]);

                results.addHostIOGrpList(iogrp);
                results.setSuccess(true);
                break;
        }
    }
}
