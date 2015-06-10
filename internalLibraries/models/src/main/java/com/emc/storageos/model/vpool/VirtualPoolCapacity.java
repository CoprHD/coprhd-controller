/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.net.URI;

public class VirtualPoolCapacity {
    
    private URI id;
    private CapacityResponse capacity;
    
    public VirtualPoolCapacity() {}
    
    public VirtualPoolCapacity(URI id) {
        this.id = id;
    }
    
    public VirtualPoolCapacity(URI id, CapacityResponse capacity) {
        super();
        this.id = id;
        this.capacity = capacity;
    }

    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    public CapacityResponse getCapacity() {
        return capacity;
    }

    public void setCapacity(CapacityResponse capacity) {
        this.capacity = capacity;
    }
    
}
