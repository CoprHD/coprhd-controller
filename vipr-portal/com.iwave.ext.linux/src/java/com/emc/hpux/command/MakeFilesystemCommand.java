/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.command.CommandException;

public class MakeFilesystemCommand extends HpuxCommand {

    public MakeFilesystemCommand(String disk) {
        setCommand(String.format("mkfs %s", disk));
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
