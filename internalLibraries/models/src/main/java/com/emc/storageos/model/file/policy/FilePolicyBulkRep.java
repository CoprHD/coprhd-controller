/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

/**
 * List of file policies and returned as a bulk response to a REST request.
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
     * List of file policies.
     * 
     */
    @XmlElement(name = "file_policies")
    public List<FilePolicyRestRep> getFilePolicies() {
        if (filePolicies == null) {
            filePolicies = new ArrayList<FilePolicyRestRep>();
        }
        return filePolicies;
    }

    /**
     * @param filePolicies the file policies to set
     */
    public void setFilePolicies(List<FilePolicyRestRep> filePolicies) {
        this.filePolicies = filePolicies;
    }

}
