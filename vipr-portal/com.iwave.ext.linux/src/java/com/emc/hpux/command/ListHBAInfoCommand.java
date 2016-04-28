/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.text.TextParser;

public class ListHBAInfoCommand extends HpuxResultsCommand<List<HBAInfo>> {

    private static final Pattern DEVICE_PATTERN = Pattern.compile("host: /dev/fclp(\\d+)");
    private static final Pattern WWPN_PATTERN = Pattern.compile("N_Port Port World Wide Name = 0x([0-9a-fA-F]*)");
    private static final Pattern WWNN_PATTERN = Pattern.compile("N_Port Node World Wide Name = 0x([0-9a-fA-F]*)");

    public ListHBAInfoCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for DEVICE in $(/usr/sbin/ioscan -kfnCfc | awk '/dev/{print $1}')\n");
        sb.append("do \n");
        sb.append("  echo \"host: $DEVICE\" ; \n");
        sb.append("  /opt/fcms/bin/fcmsutil $DEVICE | grep \"World Wide Name\"  | grep \"N_Port\" \n");
        sb.append("done; ");

        setCommand(sb.toString());
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        results = Lists.newArrayList();
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            TextParser parser = new TextParser();
            parser.setRepeatPattern(DEVICE_PATTERN);

            for (String textBlock : parser.parseTextBlocks(stdout)) {
                String host = parser.findMatch(DEVICE_PATTERN, textBlock);
                if (StringUtils.isNotBlank(host)) {
                    HBAInfo hba = new HBAInfo();
                    hba.setHostId(Integer.parseInt(host));

                    String wwnn = parser.findMatch(WWNN_PATTERN, textBlock);
                    hba.setWwnn(normalizeWWN(wwnn));

                    String wwpn = parser.findMatch(WWPN_PATTERN, textBlock);
                    hba.setWwpn(normalizeWWN(wwpn));
                    results.add(hba);
                }
            }
        }
    }

    private String normalizeWWN(String wwn) {
        wwn = StringUtils.trim(wwn);
        wwn = StringUtils.leftPad(wwn, 16, '0');
        wwn = StringUtils.lowerCase(wwn);
        return wwn;
    }
}
