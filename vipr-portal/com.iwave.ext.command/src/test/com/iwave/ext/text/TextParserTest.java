/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.text;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;

import com.iwave.ext.text.TextParserTest.DiskUtilVolume.DiskUtilVolumeParser;

public class TextParserTest {

    private static final String DISKUTIL_FILENAME = "diskutil_list";

    @Test
    public void diskUtilTest() {
        String diskUtilOutput = readFile(DISKUTIL_FILENAME);
        List<DiskUtilDisk> disks = new DiskUtilDisk.DiskUtilParser().getDisks(diskUtilOutput);
        assertEquals(3, disks.size());
        assertDisk0(disks.get(0));
        assertDisk2(disks.get(1));
        assertDisk4(disks.get(2));
    }

    private void assertDisk0(DiskUtilDisk disk) {
        disk.dump();

        assertEquals(4, disk.volumes.size());

        DiskUtilVolume vol1 = disk.volumes.get(0);
        assertEquals(0, vol1.number);
        assertTrue(vol1.partitionMap);
        assertEquals("", vol1.name.trim());
        assertEquals("GUID_partition_scheme", vol1.type);
        assertEquals(251.0, vol1.size, 0.0);
        assertEquals("GB", vol1.units);
        assertEquals("disk0", vol1.id);

        DiskUtilVolume vol2 = disk.volumes.get(1);
        assertFalse(vol2.partitionMap);
        assertEquals(1, vol2.number);
        assertEquals("", vol2.name.trim());
        assertEquals("EFI", vol2.type);
        assertEquals(209.7, vol2.size, 0.0);
        assertEquals("MB", vol2.units);
        assertEquals("disk0s1", vol2.id);

        DiskUtilVolume vol3 = disk.volumes.get(2);
        assertFalse(vol3.partitionMap);
        assertEquals(2, vol3.number);
        assertEquals("Macintosh HD", vol3.name.trim());
        assertEquals("Apple_HFS", vol3.type);
        assertEquals(250.1, vol3.size, 0.0);
        assertEquals("GB", vol3.units);
        assertEquals("disk0s2", vol3.id);

        DiskUtilVolume vol4 = disk.volumes.get(3);
        assertFalse(vol4.partitionMap);
        assertEquals(3, vol4.number);
        assertEquals("Recovery HD", vol4.name.trim());
        assertEquals("Apple_Boot", vol4.type);
        assertEquals(650.0, vol4.size, 0.0);
        assertEquals("MB", vol4.units);
        assertEquals("disk0s3", vol4.id);
    }

    private void assertDisk2(DiskUtilDisk disk) {
        disk.dump();

        assertEquals(4, disk.volumes.size());

        DiskUtilVolume vol1 = disk.volumes.get(0);
        assertEquals(0, vol1.number);
        assertTrue(vol1.partitionMap);
        assertEquals("", vol1.name.trim());
        assertEquals("FDisk_partition_scheme", vol1.type);
        assertEquals(1.0, vol1.size, 0.0);
        assertEquals("TB", vol1.units);
        assertEquals("disk2", vol1.id);

        DiskUtilVolume vol2 = disk.volumes.get(1);
        assertFalse(vol2.partitionMap);
        assertEquals(1, vol2.number);
        assertEquals("exFAT", vol2.name.trim());
        assertEquals("Windows_NTFS", vol2.type);
        assertEquals(925.3, vol2.size, 0.0);
        assertEquals("GB", vol2.units);
        assertEquals("disk2s1", vol2.id);

        DiskUtilVolume vol3 = disk.volumes.get(2);
        assertFalse(vol3.partitionMap);
        assertEquals(2, vol3.number);
        assertEquals("HFS+", vol3.name.trim());
        assertEquals("Apple_HFS", vol3.type);
        assertEquals(50.1, vol3.size, 0.0);
        assertEquals("GB", vol3.units);
        assertEquals("disk2s2", vol3.id);

        DiskUtilVolume vol4 = disk.volumes.get(3);
        assertFalse(vol4.partitionMap);
        assertEquals(3, vol4.number);
        assertEquals("FAT32", vol4.name.trim());
        assertEquals("DOS_FAT_32", vol4.type);
        assertEquals(24.7, vol4.size, 0.0);
        assertEquals("GB", vol4.units);
        assertEquals("disk2s3", vol4.id);
    }

    private void assertDisk4(DiskUtilDisk disk) {
        disk.dump();

        assertEquals(1, disk.volumes.size());

        DiskUtilVolume vol1 = disk.volumes.get(0);
        assertEquals(0, vol1.number);
        assertTrue(vol1.partitionMap);
        assertEquals("Ubuntu 10.04.4 L", vol1.name.trim());
        assertEquals(null, vol1.type);
        assertEquals(728.2, vol1.size, 0.0);
        assertEquals("MB", vol1.units);
        assertEquals("disk4", vol1.id);
    }

    static class DiskUtilDisk {

        public String name;
        public List<DiskUtilVolume> volumes;

        public void dump() {
            println("Disk Name:\t%s", name);
            for (DiskUtilVolume volume : volumes) {
                volume.dump();
            }
            println();
        }

        static class DiskUtilParser {

            private static final String DISK_REGEX = "\\/\\w+\\/([\\w\\d]+)";
            // \/\w+\/([\w\d]+)

            private static final String DISK_UTIL_UNTIL_NEXT_REGEX = "(?:(?!\\/dev).*\\n?)*";
            // (?:(?!\/dev).*\n?)*

            private static final String DISK_UTIL_BLOCK_REGEX = "(" + DISK_REGEX + "\\n" + DISK_UTIL_UNTIL_NEXT_REGEX + ")";

            private static final String HEADER_REGEX = "\\s+#:\\s+TYPE\\sNAME\\s+SIZE\\s+IDENTIFIER";
            // \s+#:\s+TYPE\sNAME\s+SIZE\s+IDENTIFIER$

            public static final Pattern DISK_UTIL_START_PATTERN = Pattern.compile(DISK_REGEX + "\\n" + HEADER_REGEX + "\\n");
            public static final Pattern DISK_UTIL_BLOCK_PATTERN = Pattern.compile(DISK_UTIL_BLOCK_REGEX);
            public static final Pattern DISK_PATTERN = Pattern.compile(DISK_REGEX);

            private final TextParser diskParser;
            private final DiskUtilVolumeParser volumeParser;

            public DiskUtilParser() {
                diskParser = new TextParser();
                diskParser.setStartPattern(DISK_UTIL_START_PATTERN);
                diskParser.setRepeatPattern(DISK_UTIL_BLOCK_PATTERN);
                volumeParser = new DiskUtilVolumeParser();
            }

            public List<DiskUtilDisk> getDisks(String diskUtilOutput) {
                List<DiskUtilDisk> disks = new ArrayList<DiskUtilDisk>();
                List<String> diskBlocks = diskParser.parseTextBlocks(diskUtilOutput);
                for (String diskBlock : diskBlocks) {
                    disks.add(parseDisk(diskBlock));
                }
                return disks;
            }

            private DiskUtilDisk parseDisk(String diskBlock) {
                Matcher diskMatch = DISK_PATTERN.matcher(diskBlock);
                if (diskMatch.find()) {
                    DiskUtilDisk disk = new DiskUtilDisk();
                    disk.name = diskMatch.group(0);
                    disk.volumes = volumeParser.getDiskUtilVolumes(diskBlock);
                    return disk;
                }
                return null;
            }

        }

    }

    static class DiskUtilVolume {

        public int number;
        public String type;
        public String name;
        public boolean partitionMap;
        public double size;
        public String units;
        public String id;

        static class DiskUtilVolumeParser {

            private static final String VOLUME_REGEX = "\\s+(\\d+):\\s{1,27}(\\w+)?\\s([\\w\\W]+?)\\s+(\\*?)(\\d+\\.\\d+)\\s(MB|GB|TB)\\s+(\\w+)";
            // \s+(\d+):\s{1,27}(\w+)?\s([\w\W]+?)\s+(\*?)(\d+\.\d+)\s(MB|GB|TB)\s+(\w+)

            private static final Pattern VOLUME_PATTERN = Pattern.compile(VOLUME_REGEX);

            private final TextParser volumeParser;

            public DiskUtilVolumeParser() {
                volumeParser = new TextParser();
                volumeParser.setStartPattern(DiskUtilDisk.DiskUtilParser.DISK_UTIL_START_PATTERN);
                volumeParser.setRepeatPattern(VOLUME_PATTERN);
            }

            public List<DiskUtilVolume> getDiskUtilVolumes(String diskUtilBlock) {
                List<DiskUtilVolume> volumes = new ArrayList<DiskUtilVolume>();
                List<String> volumeBlocks = volumeParser.parseTextBlocks(diskUtilBlock);
                for (String volumeBlock : volumeBlocks) {
                    Matcher volumeMatch = DiskUtilVolumeParser.VOLUME_PATTERN.matcher(volumeBlock);
                    if (volumeMatch.find()) {
                        DiskUtilVolume volume = new DiskUtilVolume();
                        volume.number = Integer.valueOf(volumeMatch.group(1));
                        volume.type = volumeMatch.group(2);
                        volume.name = volumeMatch.group(3);
                        volume.partitionMap = StringUtils.equals(volumeMatch.group(4), "*");
                        volume.size = Double.valueOf(volumeMatch.group(5));
                        volume.units = volumeMatch.group(6);
                        volume.id = volumeMatch.group(7);
                        volumes.add(volume);
                    }
                }
                return volumes;
            }

        }

        public void dump() {
            println("\tVolume Name:\t%s", name);
            println("\t\tPartition Map:\t%s", partitionMap);
            println("\t\tVolume Id:\t%s", id);
            println("\t\tVolume #:\t%s", number);
            println("\t\tVolume Type:\t%s", type);
            println("\t\tVolume Size:\t%s %s", size, units);
        }

    }

    protected String readFile(String filename) {
        InputStream diskUtilFile = getClass().getResourceAsStream(filename);

        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(diskUtilFile, writer);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        String text = writer.toString();
        return text;
    }

    public static void println() {
        println("");
    }

    public static void println(String format, Object... args) {
        print(format, args);
        print("\n");
    }

    public static void print(String format, Object... args) {
        System.out.print(String.format(format, args));
    }
}
