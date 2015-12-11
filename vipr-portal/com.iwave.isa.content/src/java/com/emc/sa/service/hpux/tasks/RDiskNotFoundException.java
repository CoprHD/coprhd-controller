/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.iwave.ext.command.CommandException;

public class RDiskNotFoundException extends CommandException {

    private static final long serialVersionUID = 1L;

    public RDiskNotFoundException(String message) {
        super(message);
    }
}
