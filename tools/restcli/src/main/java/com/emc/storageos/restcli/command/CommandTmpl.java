/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli.command;

import com.emc.storageos.storagedriver.DriverTask;

public abstract class CommandTmpl {
    public abstract void usage();

    public void run(String[] args) {
        throw new UnsupportedOperationException();
    }

    protected void println(String s) {
        System.out.println(s);
    }

    protected void println() {
        System.out.println();
    }

    protected void showTaskInfo(DriverTask task) {
        println("Task Status:\n\t" + task.getStatus().toString());
        println("Task Message:\n\t" + task.getMessage());
    }
}
