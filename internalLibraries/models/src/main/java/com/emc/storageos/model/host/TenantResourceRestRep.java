/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.net.URI;
import java.util.Calendar;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;

/**
 * Common class to all tenant resource types
 * 
 * @author elalih
 */
public abstract class TenantResourceRestRep extends DataObjectRestRep {
    private RelatedResourceRep tenant;

    public TenantResourceRestRep() {
    }

    public TenantResourceRestRep(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    public TenantResourceRestRep(String name, URI id, RestLinkRep link,
            Calendar creationTime, Boolean inactive, Set<String> tags,
            RelatedResourceRep tenant) {
        super(name, id, link, creationTime, inactive, tags);
        this.tenant = tenant;
    }

    /**
     * The tenant associated with the host.
     * 
     * @return the tenant associated with the host.
     */
    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }
}
