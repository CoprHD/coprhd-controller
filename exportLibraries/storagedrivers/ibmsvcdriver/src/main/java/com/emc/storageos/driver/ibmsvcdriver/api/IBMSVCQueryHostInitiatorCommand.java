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
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;
import com.emc.storageos.storagedriver.model.Initiator;

public class IBMSVCQueryHostInitiatorCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryHostInitiatorResult> {

    public static final String HOST_ID = "HostID";
    public static final String HOST_NAME = "HostName";
    public static final String PORT_WWPN = "PortWWPN";

    private String hostId;
    private String hostName;

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("id:(.*)", HOST_ID),
            new ParsePattern("name:(.*)", HOST_NAME),
            new ParsePattern("WWPN:(.*)", PORT_WWPN)
    };

    public IBMSVCQueryHostInitiatorCommand(String hostId) {
        addArgument("svcinfo lshost -delim : " + hostId);
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
        results = new IBMSVCQueryHostInitiatorResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {

        switch (spec.getPropertyName()) {
            case HOST_ID:
                hostId = capturedStrings.get(0);
                break;
            case HOST_NAME:
                hostName = capturedStrings.get(0);
                break;
            case PORT_WWPN:
                String portWWPN = IBMSVCDriverUtils.formatWWNString(capturedStrings.get(0));
                Initiator initiator = new Initiator();
                initiator.setHostName(hostName);
                initiator.setPort(portWWPN);
                initiator.setDeviceLabel(portWWPN);
                initiator.setProtocol(Initiator.Protocol.FC);
                results.setHostId(hostId);
                results.addHostInitiatorList(initiator);
                break;
        }
        results.setSuccess(true);
    }
}
