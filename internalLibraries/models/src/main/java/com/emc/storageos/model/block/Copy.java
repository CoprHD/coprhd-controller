/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.io.Serializable;
import java.net.URI;

/**
 * Specifies the copy to be operated on
 * 
 * 	type: type of protection (rp, native, srdf)
 * 	sync: synchronize the mirror
 * 	copyID: the URI of the copy to be operated on, if none specified operate on all copies for that type
 * 	name: name of a new mirror being created by start operation
 * 	count: number of mirrors to create using start operation
 */
@XmlRootElement(name = "copy")
public class Copy implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8250892549720042299L;
	private String type;
	private String sync;
	private URI copyID;
	private String name;
    private Integer count;
    private String syncDirection;
    private String copyMode;

    public enum SyncDirection {
        SOURCE_TO_TARGET,
        TARGET_TO_SOURCE
    }

    public Copy() {}
            
    public Copy(String type, String sync, URI copyID, String name, Integer count) {
    	this.type = type;
    	this.sync = sync;
    	this.copyID = copyID;
    	this.name = name;
        this.count = count;
    }

    /**
     * @valid true
     * @valid false
     */
    @XmlElement(name = "sync", required = false, defaultValue = "false")
    public String getSync() {
        return sync;
    }

    public void setSync(String sync) {
        this.sync = sync;
    }

    /**
     * Type of protection.
     * @valid none
     */
    @XmlElement(name = "type", required = true)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

	/**
	 * @return the copyID
	 */
    @XmlElement(name = "copyID", required = false)
    public URI getCopyID() {
		return copyID;
	}

	public void setCopyID(URI copyID) {
		this.copyID = copyID;
	}
	
    /** 
     * User provided name. 
     * @valid none
     */
    @XmlElement(name = "name", required = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * User provided number of copies.
     * @valid none
     */
    @XmlElement(name = "count", required = false)
    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * User provided direction for the synchronization.
     * @valid SOURCE_TO_TARGET
     * @valid TARGET_TO_SOURCE
     * @return The Sync Direction
     */
    @XmlElement(name = "syncDirection", required = false)
    public String getSyncDirection() {
        return syncDirection;
    }
    
    public void setSyncDirection(String syncDirection) {
        this.syncDirection = syncDirection;
    }
    
    /**
     * User provided SRDF copy mode for the synchronization.
     * @valid SYNCHRONOUS - Change SRDF copy mode to SYNCHRONOUS
     * @valid ASYNCHRONOUS - Change SRDF copy mode to ASYNCHRONOUS
     * @valid ADAPTIVECOPY - Change SRDF copy mode to ADAPTIVE
     * @return
     */
    @XmlElement(name = "copyMode", required = false)
	public String getCopyMode() {
		return copyMode;
	}

	public void setCopyMode(String copyMode) {
		this.copyMode = copyMode;
	}
    
}
