package com.emc.storageos.api.service.impl.resource.utils;

/**
 * Class to hold mount path and host of Datastore mount
 *
 */
public class DatastoreMount {

    private String host;

    private String mountPath;

    private String name;

    public DatastoreMount(String host, String mountPath, String name) {
        this.host = host;
        this.mountPath = mountPath;
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public String getMountPath() {
        return mountPath;
    }

    public String getName() {
        return name;
    }
}
