/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.file;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;



/**
 * Attributes associated with file share, specified during the
 * file share update.
 *
 */
public class FileSystemShareUpdateBase implements Serializable{
    
	private static final long serialVersionUID = 8769186320717628999L;
	private String shareName;
    private String description;
    
    public FileSystemShareUpdateBase() {}
    
    public FileSystemShareUpdateBase(String shareName, String description) {
        this.shareName = shareName;
        this.description = description;
        
    }

    /**
     * User provided new name of the file share.
     * @valid none
     */
    @XmlElement(name = "name")
    public String getShareName() {
        return shareName;
    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    /**
     * User provided new description of the file share.
     * @valid none
     */
    @XmlElement(name = "description", required = true)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

   }
