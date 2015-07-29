/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_quotadirectories")
public class QuotaDirectoryBulkRep extends BulkRestRep {
    private List<QuotaDirectoryRestRep> quotaDirectories;

    /**
     * The list of Quota Directories, returned as response to bulk
     * queries.
     * 
     * @valid none
     */
    @XmlElement(name = "quotadirectory")
    public List<QuotaDirectoryRestRep> getQuotaDirectories() {
        if (quotaDirectories == null) {
            quotaDirectories = new ArrayList<QuotaDirectoryRestRep>();
        }
        return quotaDirectories;
    }

    public void setQuotaDirectories(List<QuotaDirectoryRestRep> quotaDirectories) {
        this.quotaDirectories = quotaDirectories;
    }

    public QuotaDirectoryBulkRep() {
    }

    public QuotaDirectoryBulkRep(List<QuotaDirectoryRestRep> quotaDirectories) {
        this.quotaDirectories = quotaDirectories;
    }

}
