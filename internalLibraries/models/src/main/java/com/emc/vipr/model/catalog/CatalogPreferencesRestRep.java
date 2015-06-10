/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.Calendar;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RestLinkRep;

@XmlRootElement(name = "catalog_preferences")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class CatalogPreferencesRestRep extends DataObjectRestRep {

    private String approverEmail;
    private String approvalUrl;    
    
    public CatalogPreferencesRestRep() {

    }

    public CatalogPreferencesRestRep(String name, URI id, RestLinkRep link, Calendar creationTime, Boolean inactive,
            Set<String> tags) {
        super(name, id, link, creationTime, inactive, tags);
    }

    @XmlElement(name = "approver_email")
    public String getApproverEmail() {
        return approverEmail;
    }

    public void setApproverEmail(String approverEmail) {
        this.approverEmail = approverEmail;
    }

    @XmlElement(name = "approval_url")
    public String getApprovalUrl() {
        return approvalUrl;
    }

    public void setApprovalUrl(String approvalUrl) {
        this.approvalUrl = approvalUrl;
    }

}
