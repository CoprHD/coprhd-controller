/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "compute_imageservers")
public class ComputeImageServerList {

    private List<NamedRelatedResourceRep> computeImageServers;

    public ComputeImageServerList() {
    }

    public ComputeImageServerList(
            List<NamedRelatedResourceRep> computeImageServers) {
        this.computeImageServers = computeImageServers;
    }

    /**
     * List of compute image server URLs with name
     *
     * @valid none
     */
    @XmlElement(name = "compute_imageserver")
    @JsonProperty("compute_imageserver")
    public List<NamedRelatedResourceRep> getComputeImageServers() {
        if (computeImageServers == null) {
            computeImageServers = new ArrayList<NamedRelatedResourceRep>();
        }
        return computeImageServers;
    }

    public void setComputeImageServers(
            List<NamedRelatedResourceRep> computeImageServers) {
        this.computeImageServers = computeImageServers;
    }
}
