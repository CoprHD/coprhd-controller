/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;

import com.google.common.collect.Lists;
import com.iwave.ext.command.CommandOutput;

public class WindowsUtils {
    /** Message indicating that there was an error in DiskPart. */
    private static final String DISKPART_ERROR = "DiskPart has encountered an error:";
    private static final String DISKPART_VIRTUAL_DISK_ERROR = "Virtual Disk Service error:";
    /** DiskPart prompt in the output. */
    private static final String DISKPART_PROMPT = "DISKPART>";
    /** FAT drive labels are limited to 11 chars. */
    private static final int MAX_DRIVE_LABEL_LENGTH = 11;
    /** NTFS drive labels are limited to 32 chars. */
    private static final int MAX_DRIVE_LABEL_LENGTH_NTFS = 32;

    private static final String FS_TYPE_NTFS = "ntfs";
    private static final String FS_TYPE_FAT32 = "fat32";

    public static final String PARTITION_TYPE_MBR = "MBR";
    public static final String PARTITION_TYPE_GPT = "GPT";

    // 32 GB
    private static final long MAX_BYTES_FAT32 = (32L * 1024 * 1024 * 1024) - 1;

    // 2 TB
    private static final long MAX_BYTES_2TB = (2L * 1024 * 1024 * 1024 * 1024) - 1;

    public static List<String> getRescanCommands() {
        return Lists.newArrayList("RESCAN");
    }

    public static List<String> getFormatAndMountDiskCommands(int diskNumber, String fsType, String allocationUnitSize, String label,
            String mountpoint, String partitionType) {
        List<String> commands = Lists.newArrayList();
        commands.add("SELECT DISK " + diskNumber);
        commands.addAll(getFormatCommands(fsType, allocationUnitSize, label, partitionType));
        commands.add(getAssignCommand(mountpoint));
        return commands;
    }

    public static List<String> getMountVolumeCommands(int volumeNumber, String mountpoint) {
        List<String> commands = Lists.newArrayList();
        commands.add("SELECT VOLUME " + volumeNumber);
        commands.add("ONLINE VOLUME");
        commands.add(getAssignCommand(mountpoint));
        return commands;
    }

    public static String getAssignCommand(String mountpoint) {
        if (StringUtils.isNotBlank(mountpoint)) {
            if (mountpoint.length() == 1) {
                return "ASSIGN LETTER=" + mountpoint;
            }
            else {
                return "ASSIGN MOUNT=" + mountpoint;
            }
        }
        else {
            return "ASSIGN";
        }
    }

    public static String getPartitionType(String partitionType) {
        if (isGPT(partitionType)) {
            return "CONVERT GPT";
        }
        return "";
    }

    public static List<String> getUnmountVolumeCommands(int volumeNumber, String mountPoint) {
        List<String> commands = Lists.newArrayList();
        commands.add("SELECT VOLUME " + volumeNumber);
        commands.add("REMOVE");
        return commands;
    }

    private static List<String> getFormatCommands(String fsType, String allocationUnitSize, String label, String partitionType) {
        return Lists.newArrayList("CLEAN", getPartitionType(partitionType), "CREATE PARTITION PRIMARY", "ONLINE VOLUME",
                getFormatCommand(fsType, allocationUnitSize, label));
    }

    private static String getFormatCommand(String fsType, String allocationUnitSize, String label) {
        StrBuilder format = new StrBuilder();
        format.append("FORMAT QUICK");
        if (StringUtils.isNotBlank(fsType)) {
            format.append(" FS=").append(fsType);
        }
        if (StringUtils.isNotBlank(allocationUnitSize)) {
            format.append(" UNIT=").append(allocationUnitSize);
        }
        if (StringUtils.isNotBlank(label)) {
            format.append(" LABEL=").append(normalizeDriveLabel(fsType, label));
        }
        return format.toString();
    }

    public static List<String> getDetailDiskCommands(int diskNumber) {
        return Lists.newArrayList("SELECT DISK " + diskNumber, "DETAIL DISK");
    }

    public static List<String> getOnlineDiskCommands(int diskNumber, boolean currentReadOnlyState) {
        List<String> commands = Lists.newArrayList();
        commands.add("SELECT DISK " + diskNumber);
        if (currentReadOnlyState) {
            commands.add("ATTRIBUTES DISK CLEAR READONLY");
        }
        commands.add("ONLINE DISK");
        return commands;
    }

    public static List<String> getOfflineDiskCommands(int diskNumber) {
        return Lists.newArrayList("SELECT DISK " + diskNumber, "OFFLINE DISK");
    }

    public static List<String> getExtendVolumeCommands(String mountpoint) {
        return Lists.newArrayList("SELECT VOLUME " + mountpoint, "EXTEND");
    }

    public static List<String> getListDiskCommands() {
        return Lists.newArrayList("LIST DISK");
    }

    public static String getDiskPartError(CommandOutput output) {
        String error = getDiskPartError(output.getStdout());
        if (error == null) {
            error = getDiskPartError(output.getStderr());
        }
        return error;
    }

    public static String getDiskPartError(String output) {
        String error = StringUtils.substringAfter(output, DISKPART_ERROR);
        if (StringUtils.isNotBlank(error)) {
            return StringUtils.trim(StringUtils.substringBefore(error, DISKPART_PROMPT));
        }
        error = StringUtils.substringAfter(output, DISKPART_VIRTUAL_DISK_ERROR);
        if (StringUtils.isNotBlank(error)) {
            return StringUtils.trim(StringUtils.substringBefore(error, DISKPART_PROMPT));
        }
        return null;
    }

    public static String normalizeDriveLabel(String fsType, String label) {
        if (StringUtils.isNotBlank(label)) {
            String driveLabel = StringUtils.trim(label);
            driveLabel = driveLabel.replaceAll("[^A-Za-z0-9_]", "_");
            if (isNTFS(fsType)) {
                driveLabel = StringUtils.substring(driveLabel, 0, MAX_DRIVE_LABEL_LENGTH_NTFS);
            }
            else {
                driveLabel = StringUtils.substring(driveLabel, 0, MAX_DRIVE_LABEL_LENGTH);
            }
            return driveLabel;
        }
        else {
            return null;
        }
    }

    public static boolean isNTFS(String fileSystemType) {
        return StringUtils.equals(fileSystemType, FS_TYPE_NTFS);
    }

    public static boolean isFat32(String fileSystemType) {
        return StringUtils.equals(fileSystemType, FS_TYPE_FAT32);
    }

    public static boolean isFat32CapacityInBytesTooLarge(long capacityInBytes) {
        return capacityInBytes > MAX_BYTES_FAT32;
    }

    public static boolean isGPT(String partitionType) {
        return StringUtils.equals(partitionType, PARTITION_TYPE_GPT);
    }

    public static boolean isMBR(String partitionType) {
        return StringUtils.equals(partitionType, PARTITION_TYPE_MBR);
    }

    public static boolean isMBRCapacityInBytesTooLarge(long capacityInBytes) {
        return capacityInBytes > MAX_BYTES_2TB;
    }

}
