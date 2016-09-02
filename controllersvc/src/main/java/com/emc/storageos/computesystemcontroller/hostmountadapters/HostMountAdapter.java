/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.ModelClient;

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

    public void createDirectory(URI hostId, String mountPath);

    public void addToFSTab(URI hostId, String mountPath, URI resId, String subDirectory, String security, String fsType);

    public void mountDevice(URI hostId, String mountPath);

    public void verifyMountPoint(URI hostId, String mountPath);

    public void deleteDirectory(URI hostId, String mountPath);

    public void removeFromFSTab(URI hostId, String mountPath);

    public void removeFromFSTabRollBack(URI hostId, String mountPath, URI resId);

    public void unmountDevice(URI hostId, String mountPath);

}
