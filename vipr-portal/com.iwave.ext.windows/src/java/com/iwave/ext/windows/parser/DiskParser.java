/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.parser;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;

import com.iwave.ext.text.TextParser;
import com.iwave.ext.windows.model.Disk;

public class DiskParser extends TextParser {
    private static final Pattern DISK_PATTERN = Pattern.compile("Disk (\\d+) is now the selected disk");
    private static final String DISK_ID = "Disk ID";
    private static final String TYPE = "Type";
    private static final String STATUS = "Status";
    private static final String PATH = "Path";
    private static final String TARGET = "Target";
    private static final String LUN_ID = "LUN ID";
    private static final String LOCATION_PATH = "Location Path";
    private static final String CURRENT_READ_ONLY_STATE = "Current Read-only State";
    private static final String READ_ONLY = "Read-only";
    private static final String BOOT_DISK = "Boot Disk";
    private static final String PAGE_FILE_DISK = "Pagefile Disk";
    private static final String HIBERNATION_FILE_DISK = "Hibernation File Disk";
    private static final String CRASHDUMP_DISK = "Crashdump Disk";
    private static final String CLUSTERED_DISK = "Clustered Disk";

    public DiskParser() {
        setRepeatPattern(DISK_PATTERN);
    }

    public List<Disk> parseDisks(String text) {
        List<Disk> disks = Lists.newArrayList();

        List<String> textBlocks = parseTextBlocks(text);
        for (String textBlock : textBlocks) {
            Disk disk = parseDisk(textBlock);
            if (disk != null) {
                disks.add(disk);
            }
        }

        return disks;
    }

    protected Disk parseDisk(String text) {
        int number = getInt(findMatch(DISK_PATTERN, text));
        if (number < 0) {
            return null;
        }
        Disk disk = new Disk();
        disk.setNumber(number);

        Map<String, String> properties = parseProperties(text, ':');
        disk.setDiskId(properties.get(DISK_ID));
        disk.setType(properties.get(TYPE));
        disk.setStatus(properties.get(STATUS));
        disk.setPath(getInt(properties.get(PATH)));
        disk.setTarget(getInt(properties.get(TARGET)));
        disk.setLunId(getInt(properties.get(LUN_ID)));
        disk.setLocationPath(properties.get(LOCATION_PATH));
        disk.setCurrentReadOnlyState(getYesNo(properties.get(CURRENT_READ_ONLY_STATE)));
        disk.setReadOnly(getYesNo(properties.get(READ_ONLY)));
        disk.setBootDisk(getYesNo(properties.get(BOOT_DISK)));
        disk.setPageFileDisk(getYesNo(properties.get(PAGE_FILE_DISK)));
        disk.setHibernationFileDisk(getYesNo(properties.get(HIBERNATION_FILE_DISK)));
        disk.setCrashdumpDisk(getYesNo(properties.get(CRASHDUMP_DISK)));
        disk.setClusteredDisk(getYesNo(properties.get(CLUSTERED_DISK)));
        disk.setVolumes(new VolumeParser().parseVolumes(text));
        return disk;
    }

    protected int getInt(String value) {
        Integer integer = getInteger(value);
        return (integer != null) ? integer : -1;
    }
}
