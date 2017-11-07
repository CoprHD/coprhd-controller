/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.sa.service;

public class ArtificialFailures {
	public static final String ARTIFICIAL_FAILURE_LINUX_MOUNT_VOLUME = "mountLinuxVolume";
	public static final String ARTIFICIAL_FAILURE_AIX_MOUNT_VOLUME = "mountAIXVolume";
	public static final String ARTIFICIAL_FAILURE_HPUX_MOUNT_VOLUME = "mountHPUXVolume";
	public static final String ARTIFICIAL_FAILURE_WINDOWS_MOUNT_VOLUME = "mountWindowsVolume";
	public static final String ARTIFICIAL_FAILURE_WINDOWS_BEFORE_EXTEND_DRIVE = "windows_before_extendDrives";
	public static final String ARTIFICIAL_FAILURE_WINDOWS_AFTER_EXTEND_DRIVE = "windows_after_extendDrives";
	public static final String ARTIFICIAL_FAILURE_HPUX_EXPAND_VOLUME_AFTER_UNMOUNT = "hpux_expandVolume_after_unmount";
	public static final String ARTIFICIAL_FAILURE_HPUX_EXPAND_VOLUME_AFTER_REMOVE_TAG = "hpux_expandVolume_after_remove_tag";
	public static final String ARTIFICIAL_FAILURE_HPUX_EXPAND_VOLUME_AFTER_VOLUME_RESIZE = "hpux_expandVolume_after_volume_resize";
	public static final String ARTIFICIAL_FAILURE_HPUX_EXPAND_VOLUME_AFTER_MOUNT = "hpux_expandVolume_after_mount";
	public static final String ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_UNMOUNT = "linux_expandVolume_after_unmount";
	public static final String ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_REMOVE_TAG = "linux_expandVolume_after_remove_tag";
	public static final String ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_VOLUME_RESIZE = "linux_expandVolume_after_volume_resize";
	public static final String ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_MOUNT = "linux_expandVolume_after_mount";
	public static final String ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_RESIZE_PARTITION = "linux_expandVolume_after_resize_partition";
	public static final String ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_RESIZE_FILESYSTEM = "linux_expandVolume_after_resize_filesystem";
	public static final String ARTIFICIAL_FAILURE_WINDOWS_EXTEND_DRIVE = "extendDrivesWindows";
	public static final String ARTIFICIAL_FAILURE_VMWARE_EXTEND_DATASTORE = "extend_vmfs_datastore";
	public static final String ARTIFICIAL_FAILURE_VMWARE_EXPAND_DATASTORE = "expand_vmfs_datastore";
}
