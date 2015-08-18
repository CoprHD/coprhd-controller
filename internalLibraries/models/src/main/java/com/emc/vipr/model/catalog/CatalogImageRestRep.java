/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.Arrays;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "catalog_image")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class CatalogImageRestRep extends DataObjectRestRep {

    private RelatedResourceRep tenant;
    private String contentType;
    private byte[] data;

    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    @XmlElement(name = "content_type")
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @XmlElement(name = "data")
    public byte[] getData() {
    	return data.clone();
    }

    public void setData(byte[] data) {
    	if(data == null){
    		this.data = new byte[0];
    	}else{
    		this.data = Arrays.copyOf(data, data.length);
    	}
    }

}
