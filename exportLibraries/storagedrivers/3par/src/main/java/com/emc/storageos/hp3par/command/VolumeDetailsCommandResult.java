package com.emc.storageos.hp3par.command;

public class VolumeDetailsCommandResult {
    private Long sizeMiB;
    private String wwn;
    private String uuid;
    private String copyOf;
    private String name;
    private int provisioningType;
    private int copyType;
	private String userCPG;
    private String snapCPG;
    
    
    public String getUserCPG() {
		return userCPG;
	}
	public void setUserCPG(String userCPG) {
		this.userCPG = userCPG;
	}
	public String getSnapCPG() {
		return snapCPG;
	}
	public void setSnapCPG(String snapCPG) {
		this.snapCPG = snapCPG;
	}
    
    /*
     * copyType :
     *  BASE 1 Base volume (not a copy).
	 *  PHYSICAL_COPY 2 Physical copy (full copy).
	 *  VIRTUAL_COPY 3 Snapshot copy (virtual copy).
     */
    public void  setCopyType(int copyType) {
    	this.copyType = copyType;
    }
    public int getCopyType() {
    	return copyType;
    }
    
    /*
     * provisioningType :
     * FULL  1 Fully-provisioned virtual volume (FPVV) or commonly-provisioned virtual volume (CPVV)
	 * TPVV  2 Thin-provisioned virtual volume (TPVV), or TPSD (old-style thinly provisioned virtual volume)
	 * SNP   3 Snapshot (Type vcopy)
	 * PEER  4 Peer volume
	 * TDVV  6 De-duplicated volume
     */
    public int getProvisioningType() {
		return provisioningType;
	}
	public void setProvisioningType(int provisioningType) {
		this.provisioningType = provisioningType;
	}
	public String getName() {
    	return name;
    }
    public void setName(String name) {
    	this.name = name;
    }
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
