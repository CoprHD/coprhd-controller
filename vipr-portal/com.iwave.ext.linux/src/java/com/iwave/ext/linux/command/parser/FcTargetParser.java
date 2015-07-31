/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.parser;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import com.google.common.collect.Lists;

import com.iwave.ext.linux.model.FcTarget;
import com.iwave.ext.text.TextParser;

public class FcTargetParser {
    private static final Pattern TARGET_PATTERN = Pattern
            .compile("target: target(\\d+):(\\d+):(\\d+)");
    private static final Pattern NODE_NAME_PATTERN = Pattern
            .compile("node:\\s*0x([0-9a-fA-F]*)\\b");
    private static final Pattern PORT_NAME_PATTERN = Pattern
            .compile("port:\\s*0x([0-9a-fA-F]*)\\b");

    private TextParser parser;

    public FcTargetParser() {
        parser = new TextParser();
        parser.setRepeatPattern(TARGET_PATTERN);
    }

    public List<FcTarget> parseFcTargets(String text) {
        List<FcTarget> targets = Lists.newArrayList();
        for (String textBlock : parser.parseTextBlocks(text)) {
            FcTarget target = parseFcTarget(textBlock);
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    public FcTarget parseFcTarget(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Matcher m = TARGET_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }

        FcTarget target = new FcTarget();
        target.setScsiHost(NumberUtils.toInt(m.group(1)));
        target.setScsiChannel(NumberUtils.toInt(m.group(2)));
        target.setScsiId(NumberUtils.toInt(m.group(3)));

        String nodeName = parser.findMatch(NODE_NAME_PATTERN, text);
        target.setNodeName(normalizeWWN(nodeName));

        String portName = parser.findMatch(PORT_NAME_PATTERN, text);
        target.setPortName(normalizeWWN(portName));

        return target;
    }

    private String normalizeWWN(String wwn) {
        wwn = StringUtils.trim(wwn);
        wwn = StringUtils.leftPad(wwn, 16, '0');
        wwn = StringUtils.lowerCase(wwn);
        return wwn;
    }
}
