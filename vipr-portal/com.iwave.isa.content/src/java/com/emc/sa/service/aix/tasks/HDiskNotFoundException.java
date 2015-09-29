package com.emc.sa.service.aix.tasks;

import com.iwave.ext.command.CommandException;

public class HDiskNotFoundException extends CommandException {

    private static final long serialVersionUID = 1L;

    public HDiskNotFoundException(String message) {
        super(message);
    }
}
