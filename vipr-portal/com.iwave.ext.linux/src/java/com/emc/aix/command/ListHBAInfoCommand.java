/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.text.TextParser;

public class ListHBAInfoCommand extends AixResultsCommand<List<HBAInfo>> {

    private static final Pattern HOST_PATTERN = Pattern.compile("host: fcs(\\d+)");
    private static final Pattern WWPN_PATTERN = Pattern.compile("Network Address[.]+([0-9a-fA-F]*)");
    private static final Pattern WWNN_PATTERN = Pattern.compile("Device Specific.\\(Z8\\)[.]+([0-9a-fA-F]*)");

    public ListHBAInfoCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for host in `lsdev -Cc adapter -S 1 | grep ^fcs | awk '{print $1}'`; do ");
        sb.append("  echo \"host: $host\" ; ");
        sb.append("  lscfg -vpl $host | grep -E -i -w 'Network Address|Device Specific.\\(Z8\\)'; ");
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
            parser.setRepeatPattern(HOST_PATTERN);

            for (String textBlock : parser.parseTextBlocks(stdout)) {
                String host = parser.findMatch(HOST_PATTERN, textBlock);
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
