/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command.parse;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import com.emc.aix.model.MultiPathDevice;
import com.iwave.ext.text.TextParser;

public class MultiPathInquiryParser {

    private static Pattern DEVICE_BLOCK_START_PATTERN = Pattern.compile("DEVICE\\s+:VEND\\s+:PROD\\s+:WWN\\s");
    private static Pattern DEVICE_PATTERN = Pattern.compile("([\\/\\w]+)\\s+:(\\w+)\\s+:(\\w+)\\s+:(\\w{16,32})");
    
    private TextParser deviceBlockParser = new TextParser();
    
    public MultiPathInquiryParser() {
        deviceBlockParser.setStartPattern(DEVICE_BLOCK_START_PATTERN);
        deviceBlockParser.setRepeatPattern(DEVICE_PATTERN);
    }
    
    public List<MultiPathDevice> parseDevices(String output) {
        List<MultiPathDevice> devices = Lists.newArrayList();
        for (String deviceBlock : deviceBlockParser.parseTextBlocks(output)) {
            devices.add(parseDevice(deviceBlock));
        }
        return devices;
    }

    private MultiPathDevice parseDevice(String deviceBlock) {
        Matcher deviceMatcher = DEVICE_PATTERN.matcher(deviceBlock);
        if ( deviceMatcher.find() ) {
            MultiPathDevice device = new MultiPathDevice();
            device.setDevice(deviceMatcher.group(1));
            device.setVendor(deviceMatcher.group(2));
            device.setProduct(deviceMatcher.group(3));
            device.setWwn(deviceMatcher.group(4));
            return device;
        }
        return null;
    }

}