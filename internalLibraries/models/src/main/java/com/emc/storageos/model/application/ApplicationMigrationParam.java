/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * create migration parameters
 */
@XmlRootElement(name = "application_migration")
public class ApplicationMigrationParam {

    // used in create migration
    private URI targetVirtualArray;
    private URI targetVirtualPool;
    
    // used in commit and cancel migration
    private boolean removeEnvironment;

    public ApplicationMigrationParam() {
    }

    public ApplicationMigrationParam(URI tgtVarray, URI tgtVpool) {
        this.targetVirtualArray = tgtVarray;
        this.targetVirtualPool = tgtVpool;
    }

    /**
     * The virtual 
     * @return the targetVirtualArray
     */
    @XmlElement(name = "target-virtual-array")
    public URI getTargetVirtualArray() {
        return targetVirtualArray;
    }

    /**
     * @param targetVirtualArray the targetVirtualArray to set
     */
    public void setTargetVirtualArray(URI targetVirtualArray) {
        this.targetVirtualArray = targetVirtualArray;
    }

    /**
     * @return the targetVirtualPool
     */
    @XmlElement(name = "target-virtual-pool")
    public URI getTargetVirtualPool() {
        return targetVirtualPool;
    }

    /**
     * @param targetVirtualPool the targetVirtualPool to set
     */
    public void setTargetVirtualPool(URI targetVirtualPool) {
        this.targetVirtualPool = targetVirtualPool;
    }

    /**
     * @return the removeEnvironment
     */
    @XmlElement(name = "remove-environment")
    public boolean getRemoveEnvironment() {
        return removeEnvironment;
    }

    /**
     * @param removeEnvironment the removeEnvironment to set
     */
    public void setRemoveEnvironment(boolean removeEnvironment) {
        this.removeEnvironment = removeEnvironment;
    }

}
