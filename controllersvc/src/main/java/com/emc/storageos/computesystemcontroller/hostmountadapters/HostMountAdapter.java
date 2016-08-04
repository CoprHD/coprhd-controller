/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.workflow.Workflow.Method;

/**
 * 
 * @author yelkaa
 *
 */
public interface HostMountAdapter {
    public String getErrorMessage(Throwable t);

    public void setModelClient(ModelClient modelClient);

    public void setDbClient(DbClient dbClient);

    public void setCoordinator(CoordinatorClient coordinator);

    public void doMount(HostDeviceInputOutput args) throws ControllerException;

    public void doUnmount(HostDeviceInputOutput args) throws ControllerException;

    public Method createDirectoryMethod(HostDeviceInputOutput args);

    public Method addtoFSTabMethod(HostDeviceInputOutput args);

    public Method mountDeviceMethod(HostDeviceInputOutput args);

    public Method verifyMountPointMethod(HostDeviceInputOutput args);

    public Method unmountDeviceMethod(HostDeviceInputOutput args);

    public Method removeFromFSTabMethod(HostDeviceInputOutput args);

    public Method deleteDirectoryMethod(HostDeviceInputOutput args);

    public Method setMountTagMethod(HostDeviceInputOutput args);

    public Method removeMountTagMethod(HostDeviceInputOutput args);
}
