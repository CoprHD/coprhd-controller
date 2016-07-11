/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

public class GetMachineIdCommand extends CatCommand {

    public GetMachineIdCommand() {
        super("/etc/machine-id");
    }

    @Override
    public void parseOutput() {
        super.parseOutput();
        results = results.trim();
    }

}
