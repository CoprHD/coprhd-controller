/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.parser;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.IScsiTarget;
import com.iwave.ext.text.TextParser;

public class IScsiTargetParser {
    private static Pattern TARGET = Pattern.compile("Target:\\s*(.*)");
    private static Pattern PORTAL = Pattern.compile("Portal:\\s*(.*)");
    private static Pattern IFACE_NAME = Pattern.compile("Iface Name:\\s*(.*)");
    private TextParser parser = new TextParser();
    private TextParser portalParser = new TextParser();

    public IScsiTargetParser() {
        parser.setRepeatPattern(TARGET);
        portalParser.setRepeatPattern(PORTAL);
    }

    public List<IScsiTarget> parseTargets(String output) {
        List<IScsiTarget> results = Lists.newArrayList();

        for (String textBlock : parser.parseTextBlocks(output)) {
            for (IScsiTarget target : parseTargetBlock(textBlock)) {
                results.add(target);
            }
        }
        return results;
    }

    private List<IScsiTarget> parseTargetBlock(String text) {
        String iqn = parser.findMatch(TARGET, text);
        if (StringUtils.isNotBlank(iqn)) {
            List<IScsiTarget> targets = Lists.newArrayList();
            for (String textBlock : portalParser.parseTextBlocks(text)) {
                String portal = parser.findMatch(PORTAL, textBlock);
                String ifaceName = parser.findMatch(IFACE_NAME, textBlock);
                targets.add(new IScsiTarget(StringUtils.trim(iqn), StringUtils.trim(portal),
                        StringUtils.trim(ifaceName)));
            }
            return targets;
        }
        else {
            return Collections.emptyList();
        }
    }
}
