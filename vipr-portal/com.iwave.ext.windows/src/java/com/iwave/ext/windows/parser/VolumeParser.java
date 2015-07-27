/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.parser;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import com.google.common.collect.Lists;

import com.iwave.ext.text.TextParser;
import com.iwave.ext.windows.model.Volume;

public class VolumeParser extends TextParser {
    private static final Pattern START_PATTERN = Pattern.compile(" +Volume ###");
    private static final Pattern END_PATTERN = Pattern.compile("DISKPART>");
    private static final Pattern VOLUME_PATTERN = Pattern.compile("Volume (\\d+)");
    private static final int MAX_VOLUME_LINE_LENGTH = 78;

    public VolumeParser() {
        setRequiredStartPattern(START_PATTERN);
        setOptionalEndPattern(END_PATTERN);
        setRepeatPattern(VOLUME_PATTERN);
    }

    public List<Volume> parseVolumes(String text) {
        List<Volume> volumes = Lists.newArrayList();

        List<String> textBlocks = parseTextBlocks(text);
        for (String textBlock : textBlocks) {
            Volume volume = parseVolume(textBlock);
            if (volume != null) {
                volumes.add(volume);
            }
        }

        return volumes;
    }

    private Volume parseVolume(String text) {
        String line = StringUtils.substringBefore(text, "\n");
        Volume volume = new Volume();
        volume.setNumber(getInteger(findMatch(VOLUME_PATTERN, line)));
        volume.setMountPoint(getColumnText(line, 12, 3));
        volume.setLabel(getColumnText(line, 17, 11));

        // set offset for labels that overflows 11 characters
        int offset = (line.length() - MAX_VOLUME_LINE_LENGTH);

        volume.setFileSystem(getColumnText(line, offset+30, 5));
        volume.setType(getColumnText(line, offset+37, 10));
        volume.setSize(getColumnText(line, offset+49, 7));
        volume.setStatus(getColumnText(line, offset+58, 9));
        volume.setInfo(getColumnText(line, offset+69, 8));
        
        // if the volume mountPoint is blank it probably means we're dealing with a volume mounted at a file path,
        // and the mountpoint info is actually on the next line.
        if (StringUtils.isBlank(volume.getMountPoint())) {
            String line2 = StringUtils.substringAfter(text, "\n");
            volume.setMountPoint(line2.trim());
        }

        return volume;
    }

    private String getColumnText(String text, int start, int length) {
        if (start < text.length()) {
            String column = StringUtils.substring(text, start, start + length);
            return StringUtils.trimToEmpty(column);
        }
        else {
            return "";
        }
    }
}
