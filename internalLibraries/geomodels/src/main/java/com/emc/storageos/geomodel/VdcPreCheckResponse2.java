/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
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

@XmlRootElement
public class VdcPreCheckResponse2 {
    private URI id;
    private boolean compatible = false;
    private boolean clusterStable;
    private boolean isAllNodesNotReachable = false;

    @XmlElement(name = "id")
    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    @XmlElement(name = "compatible")
    public boolean getCompatible() {
        return compatible;
    }

    public void setCompatible(boolean compatible) {
        this.compatible = compatible;
    }

    @XmlElement(name = "clusterStable", required = true)
    public boolean isClusterStable() {
        return clusterStable;
    }

    public void setClusterStable(boolean clusterStable) {
        this.clusterStable = clusterStable;
    }

    @XmlElement(name = "isAllNodesNotReachable")
    public boolean getIsAllNodesNotReachable() {
        return isAllNodesNotReachable;
    }

    public void setIsAllNodesNotReachable(boolean isAllNodesNotReachable) {
        this.isAllNodesNotReachable = isAllNodesNotReachable;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(VdcPreCheckResponse2.class.toString());
        builder.append(":\n");

        builder.append("id: ");
        builder.append(id);
        builder.append("\n");

        builder.append("compatible: ");
        builder.append(compatible);
        builder.append("\n");

        builder.append("clusterStable: ");
        builder.append(clusterStable);
        builder.append("\n");

        builder.append("isAllNodesNotReachable: ");
        builder.append(isAllNodesNotReachable);
        builder.append("\n");

        return builder.toString();
    }
}
