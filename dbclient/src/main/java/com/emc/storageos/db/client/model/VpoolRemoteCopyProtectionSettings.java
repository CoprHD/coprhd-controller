/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.model.valid.EnumType;

@Cf("VpoolRemoteProtectionCopySettings")
public class VpoolRemoteCopyProtectionSettings extends DataObject {

    // protection VirtualPool
    private URI virtualPool;

    private URI virtualArray;

    private String copyMode = CopyModes.ASYNCHRONOUS.toString();

    @Name("remoteVirtualPool")
    public URI getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        this.virtualPool = virtualPool;
        setChanged("remoteVirtualPool");
    }

    @Name("remoteVirtualArray")
    public URI getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
        setChanged("remoteVirtualArray");
    }

    @EnumType(CopyModes.class)
    @Name("copyMode")
    public String getCopyMode() {
        return copyMode;
    }

    public void setCopyMode(String copyMode) {
        this.copyMode = copyMode;
        setChanged("copyMode");
    }

    public enum CopyModes {
        SYNCHRONOUS, ASYNCHRONOUS, ACTIVE;

        public static boolean lookup(String mode) {
            for (CopyModes cMode : values()) {
                if (cMode.name().equalsIgnoreCase(mode)) {
                    return true;
                }
            }
            return false;
        }
    }

}
