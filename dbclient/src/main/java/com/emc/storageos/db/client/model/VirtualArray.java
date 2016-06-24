/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Neighborhood data object
 */
@Cf("VirtualArray")
public class VirtualArray extends DataObjectWithACLs implements Serializable, GeoVisibleResource {
    static final long serialVersionUID = -2509414290367339054L;

    Boolean autoSanZoning = new Boolean(true);

    Boolean noNetwork = new Boolean(false);

    Boolean deviceRegistered = new Boolean(false);

    String protectionType = "";

    @Name("autoSanZoning")
    public Boolean getAutoSanZoning() {
        return autoSanZoning;
    }

    public void setAutoSanZoning(Boolean autoSanZoning) {
        this.autoSanZoning = autoSanZoning;
        setChanged("autoSanZoning");
    }

    @Name("noNetwork")
    public Boolean getNoNetwork() {
        return noNetwork;
    }

    public void setNoNetwork(Boolean noNetwork) {
        this.noNetwork = noNetwork;
        setChanged("noNetwork");
    }

    @Name("deviceRegistered")
    public Boolean getDeviceRegistered() {
        return deviceRegistered;
    }

    public void setDeviceRegistered(Boolean deviceRegistered) {
        this.deviceRegistered = deviceRegistered;
        setChanged("deviceRegistered");
    }

    @Name("protectionType")
    public String getProtectionType() {
        return protectionType;
    }

    public void setProtectionType(String protectionType) {
        this.protectionType = protectionType;
        setChanged("protectionType");
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        out.writeBoolean(autoSanZoning);
        out.writeBoolean(deviceRegistered);
        out.writeObject(protectionType);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        autoSanZoning = in.readBoolean();
        deviceRegistered = in.readBoolean();
        protectionType = (String) in.readObject();
    }
}
