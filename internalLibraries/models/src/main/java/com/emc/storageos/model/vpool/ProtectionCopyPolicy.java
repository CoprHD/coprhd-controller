/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

public class ProtectionCopyPolicy {
   
    private String journalSize;
    private URI journalVarray;
    private URI journalVpool;

    public ProtectionCopyPolicy() {}
    
    public ProtectionCopyPolicy(String journalSize) {
        this.journalSize = journalSize;
    }
    
    public ProtectionCopyPolicy(String journalSize, URI journalVarray, URI journalVpool) {
        this.journalSize = journalSize;
        this.journalVarray = journalVarray;
        this.journalVpool = journalVpool;
    }

    /**
     * The journal size for a protection copy.
     * 
     * @valid none
     */
    @XmlElement(name = "journal_size", required = false)
    public String getJournalSize() {
        return journalSize;
    }

    public void setJournalSize(String journalSize) {
        this.journalSize = journalSize;
    }
    
    /**
     * The journal virtual array for a protection copy.
     * 
     * @valid none
     */    
    @XmlElement(name = "journal_varray", required =false)
	public URI getJournalVarray() {
		return journalVarray;
	}

	public void setJournalVarray(URI journalVarray) {
		this.journalVarray = journalVarray;
	}
	   
    /**
     * The journal virtual pool for a protection copy.
     * 
     * @valid none
     */    
	@XmlElement(name = "journal_vpool", required = false)
	public URI getJournalVpool() {
		return journalVpool;
	}

	public void setJournalVpool(URI journalVpool) {
		this.journalVpool = journalVpool;
	}

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((journalSize == null) ? 0 : journalSize.hashCode());
		result = prime * result
				+ ((journalVarray == null) ? 0 : journalVarray.hashCode());
		result = prime * result
				+ ((journalVpool == null) ? 0 : journalVpool.hashCode());
		return result;
	}

    @Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ProtectionCopyPolicy other = (ProtectionCopyPolicy) obj;
		if (journalSize == null) {
			if (other.journalSize != null)
				return false;
		} else if (!journalSize.equals(other.journalSize))
			return false;
		if (journalVarray == null) {
			if (other.journalVarray != null)
				return false;
		} else if (!journalVarray.equals(other.journalVarray))
			return false;
		if (journalVpool == null) {
			if (other.journalVpool != null)
				return false;
		} else if (!journalVpool.equals(other.journalVpool))
			return false;
		return true;
	}
}
