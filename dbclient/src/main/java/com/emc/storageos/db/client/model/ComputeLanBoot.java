/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("ComputeLanBoot")
public class ComputeLanBoot extends DiscoveredSystemObject {

    private int order;
    private String prot;
    private URI computeBootDef;
    private URI computeBootPolicy;

    @Name("order")
    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
        setChanged("order");
    }

    @Name("prot")
    public String getProt() {
        return prot;
    }

    public void setProt(String prot) {
        this.prot = prot;
        setChanged("prot");
    }

    @RelationIndex(cf = "RelationIndex", type = ComputeBootDef.class)
    @Name("computeBootDef")
    public URI getComputeBootDef() {
        return computeBootDef;
    }

    public void setComputeBootDef(URI computeBootDef) {
        this.computeBootDef = computeBootDef;
        setChanged("computeBootDef");
    }

    @RelationIndex(cf = "RelationIndex", type = ComputeBootPolicy.class)
    @Name("computeBootPolicy")
    public URI getComputeBootPolicy() {
        return computeBootPolicy;
    }

    public void setComputeBootPolicy(URI computeBootPolicy) {
        this.computeBootPolicy = computeBootPolicy;
        setChanged("computeBootPolicy");
    }

}
