/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import java.io.Serializable;
import com.iwave.ext.netapp.model.ShareState;

/**
 * @author sdorcas
 * Models a lun-info object.
 */
public class LunInfo implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private String path = "";
	private boolean online = false;
	private boolean mapped = false;
	private boolean spaceReserved = false;
	private int id = -1;
	private long size = -1;
	private long sizeUsed = -1;
    private ShareState shareState;
    private LunOSType lunType;
	
	/**
	 * 
	 */
	public LunInfo() {
		// TODO Auto-generated constructor stub
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean isOnline() {
		return online;
	}

	public void setOnline(boolean online) {
		this.online = online;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public long getSizeUsed() {
		return sizeUsed;
	}

	public void setSizeUsed(long sizeUsed) {
		this.sizeUsed = sizeUsed;
	}

    public boolean isMapped() {
        return mapped;
    }

    public void setMapped(boolean mapped) {
        this.mapped = mapped;
    }

    public boolean isSpaceReserved() {
        return spaceReserved;
    }

    public void setSpaceReserved(boolean spaceReserved) {
        this.spaceReserved = spaceReserved;
    }

    public ShareState getShareState() {
        return shareState;
    }

    public void setShareState(ShareState shareState) {
        this.shareState = shareState;
    }

    public LunOSType getLunType() {
        return lunType;
    }

    public void setLunType(LunOSType lunType) {
        this.lunType = lunType;
    }

}
