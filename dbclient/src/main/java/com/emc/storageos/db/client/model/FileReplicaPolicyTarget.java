/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "file_policy_replica_target")
public class FileReplicaPolicyTarget extends AbstractSerializableNestedObject {

    private static final String PATH = "path";
    private static final String STORAGE_SYSTEM = "storageSystem";
    private static final String NAS_SERVER = "nasServer";
    private static final String APPLIED_AT = "appliedAt";

    /**
     * JAXB requirement
     */
    public FileReplicaPolicyTarget() {
    }

    public String getPath() {
        return getStringField(PATH);
    }

    public void setPath(String path) {
        if (path == null) {
            path = "";
        }
        setField(PATH, path);
    }

    public String getStorageSystem() {
        return getStringField(STORAGE_SYSTEM);
    }

    public void setStorageSystem(String storageSystem) {
        if (storageSystem == null) {
            storageSystem = "";
        }
        setField(STORAGE_SYSTEM, storageSystem);
    }

    public String getNasServer() {
        return getStringField(NAS_SERVER);
    }

    public String getAppliedAt() {
        return getStringField(APPLIED_AT);
    }

    public void setNasServer(String nasServer) {
        if (nasServer == null) {
            nasServer = "";
        }
        setField(NAS_SERVER, nasServer);
    }

    public void setAppliedAt(String appliedAt) {
        if (appliedAt == null) {
            appliedAt = "";
        }
        setField(APPLIED_AT, appliedAt);
    }

    public String getFileTargetReplicaKey() {
        return String.format("%1$s.%2$s.%3$s", getStorageSystem(), getNasServer(), getPath());
    }

    public static String fileTargetReplicaKey(String storageSystem, String nasServer, String appliedAt) {
        return String.format("%1$s.%2$s.%3$s", storageSystem, nasServer, appliedAt);
    }

}
