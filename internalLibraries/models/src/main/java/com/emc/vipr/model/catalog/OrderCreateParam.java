/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "order_create")
public class OrderCreateParam extends OrderCommonParam {

    private URI tenantId;

    @XmlElement(name = "tenantId")
    public URI getTenantId() {
        return tenantId;
    }

    public void setTenantId(URI tenantId) {
        this.tenantId = tenantId;
    }
}
