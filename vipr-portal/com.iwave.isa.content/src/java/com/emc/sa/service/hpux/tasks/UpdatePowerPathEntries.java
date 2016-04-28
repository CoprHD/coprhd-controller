/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.iwave.ext.linux.command.powerpath.PowermtConfigCommand;
import com.iwave.ext.linux.command.powerpath.PowermtRestoreCommand;
import com.iwave.ext.linux.command.powerpath.PowermtSaveCommand;

public class UpdatePowerPathEntries extends HpuxExecutionTask<Void> {

    public UpdatePowerPathEntries() {
        setName("UpdatePowerPathEntries.name");
    }

    @Override
    public void execute() throws Exception {
        executeCommand(new PowermtConfigCommand(), MEDIUM_TIMEOUT);
        executeCommand(new PowermtRestoreCommand(), MEDIUM_TIMEOUT);
        executeCommand(new PowermtSaveCommand(), MEDIUM_TIMEOUT);
        setDetail("powermt config; powermt restore; powermt save;");
    }
}
