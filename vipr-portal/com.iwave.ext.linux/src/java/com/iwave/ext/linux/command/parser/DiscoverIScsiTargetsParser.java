/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command.parser;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.IScsiTarget;

public class DiscoverIScsiTargetsParser {
    private static final Pattern ISCSI_TARGETS = Pattern.compile("^([0-9.:,]+)\\s+(.*)$");
    private static final Pattern PORTAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}:\\d+,\\d+");

    public List<IScsiTarget> parseTargets(String text) {
        List<IScsiTarget> targets = Lists.newArrayList();

        for (String line : StringUtils.split(text, "\n")) {
            Matcher m = ISCSI_TARGETS.matcher(line);
            if (m.find()) {
                String portal = m.group(1);
                String iqn = m.group(2);
                if (isValidPortal(portal)) {
                    targets.add(new IScsiTarget(iqn, portal, null));
                }
            }
        }
        return targets;
    }

    private boolean isValidPortal(String value) {
        return PORTAL.matcher(value).matches();
    }
}
