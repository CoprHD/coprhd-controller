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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.ibmsvcdriver.command.Command;

public class IBMSVCCLICommand extends Command {

    // The logger.
    private static final Logger _log = LoggerFactory.getLogger(IBMSVCCLICommand.class);
    
    public static final String IBMSVC_CLI = "";

    private String customCLI;

    IBMSVCCLICommand() {
        setCommand(IBMSVC_CLI);
    }

    public void useCustomInvocationIfSet(String customInvocation) {
        if (customInvocation != null) {
            customCLI = customInvocation;
            _log.info("Using the custom command execution.", customCLI);
            setCommand(customCLI);
        }
    }

}
