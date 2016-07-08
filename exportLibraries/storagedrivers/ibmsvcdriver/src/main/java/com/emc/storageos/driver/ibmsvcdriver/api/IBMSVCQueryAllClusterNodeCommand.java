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
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCClusterNode;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;

public class IBMSVCQueryAllClusterNodeCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryAllClusterNodeResult> {

    public static final String NODE_INFO = "NodeInfo";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("(.*)", NODE_INFO)
    };

    public IBMSVCQueryAllClusterNodeCommand() {
        addArgument("svcinfo lsnode -delim : -nohdr");
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
        results = new IBMSVCQueryAllClusterNodeResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            case NODE_INFO:
                String[] nodeData = capturedStrings.get(0).split(":");
                IBMSVCClusterNode clusterNode = new IBMSVCClusterNode();
                clusterNode.setNodeId(nodeData[0]);
                clusterNode.setNodeName(nodeData[1]);
                clusterNode.setNodeWWNN(nodeData[3]);
                results.addClusterNode(clusterNode);
                break;
        }
        results.setSuccess(true);
    }
}
