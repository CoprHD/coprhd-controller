/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_id_list")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteIdListParam {
    private List<String> siteIdList;

    public SiteIdListParam() {
        siteIdList = new ArrayList<>();
    }
    @XmlElement(name = "id")
    public List<String> getIds() {
        return siteIdList;
    }

    public void setIds(List<String> siteIdList) {
        this.siteIdList = siteIdList;
    }
}
