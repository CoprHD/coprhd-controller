/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


/**
 * Attributes associated with a file system quota directory, 
 * specified during it's creation.
 *
 */
@XmlRootElement(name = "quota_directory_create")
public class QuotaDirectoryCreateParam {
	
    private String name;
    private Boolean oplock;
    private String size; // Quota size - hard limit.
    
    
    // UNIX, NTFS, Mixed
    private String securityStyle = "unix"; 
    
    public QuotaDirectoryCreateParam() {}
    
    public QuotaDirectoryCreateParam(String name) {
        this.name = name;
    }
    
    public QuotaDirectoryCreateParam(String name, String securityStyle) {
        this.name = name;
        this.securityStyle = securityStyle; 
    }
    
    public QuotaDirectoryCreateParam(String name, boolean oplock, String size, String securityStyle) {
        this.name = name;
        this.oplock = oplock;
        this.size = size;
        this.securityStyle = securityStyle; 
    }
    
    /**
     * User provided name of the quota directory.
     * @valid none
     */
    @XmlElement(name = "name", required = true)
    public String getQuotaDirName() {
        return name;
    }

    public void setQuotaDirName(String name) {
        this.name = name;
    }

    /**
     * Limit total space usage within this quota directory (in Bytes)
     * @valid none
     */
    @XmlElement(name = "size")
    public String getSize() {
       return size;
    }
   
    public void setSize(String size) {
    	this.size = size;
    }
   
    /**
     * Flag to specify Read/Write cache enable for this quota directory.
     * @valid true
     * @valid false
     */
    @XmlElement(name = "oplock")
    public Boolean getOpLock() {
    	return oplock;
    }
    
    public void setOpLock(Boolean oplock) {
       this.oplock = oplock;
    }

    /**
     * Security style for the quota directory.  
     * Default is "UNIX".
     * @valid "UNIX" = Security style by default
     * @valid "NTFS"
     * @valid "Mixed"
     */
    @XmlElement(name = "security_style")
    public String getSecurityStyle() {
        return securityStyle;
    }

    public void setSecurityStyle(String securityStyle) {
        this.securityStyle = securityStyle;
    }	

}


