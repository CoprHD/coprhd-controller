/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.parser;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.math.NumberUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.LunInfo;
import com.iwave.ext.text.TextParser;

public class LunInfoParser extends TextParser {
    private static final Pattern HOST = Pattern.compile("Host\\:\\s+scsi(\\d+)");
    private static final Pattern CHANNEL = Pattern.compile("Channel\\:\\s+(\\d+)");
    private static final Pattern ID = Pattern.compile("Id\\:\\s+(\\d+)");
    private static final Pattern LUN = Pattern.compile("Lun\\:\\s+(\\d+)");
    private static final Pattern VENDOR = Pattern.compile("Vendor\\:\\s+(\\w+)");
    private static final Pattern MODEL = Pattern.compile("Model\\:\\s+(\\w+)");
    private static final Pattern REVISION = Pattern.compile("Rev\\:\\s+(\\w+)");
    private static final Pattern TYPE = Pattern.compile("Type\\:\\s+(\\w+)");
    private static final Pattern SCSI_REVISION = Pattern.compile("ANSI\\s+SCSI\\s+revision\\: (\\w+)");

    public LunInfoParser() {
        setRepeatPattern(HOST);
    }

    public List<LunInfo> parseLunInfos(String text) {
        List<LunInfo> results = Lists.newArrayList();
        for (String textBlock : parseTextBlocks(text)) {
            LunInfo lunInfo = parseLunInfo(textBlock);
            if (lunInfo != null) {
                results.add(lunInfo);
            }
        }
        return results;
    }

    public LunInfo parseLunInfo(String text) {
        String host = findMatch(HOST, text);
        if (host == null) {
            return null;
        }
        String channel = findMatch(CHANNEL, text);
        if (channel == null) {
            return null;
        }
        String id = findMatch(ID, text);
        if (id == null) {
            return null;
        }
        String lun = findMatch(LUN, text);
        if (lun == null) {
            return null;
        }
        String vendor = findMatch(VENDOR, text);
        String model = findMatch(MODEL, text);
        String revision = findMatch(REVISION, text);
        String type = findMatch(TYPE, text);
        String scsiRevision = findMatch(SCSI_REVISION, text);

        LunInfo lunInfo = new LunInfo();
        lunInfo.setHost(NumberUtils.toInt(host, -1));
        lunInfo.setChannel(NumberUtils.toInt(channel, -1));
        lunInfo.setId(NumberUtils.toInt(id, -1));
        lunInfo.setLun(NumberUtils.toInt(lun, -1));
        lunInfo.setVendor(vendor);
        lunInfo.setModel(model);
        lunInfo.setRevision(revision);
        lunInfo.setType(type);
        lunInfo.setScsiRevision(scsiRevision);
        return lunInfo;
    }
}
