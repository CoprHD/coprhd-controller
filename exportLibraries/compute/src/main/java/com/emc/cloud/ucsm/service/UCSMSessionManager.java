/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

public class UCSMSessionManager implements ComputeSessionManager {
    @Autowired
    ApplicationContext applicationContext;

    CoordinatorClientImpl coordinator;

    private String prototypeSessionName;

    public UCSMSessionManager() {
    }

    public ComputeSession getSession(String serviceUri, String username, String password) {
        return (ComputeSession) applicationContext.getBean(prototypeSessionName, serviceUri, username, password);
    }

    public void setPrototypeSessionName(String prototypeSessionName) {
        this.prototypeSessionName = prototypeSessionName;
    }

    public void setCoordinator(CoordinatorClientImpl coordinator) {
        this.coordinator = coordinator;
    }

}
