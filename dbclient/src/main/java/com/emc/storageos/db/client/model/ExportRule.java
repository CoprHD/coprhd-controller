/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;



/**
 * Base data object for filesystem export rule and snapshot export rule
 */
public abstract class ExportRule extends DataObject {

	protected String fsExportIndex;
	
	protected String snapshotExportIndex;
	
    // Export Path of an export.
    protected String exportPath;

    // Security flavor of the export rule.
    protected String secFlavor;

    //rootUserMapping
    protected String anon;

    //ReadOnly hosts for the export Rule
    protected  StringSet readOnlyHosts;

    //ReadWrite hosts for the export Rule
    protected StringSet readWriteHosts;
    
    //Root hosts for the export Rule
    protected StringSet rootHosts;

	//DeviceExportId is the uid of the export on the array. Currently Isilon uses it
    //NetApp and VNXFile don't use this field. 
    protected String deviceExportId;
   
    protected String mountPoint;
    
	@Name("mountPoint")
	public String getMountPoint() {
		return mountPoint;
	}

	public void setMountPoint(String mountPoint) {
		this.mountPoint = mountPoint;
		setChanged("mountPoint");
	}

  
    /**
     * Get exportPath 
     * @return 
     */
    
    @Name("exportPath")
    @AlternateId("exportPathTable")
    public String getExportPath() {
		return exportPath;
	}
    
    /**
     * Set exportPath in the exportRule
     * @return 
     */
	public void setExportPath(String exportPath) {
		this.exportPath = exportPath;
		setChanged("exportPath");
		calculateExportRuleIndex();
	}

	/**
     * Get SecurityFlavor for the exportRule 
     * @return
     */
	 
	@Name("secFlavor")
	@AlternateId("secFlavorTable")
	public String getSecFlavor() {
		return secFlavor;
	}

	/**
     * Set SecurityFlavor for the exportRule
     * @return 
     */
	public void setSecFlavor(String securityFlavor) {
		this.secFlavor = securityFlavor;
		setChanged("secFlavor");
		calculateExportRuleIndex();
	}

	/**
     * Get AnonUser for the exportRule 
     * @return
     */
	@Name("anon")
	public String getAnon() {
		return anon;
	}

	/**
     * Set AnonUser for the exportRule 
     * @return
     */
	
	public void setAnon(String anonUser) {
		this.anon = anonUser;
		setChanged("anon");
	}

	
	/**
     * Get ReadOnlyHosts for the exportRule 
     * @return
     */
	@Name("readOnlyHosts")
	public StringSet getReadOnlyHosts() {
		return readOnlyHosts;
	}

	/**
     * Set ReadOnlyHosts for the exportRule 
     * @return
     */
	
	public void setReadOnlyHosts(StringSet readOnlyHosts) {
		this.readOnlyHosts = readOnlyHosts;
		setChanged("readOnlyHosts");
	}

	/**
     * Get ReadWriteHosts for the exportRule 
     * @return
     */
	@Name("readWriteHosts")
	public StringSet getReadWriteHosts() {
		return readWriteHosts;
	}

	/**
     * Set ReadWriteHosts for the exportRule 
     * @return
     */
	public void setReadWriteHosts(StringSet readWriteHosts) {
		this.readWriteHosts = readWriteHosts;
		setChanged("readWriteHosts");
	}

	/**
     * Get rootHosts for the exportRule 
     * @return
     */
	@Name("rootHosts")
	public StringSet getRootHosts() {
			return rootHosts;
	}

	/**
     * Set rootHosts for the exportRule 
     * @return
     */
	public void setRootHosts(StringSet rootHosts) {
			this.rootHosts = rootHosts;
			setChanged("rootHosts");
			
	}
	
	/**
     * Get deviceExportId for the exportRule 
     * @return
     */
	@Name("deviceExportId")
	public String getDeviceExportId() {
		return deviceExportId;
	}
	
	/**
     * Set deviceExportId for the exportRule 
     * @return
     */
	public void setDeviceExportId(String deviceExportId) {
		this.deviceExportId = deviceExportId;
		setChanged("deviceExportId");
	}

	/**
     * Get fsExportIndex for the exportRule 
     * @return
     */
	@Name("fsExportIndex")
	@AlternateId("fsExportRuleIndexTable")
	public String getFsExportIndex() {
		return fsExportIndex;
	}

	public void setFsExportIndex(String fsExportIndex) {
		this.fsExportIndex = fsExportIndex;
		setChanged("fsExportIndex");
	}

	/**
     * Get snapshotExportIndex for the exportRule 
     * @return
     */
	@Name("snapshotExportIndex")
	@AlternateId("snapshotExportRuleIndexTable")
	public String getSnapshotExportIndex() {
		return snapshotExportIndex;
	}

	public void setSnapshotExportIndex(String snapshotExportIndex) {
		this.snapshotExportIndex = snapshotExportIndex;
		setChanged("snapshotExportIndex");
	}
	
	@Override
    public String canBeDeleted() {
		return null;
    }
	
	//Calculate index for corresponding filesystem or snapshot or qtree export rule.
	public abstract void calculateExportRuleIndex();
	
	
}
