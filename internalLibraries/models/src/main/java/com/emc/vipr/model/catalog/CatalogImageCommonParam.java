/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.Arrays;

import javax.xml.bind.annotation.XmlElement;

public class CatalogImageCommonParam {

    private String name;
    private String contentType;
    private byte[] data;

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

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
