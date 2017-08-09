/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi.internalbean;

import java.util.ArrayList;
import java.util.List;

/**
 * @author fengs5
 *
 */
public class Host {

    String name;
    String id;
    List<String> existInitiators = new ArrayList<>();
    List<String> newInitiators = new ArrayList<>();

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the existInitiators
     */
    public List<String> getExistInitiators() {
        return existInitiators;
    }

    public void addNewInitiator(String initiator) {
        newInitiators.add(initiator);
    }

    /**
     * @param existInitiators the existInitiators to set
     */
    public void setExistInitiators(List<String> existInitiators) {
        this.existInitiators = existInitiators;
    }

    /**
     * @return the newInitiators
     */
    public List<String> getNewInitiators() {
        return newInitiators;
    }

    /**
     * @param newInitiators the newInitiators to set
     */
    public void setNewInitiators(List<String> newInitiators) {
        this.newInitiators = newInitiators;
    }

    /**
     * @param name
     */
    public Host(String name) {
        super();
        this.name = name;
    }

}
