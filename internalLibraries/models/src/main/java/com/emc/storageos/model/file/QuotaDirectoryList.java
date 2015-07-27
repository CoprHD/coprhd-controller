/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model.file;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "quota_dir_list")
public class QuotaDirectoryList {

    private List<NamedRelatedResourceRep> quotaDirs;

    public QuotaDirectoryList() {}
    
    public QuotaDirectoryList(List<NamedRelatedResourceRep> quotaDirs) {
        this.quotaDirs = quotaDirs;
    }

    /**
     * The list of virtual pool response instances.
     * 
     * @valid none
     */
    @XmlElement(name = "quota_dir")
    @JsonProperty("quota_dir")
    public List<NamedRelatedResourceRep> getQuotaDirs() {
        if (quotaDirs == null) {
        	quotaDirs = new ArrayList<NamedRelatedResourceRep>();
        }
        return quotaDirs;
    }

    public void setQuotaDirs(List<NamedRelatedResourceRep> quotaDirs) {
        this.quotaDirs = quotaDirs;
    }

    /**
     * Determines whether or not the list contains a quota resource with the
     * passed id.
     * 
     * @param quotaid The quota id to check.
     * 
     * @return true if the list contains a quota resource with the passed id,
     *         false otherwise.
     */
    public boolean containsQuotaDirResource(String quotaId) {
        for (RelatedResourceRep quotaDirRes : getQuotaDirs()) {
            URI quotaDirId = quotaDirRes.getId();
            if ((quotaDirId != null)
                && (quotaDirId.toString().equals(quotaId))) {
                return true;
            }
        }
        return false;
    }
}
