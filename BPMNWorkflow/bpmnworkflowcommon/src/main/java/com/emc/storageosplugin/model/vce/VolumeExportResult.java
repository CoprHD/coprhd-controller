package com.emc.storageosplugin.model.vce;

import java.util.List;

public class VolumeExportResult extends OperationResult {
	
	private String exportGroupID;
	private String exportGroupName;
	private String exportGroupGeneratedName;	
	private String lunID;
	private String hostName;
        private String wwn;
	private boolean isIscsi = false;
	private IscsiTargetInfo[] targets;
	
	public boolean getIsIscsi() {
		return isIscsi;
	}

	public void setIsIscsi(boolean isIscsi) {
		this.isIscsi = isIscsi;
	}

	public IscsiTargetInfo[] getTargets() {
		return targets;
	}

	public void setTargets(List<IscsiTargetInfo> targets) {
		this.targets =  targets.toArray(new IscsiTargetInfo[0]);
	}
	
	public void setTargets(IscsiTargetInfo[] targets) {
		this.targets =  targets;
	}

	public String getLunID() {
		return lunID;
	}

	public void setLunID(String lunID) {
		this.lunID = lunID;
	}

        public String getWwn() {
		return wwn;
	}

	public void setWwn(String wwn) {
		this.wwn = wwn;
	}

	public String getExportGroupName() {
		return exportGroupName;
	}

	public void setExportGroupName(String exportGroupName) {
		this.exportGroupName = exportGroupName;
	}
	
	public String getExportGroupGeneratedName() {
		return exportGroupGeneratedName;
	}

	public void setExportGroupGeneratedName(String exportGroupGeneratedName) {
		this.exportGroupGeneratedName = exportGroupGeneratedName;
	}

	public String getExportGroupID() {
		return exportGroupID;
	}

	public void setExportGroupID(String exportGroupID) {
		this.exportGroupID = exportGroupID;
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

}
