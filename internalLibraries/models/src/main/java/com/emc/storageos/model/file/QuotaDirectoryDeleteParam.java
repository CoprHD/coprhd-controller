/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Whether or not the file system quota directory
 * should be forcibly deleted. If set to yes, it
 * will be deleted, even if there are valid underlying
 * objects, such as exports, etc.
 * 
 * @valid true
 * @valid false
 */
@XmlRootElement(name = "quota_directory_deactivate")
public class QuotaDirectoryDeleteParam {

    private boolean forceDelete;

    public QuotaDirectoryDeleteParam() {
    }

    public QuotaDirectoryDeleteParam(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

    @XmlElement(name = "forceDelete")
    public boolean getForceDelete() {
        return forceDelete;
    }

    public void setForceDelete(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }
}
