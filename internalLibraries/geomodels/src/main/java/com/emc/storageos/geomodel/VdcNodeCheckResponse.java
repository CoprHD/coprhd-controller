/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.geomodel;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author cgarber
 *
 */
@XmlRootElement
public class VdcNodeCheckResponse {
    
    private URI id;
    private String shortId;
    private boolean nodesReachable;
    
    @XmlElement(name="id")    
    public URI getId() {
        return id;
    }
    public void setId(URI id) {
        this.id = id;
    }
    
    @XmlElement(name="short_id")
    public String getShortId() {
        return shortId;
    }
    public void setShortId(String shortId) {
        this.shortId = shortId;
    }
    
    /**
     * @return the nodesReachable
     */
    @XmlElement(name="isNodesReachable")
    public boolean isNodesReachable() {
        return nodesReachable;
    }
    /**
     * @param nodesReachable the nodesReachable to set
     */
    public void setNodesReachable(boolean nodesReachable) {
        this.nodesReachable = nodesReachable;
    }

}
