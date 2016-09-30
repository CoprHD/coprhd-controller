package com.emc.storageosplugin.model.vce;

public class IscsiTargetInfo {
	private String ipAddress;
	private int tcpPort;
	private String iqnName;
	public String getIpAddress() {
		return ipAddress;
	}
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
	public int getTcpPort() {
		return tcpPort;
	}
	public void setTcpPort(int tcpPort) {
		this.tcpPort = tcpPort;
	}
	public String getIqnName() {
		return iqnName;
	}
	public void setIqnName(String iqnName) {
		this.iqnName = iqnName;
	}
	
	

}
