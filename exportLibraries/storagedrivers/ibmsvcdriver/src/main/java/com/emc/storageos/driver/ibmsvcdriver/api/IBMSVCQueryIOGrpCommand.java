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

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;

import java.util.List;

public class IBMSVCQueryIOGrpCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryIOGrpResult>{

    public static final String IOGRP_PARAMS_INFO = "IOGrpInfo";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", IOGRP_PARAMS_INFO)
    };

    public IBMSVCQueryIOGrpCommand() {
        addArgument("svcinfo lsiogrp -delim : -nohdr");
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
        results = new IBMSVCQueryIOGrpResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {

            case IOGRP_PARAMS_INFO:

                String[] iogrpData = capturedStrings.get(0).split(":");

                IBMSVCIOgrp iogrp = new IBMSVCIOgrp();

                iogrp.setIogrpId(iogrpData[0]);
                iogrp.setIogrpName(iogrpData[1]);
                iogrp.setNodeCount(Integer.parseInt(iogrpData[2]));
                iogrp.setVdiskCount(Integer.parseInt(iogrpData[3]));
                iogrp.setHostCount(Integer.parseInt(iogrpData[4]));

                results.addIogrpList(iogrp);
                results.setSuccess(true);
                break;
        }
    }
}
