/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("ComputeSanBoot")
public class ComputeSanBoot extends DiscoveredSystemObject {

    private Integer order;
    private URI computeBootDef;
    private URI computeBootPolicy;
    private Boolean isFirstBootDevice;

    @Name("isFirstBootDevice")
    public Boolean getIsFirstBootDevice() {
        return isFirstBootDevice;
    }

    public void setIsFirstBootDevice(Boolean isFirstBootDevice) {
        this.isFirstBootDevice = isFirstBootDevice;
        setChanged("isFirstBootDevice");
    }

    @Name("order")
    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
        setChanged("order");
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
