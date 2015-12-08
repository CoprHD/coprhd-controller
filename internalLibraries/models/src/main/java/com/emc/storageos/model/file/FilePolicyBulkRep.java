/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

/**
 * List of File Policies and returned as a bulk response to a REST request.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "bulk_file_policies")
public class FilePolicyBulkRep extends BulkRestRep {
    private List<FilePolicyRestRep> filePolicies;

    public FilePolicyBulkRep() {
    }

    public FilePolicyBulkRep(List<FilePolicyRestRep> filePolicies) {
        super();
        this.filePolicies = filePolicies;
    }

    /**
     * List of File Policies. A file policy represents a
     * file schedule policy.
     * 
     * @valid none
     */
    @XmlElement(name = "file_policy")
    public List<FilePolicyRestRep> getFilePolicies() {
        if (filePolicies == null) {
            filePolicies = new ArrayList<FilePolicyRestRep>();
        }
        return filePolicies;
    }

    /**
     * @param filePolicies the filePolicies to set
     */
    public void setFilePolicies(List<FilePolicyRestRep> filePolicies) {
        this.filePolicies = filePolicies;
    }
}
