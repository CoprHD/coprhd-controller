/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.parser;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import com.iwave.ext.windows.model.DiskSummary;
import com.iwave.ext.text.TextParser;

public class ListDiskParser {
    private static Pattern DISK_START_PATTERN =
            Pattern.compile("Disk ###\\s+Status\\s+Size\\s+Free\\s+Dyn\\s+Gpt");

    private static Pattern DISK_PATTERN =
            Pattern.compile("\\s{2}(Disk\\s\\d+)\\s+(\\w*)\\s+(\\w*\\s\\w*)\\s+(\\w*\\s\\w*)\\s{3}([*]|\\s)\\s{4}([*]|\\s)");

    private TextParser diskParser = new TextParser();

    public ListDiskParser() {
        diskParser.setStartPattern(DISK_START_PATTERN);
        diskParser.setRepeatPattern(DISK_PATTERN);
    }

    public List<DiskSummary> parseDevices(String output) {
        List<DiskSummary> disks = Lists.newArrayList();
        for (String diskInfo : diskParser.parseTextBlocks(output)) {
            disks.add(parseDisk(diskInfo));
        }
        return disks;
    }

    private DiskSummary parseDisk(String diskInfo) {
        Matcher diskMatcher = DISK_PATTERN.matcher(diskInfo);
        if (diskMatcher.find()) {
            DiskSummary disk = new DiskSummary();
            disk.setDiskNumber(diskMatcher.group(1));
            disk.setStatus(diskMatcher.group(2));
            disk.setSize(diskMatcher.group(3));
            disk.setFree(diskMatcher.group(4));
            disk.setDyn(diskMatcher.group(5));
            disk.setGpt(diskMatcher.group(6));
            return disk;
        }
        return null;
    }
}

// Active code page: 437
//
// Microsoft DiskPart version 6.2.9200
//
// Copyright (C) 1999-2012 Microsoft Corporation.
// On computer: LGLW7150
//
// DISKPART>
// Disk ### Status Size Free Dyn Gpt
// -------- ------------- ------- ------- --- ---
// Disk 0 Online 135 GB 0 B
// Disk 3 Offline 2048 MB 2048 MB
// Disk 5 Online 16 GB 0 B *
// Disk 7 Offline 2048 MB 2048 MB
// Disk 9 Offline 3072 MB 3072 MB
// Disk 10 Offline 1024 MB 1024 MB
// Disk 11 Offline 4096 MB 4096 MB
// Disk 12 Offline 2048 MB 2048 MB
// Disk 13 Offline 2048 MB 2048 MB
// Disk 14 Offline 7168 MB 7168 MB
// Disk 15 Offline 5120 MB 5120 MB
// Disk 16 Offline 2048 MB 2048 MB
// Disk 17 Online 20 GB 4097 MB *
// Disk 18 Offline 1024 MB 1024 MB
// Disk 19 Offline 2048 MB 2048 MB
// Disk 20 Offline 240 MB 0 B
// Disk 21 Offline 3072 MB 3072 MB
// Disk 22 Online 2048 MB 0 B
// Disk 23 Online 2048 MB 0 B
// Disk 24 Offline 2048 MB 2048 MB
// Disk 26 Offline 0 B 0 B
// Disk 27 Online 2048 MB 0 B
// Disk 28 Offline 0 B 0 B
// Disk 29 Offline 0 B 0 B
// Disk 30 Offline 0 B 0 B
// Disk 38 Reserved 1024 MB 0 B
// Disk 39 Reserved 4096 MB 0 B
// Disk 40 Reserved 3072 MB 0 B
// Disk 41 Reserved 2048 MB 0 B
// Disk 42 Offline 2048 MB 2048 MB
// Disk 43 Reserved 2048 MB 960 KB
// Disk 44 Offline 2048 MB 2048 MB
// Disk 45 Offline 2048 MB 2048 MB
// Disk 46 Reserved 2048 MB 960 KB
// Disk 47 Reserved 2048 MB 960 KB
// Disk 48 Reserved 3072 MB 960 KB
// Disk 49 Reserved 2048 MB 0 B
// Disk 54 Offline 1024 MB 1024 MB
//
// DISKPART>
// Leaving DiskPart...

