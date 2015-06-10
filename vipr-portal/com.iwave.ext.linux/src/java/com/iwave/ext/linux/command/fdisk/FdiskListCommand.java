/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.fdisk;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Sets;
import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxResultsCommand;

/**
 * Lists disk devices using fdisk. Can optionally include or not include mapper
 * devices or non-mapper devices (ones in /dev/mapper/).
 * 
 * @author Chris Dail
 */
public class FdiskListCommand extends LinuxResultsCommand<Set<String>> {
    private static final Pattern diskPattern = Pattern.compile("Disk\\s(/dev.+)\\:");
    private static final String MAPPER_PREFIX = "/dev/mapper/";
    
    private boolean includeMapper = true;
    private boolean includeRegular = true;
    
    public FdiskListCommand() {
        setCommand(CommandConstants.FDISK);
        addArguments("-l");
        setRunAsRoot(true);
    }
    
    public void setIncludeMapper(boolean includeMapper) {
        this.includeMapper = includeMapper;
    }
    
    public void setIncludeRegular(boolean includeRegular) {
        this.includeRegular = includeRegular;
    }
    
    @Override
    public void parseOutput() {
        results = Sets.newLinkedHashSet();
        
        Matcher matcher = diskPattern.matcher(getOutput().getStdout());
        while (matcher.find()) {
            String device = matcher.group(1);
            if (includeMapper && includeRegular) {
                results.add(device);
            }
            else if (includeMapper && device.startsWith(MAPPER_PREFIX)) {
                results.add(device);
            }
            else if (includeRegular && !device.startsWith(MAPPER_PREFIX)) {
                results.add(device);
            }
        }
    }
}
