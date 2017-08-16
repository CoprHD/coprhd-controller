/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.List;


/**
 * Class to hold mount path and host of Datastore mount
 *
 */
public class DatastoreMount {

    private String remoteHost;

    private String mountPath;

    private String name;

    private List<String> hostList;


    public DatastoreMount(String name, String remoteHost, String mountPath, List<String> hostList) {
        this.name = name;
        this.remoteHost = remoteHost;
        this.mountPath = mountPath;
        this.hostList = hostList;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public String getMountPath() {
        return mountPath;
    }

    public String getName() {
        return name;
    }

    public List<String> getHostList() {
        return hostList;
    }
}
