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

public class IBMSVCQueryAllHostCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryAllHostResult> {

    public static final String HOST_PARAMS_INFO = "HostsInfo";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", HOST_PARAMS_INFO)
    };

    public IBMSVCQueryAllHostCommand() {
        addArgument("svcinfo lshost -delim : -nohdr");
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
        results = new IBMSVCQueryAllHostResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {

        switch (spec.getPropertyName()) {

            case HOST_PARAMS_INFO:

                String[] hostData = capturedStrings.get(0).split(":");

                IBMSVCHost host = new IBMSVCHost();
                host.setHostId(hostData[0]);
                host.setHostName(hostData[1]);
                host.setPortCount(hostData[2]);
                host.setHostStatus(hostData[4]);
                results.addHostList(host);
                results.setSuccess(true);
                break;
        }
    }
}
