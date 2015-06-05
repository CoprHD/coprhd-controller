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
import com.iwave.ext.linux.model.VolumeGroup;
import com.iwave.ext.text.TextParser;

public class VolumeGroupParser {
    private static final Pattern VOLUME_GROUP = Pattern.compile("--- Volume group ---");
    private static final Pattern VOLUME_GROUP_NAME = Pattern.compile("VG Name\\s+(.+)");
    private static final Pattern LOGICAL_VOLUME_NAME = Pattern.compile("LV Name\\s+(.+)");
    private static final Pattern LOGICAL_VOLUME_PATH = Pattern.compile("LV Path\\s+(.+)");
    private static final Pattern PHYSICAL_VOLUME_NAME = Pattern.compile("PV Name\\s+(.+)");

    private TextParser parser;

    public VolumeGroupParser() {
        parser = new TextParser();
        parser.setRepeatPattern(VOLUME_GROUP);
    }

    public List<VolumeGroup> parseVolumeGroups(String text) {
        List<VolumeGroup> results = Lists.newArrayList();

        List<String> textBlocks = parser.parseTextBlocks(text);
        for (String textBlock : textBlocks) {
            VolumeGroup vg = parseVolumeGroup(textBlock);
            if (vg != null) {
                results.add(vg);
            }
        }

        return results;
    }

    public VolumeGroup parseVolumeGroup(String text) {
        VolumeGroup vg = new VolumeGroup();
        vg.setName(findMatch(VOLUME_GROUP_NAME, text));

        // Use the logical volume paths if they are there, otherwise use logical volume name.
        List<String> logicalVolumes = findMatches(LOGICAL_VOLUME_PATH, text);
        if (logicalVolumes.isEmpty()) {
            logicalVolumes = findMatches(LOGICAL_VOLUME_NAME, text);
        }
        vg.setLogicalVolumes(logicalVolumes);

        vg.setPhysicalVolumes(findMatches(PHYSICAL_VOLUME_NAME, text));
        return vg;
    }

    private String findMatch(Pattern pattern, String text) {
        return StringUtils.trim(parser.findMatch(pattern, text));
    }

    private List<String> findMatches(Pattern pattern, String text) {
        List<String> results = Lists.newArrayList();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            results.add(StringUtils.trim(m.group(1)));
        }
        return results;
    }

}
