/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

public class ProtectionSourcePolicy {
    
    private String journalSize;
    private URI journalVarray;
    private URI journalVpool;
    private URI standbyJournalVarray;
    private URI standbyJournalVpool;
    private String remoteCopyMode;
    private Long rpoValue;
    private String rpoType;
    
    public ProtectionSourcePolicy() {}
    
    public ProtectionSourcePolicy(String journalSize, String remoteCopyMode, Long rpoValue, String rpoType) {
        this.journalSize = journalSize;
        this.remoteCopyMode = remoteCopyMode;
        this.rpoValue = rpoValue;
        this.rpoType = rpoType;
    }

    /**
     * The journal size for a protection source.
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
     * The journal virtual array for a protection source/active source.
     * 
     * @valid none
     */
    @XmlElement(name = "journal_varray", required = false)
    public URI getJournalVarray() {
		return journalVarray;
	}

	public void setJournalVarray(URI journalVarray) {
		this.journalVarray = journalVarray;
	}

	  /**
     * The journal virtual pool for a protection source/active source.
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

	  /**
     * The journal virtual array for stand-by source.
     * 
     * @valid none
     */
	@XmlElement(name = "standby_journal_varray", required = false)
	public URI getStandbyJournalVarray() {
		return standbyJournalVarray;
	}

	public void setStandbyJournalVarray(URI standbyJournalVarray) {
		this.standbyJournalVarray = standbyJournalVarray;
	}

	  /**
     * The journal virtual pool for stand-by source.
     * 
     * @valid none
     */
	@XmlElement(name = "standby_journal_vpool", required = false)
	public URI getStandbyJournalVpool() {
		return standbyJournalVpool;
	}

	public void setStandbyJournalVpool(URI standbyJournalVpool) {
		this.standbyJournalVpool = standbyJournalVpool;
	}

    /**
     * The remote copy mode, sync or async
     * 
     * @valid ASYNCHRONOUS = RecoverPoint CG will be in Asynchronous mode (default)
     * @valid SYNCHRONOUS = RecoverPoint CG will be in Synchronous mode
     */
    @XmlElement(name="remote_copy_mode", required = false)
    public String getRemoteCopyMode() {
        return remoteCopyMode;
    }

    public void setRemoteCopyMode(String remoteCopyMode) {
        this.remoteCopyMode = remoteCopyMode;
    }

	/**
	 * RPO value sent to RP
	 * 
	 * @return RPO value
	 */
    @XmlElement(name="rpo_value", required = false)
	public Long getRpoValue() {
		return rpoValue;
	}

	public void setRpoValue(Long rpoValue) {
		this.rpoValue = rpoValue;
	}

	/**
	 * Type of RPO unit
	 * 
	 * @valid SECONDS = Seconds (time-based RPO)
     * @valid MINUTES = Minutes (time-based RPO)
     * @valid HOURS = Hours (time-based RPO)
     * @valid WRITES = Number of writes (transaction-based RPO)
     * @valid BYTES = Bytes (sized-based RPO)
     * @valid KB = Kilobytes (sized-based RPO)
     * @valid MB = Megabytes (sized-based RPO)
     * @valid GB = Gigabytes (sized-based RPO)
     * @valid TB = Terabytes (sized-based RPO)
	 */
    @XmlElement(name="rpo_type", required = false)
	public String getRpoType() {
		return rpoType;
	}

	public void setRpoType(String rpoType) {
		this.rpoType = rpoType;
	}
}
