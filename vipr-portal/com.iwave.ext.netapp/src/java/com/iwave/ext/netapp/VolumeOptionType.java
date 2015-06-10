/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

/**
 * @author sdorcas
 * Enumeration containing all the volume options for the ONTAP command
 * "volume-set-option".
 */
public enum VolumeOptionType {
	convert_ucode, snapshot_clone_dependency, create_ucode, extent, fractional_reserve,
	fs_sized_fixed, guarantee, ignore_inconsistent, maxdirsize, minra, no_atime_update,
	no_i2p, nosnap, nosnapdir, nvfail, read_realloc
}
