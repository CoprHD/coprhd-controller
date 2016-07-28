/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

public class RemoteSystemCreateParam {

    private String managementAddress;
    private String remoteUsername;
    private String remotePassword;

    public String getManagementAddress() {
        return managementAddress;
    }

    public void setManagementAddress(String managementAddress) {
        this.managementAddress = managementAddress;
    }

    public String getRemoteUsername() {
         return remoteUsername;
    }
    public void setRemoteUsername(String remoteUsername) {
         this.remoteUsername = remoteUsername;
    }

    public String getRemotePassword() {
          return remotePassword;
     }
     public void setRemotePassword(String remotePassword) {
         this.remotePassword = remotePassword;
     }


}
