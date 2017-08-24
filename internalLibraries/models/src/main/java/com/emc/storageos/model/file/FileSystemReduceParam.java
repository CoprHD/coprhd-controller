/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "filesystem_reduce")
public class FileSystemReduceParam {
	
	private String newSize;
	
	public FileSystemReduceParam() {
	}
	
	public FileSystemReduceParam(String newSize) {
		this.newSize = newSize;
	}
	
	/**
     *Defines new reduced quota of a FileSystem
     * Supported size formats: TB, GB, MB, B. Default format is size in bytes.
     * Examples: 100GB, 614400000, 614400000B
     */
	@XmlElement(required = true, name = "new_size")
	public String getNewSize() {
		return newSize;
	}

	public void setNewSize(String newSize) {
		this.newSize = newSize;
	}
}
