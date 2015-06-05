/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.Collection;

import org.apache.commons.lang.text.StrBuilder;

import com.iwave.ext.command.CommandException;
import com.iwave.ext.linux.model.HBAInfo;

public class RescanHBAsCommand extends LinuxCommand {
    private static final String HOSTS = "hosts";
    private static final String SLEEP_TIME = "sleepTime";

    public RescanHBAsCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for host in ${hosts}; do ");
        sb.append("  echo 1 > /sys/class/fc_host/$host/issue_lip; ");
        sb.append("done; ");
        sb.append("sleep ${sleepTime}; ");
        sb.append("for host in ${hosts}; do ");
        sb.append("  echo \"- - -\" > /sys/class/scsi_host/$host/scan; ");
        sb.append("done; ");
        setCommand(sb.toString());
        // Default sleep time to 5s
        setSleepTime(5);
        setRunAsRoot(true);
    }

    public void setSleepTime(int sleepTime) {
        setVariableValue(SLEEP_TIME, String.valueOf(sleepTime));
    }

    public void setHostIds(Collection<Integer> hostIds) {
        StrBuilder sb = new StrBuilder();
        for (Integer hostId : hostIds) {
            sb.appendSeparator(' ');
            sb.append("host").append(hostId);
        }
        setVariableValue(HOSTS, sb.toString());
    }

    public void setHbas(Collection<HBAInfo> hbas) {
        StrBuilder sb = new StrBuilder();
        for (HBAInfo hba : hbas) {
            sb.appendSeparator(' ');
            sb.append("host").append(hba.getHostId());
        }
        setVariableValue(HOSTS, sb.toString());
    }

    @Override
    protected void validateCommandLine() throws CommandException {
        requireVariableValues(HOSTS, SLEEP_TIME);
    }
}
