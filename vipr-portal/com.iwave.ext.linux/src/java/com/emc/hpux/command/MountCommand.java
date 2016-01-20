/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.command.CommandException;

public class MountCommand extends HpuxCommand {

    public MountCommand(String source, String mountPoint) {
        setCommand(String.format("mount %s %s", source, mountPoint));
        setRunAsRoot(true);
    }

    @Override
    protected void processError() throws CommandException {
        String stdout = StringUtils.trimToEmpty(getOutput().getStdout());
        String stderr = StringUtils.trimToEmpty(getOutput().getStderr());
        if (stdout.isEmpty() || !stderr.isEmpty()) {
            super.processError();
        }
    }

}
