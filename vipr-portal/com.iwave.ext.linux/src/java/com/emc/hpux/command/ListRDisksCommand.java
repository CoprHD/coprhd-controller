/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.emc.hpux.model.RDisk;

public class ListRDisksCommand extends HpuxResultsCommand<List<RDisk>> {

    private static Pattern IQN_PATTERN = Pattern.compile("(.+):0x(.+)");

    public ListRDisksCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("scsimgr -p get_attr all_lun -a device_file -a wwid");
        setCommand(sb.toString());
    }

    @Override
    public void parseOutput() {
        results = new ArrayList<RDisk>();

        String stdout = getOutput().getStdout();
        if (StringUtils.isNotBlank(stdout)) {
            Matcher m = IQN_PATTERN.matcher(stdout);
            while (m.find()) {
                RDisk disk = new RDisk(m.group(1).replace("/dev/rdisk", "/dev/disk"), m.group(1), m.group(2));
                results.add(disk);
            }
        }
    }
}