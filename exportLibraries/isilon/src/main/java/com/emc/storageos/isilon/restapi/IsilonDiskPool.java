/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

public class IsilonDiskPool extends IsilonPool {

	// [{"disk_usage":{"available":15754415955968,"total":16921439059968,"used":94310457344},"entry_id":1,"name":"x200_5.5tb_200gb-ssd_6gb"}]

	private DiskUsage disk_usage;
	private String entry_id;
	private String name;

	public class DiskUsage {
		public Long available;
		public Long total;
		public Long used;

		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append("[ available: " + available);
			str.append(", total: " + total);
			str.append(", used: " + used + "]");
			return str.toString();
		}

	};

	public DiskUsage getDiskUsage() {
		return disk_usage;
	}

	public String getEntry_id() {
		return entry_id;
	}

	public String getName() {
		return name;
	}

	public Long getAvailableBytes() {
		return getDiskUsage().available;
	}

	public Long getUsedBytes() {
		return getDiskUsage().used;
	}

	public Long getTotalBytes() {
		return getDiskUsage().total;
	}

	@Override
	public Long getFreeBytes() {
		return (getTotalBytes() - getUsedBytes());
	}

	public String getNativeId() {
		return getName();
	}

	@Override
	public String toString() {
		return "IsilonDiskPool [disk_usage=" + disk_usage + ", entry_id="
				+ entry_id + ", name=" + name + "]";
	}

}
