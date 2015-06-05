/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command.fc;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.command.LinuxResultsCommand;
import com.iwave.ext.linux.command.parser.FcTargetParser;
import com.iwave.ext.linux.model.FcTarget;

public class ListFcTargetsCommand extends LinuxResultsCommand<List<FcTarget>> {

    public ListFcTargetsCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for target in `ls /sys/class/fc_transport`; do ");
        sb.append("  echo \"target: $target\" ; ");
        sb.append("  echo -n \"node: \" ; cat /sys/class/fc_transport/$target/node_name; ");
        sb.append("  echo -n \"port: \" ; cat /sys/class/fc_transport/$target/port_name; ");
        sb.append("done; ");
        setCommand(sb.toString());
    }

    @Override
    public void parseOutput() {
        String stdout = getOutput().getStdout();
        if (StringUtils.isNotBlank(stdout)) {
            results = new FcTargetParser().parseFcTargets(stdout);
        }
        else {
            results = Lists.newArrayList();
        }
    }
}
