/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "compute_image_update")
public class ComputeImageUpdate {

    private String name;
    private String imageUrl;

    public ComputeImageUpdate() {
    }

    @Length(min = 2, max = 128)
    @XmlElement(required = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "image_url")
    @JsonProperty("image_url")
    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
