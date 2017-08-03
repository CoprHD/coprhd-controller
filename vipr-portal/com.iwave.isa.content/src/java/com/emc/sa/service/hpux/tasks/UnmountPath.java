/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.emc.sa.engine.ExecutionUtils;
import com.iwave.ext.linux.command.UnmountCommand;

public class UnmountPath extends HpuxExecutionTask<Void> {

    private String path;

    public UnmountPath(String path) {
        this.path = path;
    }

    @Override
    public void execute() throws Exception {
        try {
            UnmountCommand command = new UnmountCommand();
            command.setPath(path);
            executeCommand(command, SHORT_TIMEOUT);
        } catch (Exception ex) {
            ExecutionUtils.fail("failTask.UnmountPath", new Object[] { path }, path, ex.getMessage(), path);
        }
    }
}