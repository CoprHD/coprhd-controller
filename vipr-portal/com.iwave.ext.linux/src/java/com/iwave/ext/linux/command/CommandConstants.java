/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

public class CommandConstants {
    public static final String FDISK = "/sbin/fdisk";
    public static final String MOUNT = "mount";
    public static final String UMOUNT = "umount";
    public static final String IFCONFIG = "/sbin/ifconfig";
    public static final String MULTIPATH = "/sbin/multipath";
    public static final String POWERMT = "PATH=/sbin:/usr/sbin powermt";
    public static final String POWERPATHINQUIRY = "/sbin/pp_inq";
    public static final String ISCSIADM = "/sbin/iscsiadm";
    public static final String MKE2FS = "/sbin/mke2fs";
    public static final String E2FSCK = "/sbin/e2fsck";
    public static final String RESIZE2FS = "/sbin/resize2fs";
    public static final String MKFS = "/sbin/mkfs";
    public static final String TIMEOUT = "timeout";
    public static final String DF = "df -P";

    public static final String VGDISPLAY = "PATH=/sbin:/usr/sbin vgdisplay";
    public static final String VGCREATE = "PATH=/sbin:/usr/sbin vgcreate";
    public static final String VGCHANGE = "PATH=/sbin:/usr/sbin vgchange";
    public static final String VGEXTEND = "PATH=/sbin:/usr/sbin vgextend";
    public static final String VGREMOVE = "PATH=/sbin:/usr/sbin vgremove";
    public static final String VGREDUCE = "PATH=/sbin:/usr/sbin vgreduce";
    public static final String LVDISPLAY = "PATH=/sbin:/usr/sbin lvdisplay";
    public static final String LVCREATE = "PATH=/sbin:/usr/sbin lvcreate";
    public static final String LVCHANGE = "PATH=/sbin:/usr/sbin lvchange";
    public static final String LVREMOVE = "PATH=/sbin:/usr/sbin lvremove";
    public static final String LVRESIZE = "PATH=/sbin:/usr/sbin lvresize";
    public static final String PVDISPLAY = "PATH=/sbin:/usr/sbin pvdisplay";
    public static final String PVCREATE = "PATH=/sbin:/usr/sbin pvcreate";
    public static final String PVREMOVE = "PATH=/sbin:/usr/sbin pvremove";
}
