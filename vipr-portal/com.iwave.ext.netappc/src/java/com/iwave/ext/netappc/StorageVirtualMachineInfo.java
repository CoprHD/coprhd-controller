/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc;

import java.util.ArrayList;
import java.util.List;

public class StorageVirtualMachineInfo {
    private String name;         // Name of the SVM.
    private String uuid;         // UUID of the SVM.
    private String rootVolume;   // Name of the SVM's root volume
    private List<SVMNetInfo> interfaces;   // List of all network interfaces associated with this svm.

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getRootVolume() {
        return rootVolume;
    }

    public void setRootVolume(String rootVolume) {
        this.rootVolume = rootVolume;
    }

    public List<SVMNetInfo> getInterfaces() {
        if (interfaces == null) {
            interfaces = new ArrayList<SVMNetInfo>();
        }
        return interfaces;
    }

    public void setInterfaces(List<SVMNetInfo> interfaces) {
        this.interfaces = interfaces;
    }
}
