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
import com.google.common.base.Joiner;

public class IBMSVCQueryStorageSystemCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryStorageSystemResult> {
   
    public static final String IBMSVC_SERIALNUMBER = "SerialNumber";
    public static final String IBMSVC_FWVERSION = "FirmwareVersion";
    public static final String IBMSVC_IPADDRESS = "IpAddress";
    public static final String IBMSVC_MODEL = "Model";
    public static final String IBMSVC_TOTAL_CAPACITY = "TotalCapacity";
    public static final String IBMSVC_FREE_CAPACITY = "FreeCapacity";
    public static final String IBMSVC_IN_USE_CAPACITY = "InUseCapacity";
    public static final String IBMSVC_VERSION = "Version";
    public static final String IBMSVC_MAXVERSION = "MajorVersion";
    public static final String IBMSVC_MINVERSION = "MinorVersion";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("id:(.*)", IBMSVC_SERIALNUMBER),
            new ParsePattern("console_IP:(.*):(.*)", IBMSVC_IPADDRESS),
            new ParsePattern("total_mdisk_capacity:(.*)", IBMSVC_TOTAL_CAPACITY),
            new ParsePattern("total_free_space:(.*)", IBMSVC_FREE_CAPACITY),
            new ParsePattern("total_used_capacity:(.*)", IBMSVC_IN_USE_CAPACITY),
            new ParsePattern("product_name:(.*)", IBMSVC_MODEL),
            new ParsePattern("code_level:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+).*", IBMSVC_VERSION),
    };

    public IBMSVCQueryStorageSystemCommand() {
        addArgument("svcinfo lssystem -delim :");
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
        results = new IBMSVCQueryStorageSystemResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            case IBMSVC_IPADDRESS:
                String ipAddress = capturedStrings.get(0);
                results.setProperty(IBMSVC_IPADDRESS, ipAddress);
                break;
            case IBMSVC_VERSION:
                String firmwareVersion = capturedStrings.get(0) + "." + capturedStrings.get(1)
                        + "." + capturedStrings.get(2) + "." + capturedStrings.get(3);
                results.setProperty(IBMSVC_FWVERSION, firmwareVersion);
                results.setProperty(IBMSVC_MAXVERSION, capturedStrings.get(0));
                results.setProperty(IBMSVC_MINVERSION, capturedStrings.get(1));
            default:
                results.setProperty(spec.getPropertyName(), Joiner.on(',').join(capturedStrings));
                break;
        }
        results.setSuccess(true);
    }
}
