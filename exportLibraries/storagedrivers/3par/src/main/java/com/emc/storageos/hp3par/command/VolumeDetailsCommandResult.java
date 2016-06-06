package com.emc.storageos.hp3par.command;

public class VolumeDetailsCommandResult {
    private Long sizeMiB;
    private String wwn;
    private String uuid;
    private String copyOf;
    
    
    public String getCopyOf() {
		return copyOf;
	}
	public void setCopyOf(String copyOf) {
		this.copyOf = copyOf;
	}
	public Long getSizeMiB() {
        return sizeMiB;
    }
    public void setSizeMiB(Long sizeMiB) {
        this.sizeMiB = sizeMiB;
    }
    public String getWwn() {
        return wwn;
    }
    public void setWwn(String wwn) {
        this.wwn = wwn;
    }
    public String getUuid() {
        return uuid;
    }
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
