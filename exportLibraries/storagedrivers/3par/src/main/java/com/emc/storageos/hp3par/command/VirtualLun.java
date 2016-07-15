/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class VirtualLun {
	private String id;
	private String volumeName;
	private String hostname;
	private String remoteName;
	private Integer lun;
	private Integer type;
	private String volumeWWN;
	private Integer multipathing;
	private String hostDeviceName;
	private boolean active;	
	private Position portPos;

	public Position getPortPos() {
		return portPos;
	}
	public void setPortPos(Position portPos) {
		this.portPos = portPos;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getVolumeName() {
		return volumeName;
	}
	public void setVolumeName(String volumeName) {
		this.volumeName = volumeName;
	}
	public String getHostname() {
		return hostname;
	}
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	public String getRemoteName() {
		return remoteName;
	}
	public void setRemoteName(String remoteName) {
		this.remoteName = remoteName;
	}
	public Integer getLun() {
		return lun;
	}
	public void setLun(Integer lun) {
		this.lun = lun;
	}
	public Integer getType() {
		return type;
	}
	public void setType(Integer type) {
		this.type = type;
	}
	public String getVolumeWWN() {
		return volumeWWN;
	}
	public void setVolumeWWN(String volumeWWN) {
		this.volumeWWN = volumeWWN;
	}
	public Integer getMultipathing() {
		return multipathing;
	}
	public void setMultipathing(Integer multipathing) {
		this.multipathing = multipathing;
	}
	public String getHostDeviceName() {
		return hostDeviceName;
	}
	public void setHostDeviceName(String hostDeviceName) {
		this.hostDeviceName = hostDeviceName;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}		
	
	@Override
	public String toString() {
	    return String.format("Hostname:" + getHostname() +"  PortPos:"+ getPortPos()
	    					+" RemoteName:"+ getRemoteName() + " VolumeWWN"+ getVolumeWWN()
	    					+" VolumeName:"+ getVolumeName() + " Type:"+getType());
	}
}
