/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
