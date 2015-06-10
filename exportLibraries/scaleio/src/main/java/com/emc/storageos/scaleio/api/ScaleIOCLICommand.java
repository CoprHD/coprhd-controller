/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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

package com.emc.storageos.scaleio.api;

import com.iwave.ext.command.Command;

/**
 * Basic ScaleIO command is to invoke the /opt/scaleio/ecs/mdm/bin/cli
 * that is on the primary MDM host.
 *
 * Classes that derive from command are expected to add arguments for
 * the specific ScaleIO command. For example if there was ScaleIO
 * command called --foo that was called list ./cli --foo, then the
 * derived implementation would have to call addArgument("--foo") before
 * the command should be executed.
 *
 */
public class ScaleIOCLICommand extends Command {

    public static final String SCALEIO_CLI = "scli";

    private String customCLI;

    public enum ScaleIOCommandSemantics {
        SIO1_2X, SIO1_30
    }

    ScaleIOCLICommand() {
        setCommand(SCALEIO_CLI);
    }

    public void useCustomInvocationIfSet(String customInvocation) {
        if (customInvocation != null) {
            customCLI = customInvocation;
            setCommand(customInvocation);
        }
    }

    /**
     * Need to do something special for SIO 1.30. There is an MDM username and password
     * required for running commands. This needs to be specified, so we will do it
     * at the ScaleIOCLICommand level.
     *
     * @param mdmUsername [in] - MDM username
     * @param mdmPassword [in] - MDM password
     */
    public void use130Semantics(String mdmUsername, String mdmPassword) {
        String cli = (customCLI != null) ? customCLI : SCALEIO_CLI;
        String loginCmd = String.format("%s --login --username \"%s\" --password \"%s\"", cli, mdmUsername, mdmPassword);
        String command = String.format("%s && %s", loginCmd, cli);
        // We are basically going to construct the command like this:
        // scli --login --username <username> --password <password> && scli ...
        setCommand(command);
    }
}
