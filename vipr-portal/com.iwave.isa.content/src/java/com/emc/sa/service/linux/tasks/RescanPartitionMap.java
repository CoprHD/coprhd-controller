/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.KpartxCommand;
import com.iwave.ext.linux.command.PartProbeCommand;
import com.iwave.utility.ssh.SSHTimeoutException;


public class RescanPartitionMap extends LinuxExecutionTask<Void> {

    private String device;
    
    public RescanPartitionMap(String device) {
        this.device = device;
    }
    
    @Override
    public void execute() throws Exception {
        executeCommand(new KpartxCommand(device));
        
        try {
            executeCommand(new PartProbeCommand(device), LONG_TIMEOUT);
        }
        catch (SSHTimeoutException timeout) {
            logWarn("rescan.partition.map.timeout", this.getTargetCLI().getHost());
        }

        setDetail(String.format("kpartx %s; partprobe %s;", device, device));
    }

}
