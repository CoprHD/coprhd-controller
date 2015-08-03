/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.parser;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.iwave.ext.windows.model.Volume;

public class VolumeParserTest {

    VolumeParser parser = null;

    @Before
    public void setup() {
        parser = new VolumeParser();
    }

    @Test
    public void testParseVolumes() {
        String text = "  Volume ###  Ltr  Label        Fs     Type        Size     Status     Info\r\n" +
                "  ----------  ---  -----------  -----  ----------  -------  ---------  --------\r\n" +
                "  Volume 1         System Rese  NTFS   Partition    100 MB  Healthy    System  \r\n" +
                "  Volume 2     C                NTFS   Partition     67 GB  Healthy    Boot    \r\n" +
                "DISKPART> ";

        List<Volume> volumes = parser.parseVolumes(text);

        assertEquals(2, volumes.size());

        Volume volume1 = volumes.get(0);
        assertEquals(1, volume1.getNumber());
        assertEquals("", volume1.getMountPoint());
        assertEquals("System Rese", volume1.getLabel());
        assertEquals("NTFS", volume1.getFileSystem());
        assertEquals("Partition", volume1.getType());
        assertEquals("100 MB", volume1.getSize());
        assertEquals("Healthy", volume1.getStatus());
        assertEquals("System", volume1.getInfo());

        Volume volume2 = volumes.get(1);
        assertEquals(2, volume2.getNumber());
        assertEquals("C", volume2.getMountPoint());
        assertEquals("", volume2.getLabel());
        assertEquals("NTFS", volume2.getFileSystem());
        assertEquals("Partition", volume2.getType());
        assertEquals("67 GB", volume2.getSize());
        assertEquals("Healthy", volume2.getStatus());
        assertEquals("Boot", volume2.getInfo());
    }

    @Test
    public void testParseVolumesLongLabel() {
        String text = "  Volume ###  Ltr  Label        Fs     Type        Size     Status     Info\r\n" +
                "  ----------  ---  -----------  -----  ----------  -------  ---------  --------\r\n" +
                "  Volume 1         ?V?X?e?????\\??????    NTFS   Partition    100 MB  Healthy    System  \r\n" +
                "  Volume 2     C                NTFS   Partition     33 GB  Healthy    Boot    \r\n" +
                "DISKPART> ";

        List<Volume> volumes = parser.parseVolumes(text);

        assertEquals(2, volumes.size());

        Volume volume1 = volumes.get(0);
        assertEquals(1, volume1.getNumber());
        assertEquals("", volume1.getMountPoint());
        assertEquals("?V?X?e?????", volume1.getLabel());
        assertEquals("NTFS", volume1.getFileSystem());
        assertEquals("Partition", volume1.getType());
        assertEquals("100 MB", volume1.getSize());
        assertEquals("Healthy", volume1.getStatus());
        assertEquals("System", volume1.getInfo());

        Volume volume2 = volumes.get(1);
        assertEquals(2, volume2.getNumber());
        assertEquals("C", volume2.getMountPoint());
        assertEquals("", volume2.getLabel());
        assertEquals("NTFS", volume2.getFileSystem());
        assertEquals("Partition", volume2.getType());
        assertEquals("33 GB", volume2.getSize());
        assertEquals("Healthy", volume2.getStatus());
        assertEquals("Boot", volume2.getInfo());
    }
}
