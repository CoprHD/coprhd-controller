/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.MkfsCommand;

public class FormatVolume extends LinuxExecutionTask<Void> {

    public static final String EXT3 = "ext3";
    public static final String EXT4 = "ext4";

    private String device;
    private String fsType;
    private String blockSize;
    private boolean journaling;

    public FormatVolume(String device, String fsType, String blockSize, boolean journaling) {
        this.device = device;
        this.fsType = fsType;
        this.blockSize = blockSize;
        this.journaling = journaling;
    }

    @Override
    public void execute() throws Exception {
        MkfsCommand command = new MkfsCommand(device, fsType, blockSize, journaling);
        executeCommand(command, LONG_TIMEOUT);
    }

}
