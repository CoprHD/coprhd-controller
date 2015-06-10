/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */

package com.iwave.ext.linux.command.parser;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.PowerPathDevice;
import com.iwave.ext.text.TextParser;

public class PowerPathInvistaInquiryParser {

    private static Pattern DEVICE_BLOCK_START_PATTERN = 
            Pattern.compile("Invista\\sDevice\\s+Array\\sSerial\\s#\\s+SP\\s+IP\\s+Address\\s+LUN\\s+WWN\\s+");

    private static Pattern DEVICE_PATTERN = 
            Pattern.compile("([\\/\\w]+)\\s+(\\w+)\\s+(\\S*)\\s+(\\w+)\\s+(\\w{32})");
   
    private TextParser deviceBlockParser = new TextParser();
    
    public PowerPathInvistaInquiryParser() {
        deviceBlockParser.setStartPattern(DEVICE_BLOCK_START_PATTERN);
        deviceBlockParser.setRepeatPattern(DEVICE_PATTERN);
    }
    
    public List<PowerPathDevice> parseDevices(String output) {
        List<PowerPathDevice> devices = Lists.newArrayList();
        for (String deviceBlock : deviceBlockParser.parseTextBlocks(output)) {
            devices.add(parseDevice(deviceBlock));
        }
        return devices;
    }

    private PowerPathDevice parseDevice(String deviceBlock) {
        Matcher deviceMatcher = DEVICE_PATTERN.matcher(deviceBlock);
        if ( deviceMatcher.find() ) {
            PowerPathDevice device = new PowerPathDevice();
            device.setDevice(deviceMatcher.group(1));
            device.setVendor("EMC");
            device.setProduct("Invista");
            device.setWwn(deviceMatcher.group(5));
            return device;
        }
        return null;
    }

}