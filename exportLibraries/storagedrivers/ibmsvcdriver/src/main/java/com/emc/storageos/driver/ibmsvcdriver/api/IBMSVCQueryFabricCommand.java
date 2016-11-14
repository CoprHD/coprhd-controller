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

public class IBMSVCQueryFabricCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryFabricResult> {
    public static final String HOST_PARAMS_INFO = "FabricConnectivityInfo";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", HOST_PARAMS_INFO)
    };

    public IBMSVCQueryFabricCommand() {
        addArgument("svcinfo lsfabric -delim : -nohdr");
    }

    public IBMSVCQueryFabricCommand(String hostId) {

        addArgument("svcinfo lsfabric -delim : -nohdr");
        addArgument("-host " + hostId);
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
        results = new IBMSVCQueryFabricResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {

        switch (spec.getPropertyName()) {

            case HOST_PARAMS_INFO:

                String[] fabricData = capturedStrings.get(0).split(":");

                IBMSVCFabricConnectivity fabricConnectivity = new IBMSVCFabricConnectivity();

                fabricConnectivity.setRemoteWWPN(fabricData[0]);
                fabricConnectivity.setRemoteNportId(fabricData[1]);
                fabricConnectivity.setId(fabricData[2]);
                fabricConnectivity.setNodeName(fabricData[3]);
                fabricConnectivity.setLocalWWPN(fabricData[4]);
                fabricConnectivity.setLocalPort(fabricData[5]);
                fabricConnectivity.setLocalNportId(fabricData[6]);
                fabricConnectivity.setState(fabricData[7]);
                fabricConnectivity.setName(fabricData[8]);
                fabricConnectivity.setClusterName(fabricData[9]);
                fabricConnectivity.setType(fabricData[10]);

                results.addInitiator(fabricData[0]);
                results.addTarget(fabricData[4]);

                results.addFabricConnectivityList(fabricConnectivity);
                results.setSuccess(true);
                break;
        }
    }
}
