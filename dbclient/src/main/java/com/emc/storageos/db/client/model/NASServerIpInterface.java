/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.model.valid.EnumType;

/**
 * An interface on NASServer through which the fileSystems on the
 * NASServer can be accessed
 * 
 * @author ganeso
 * 
 */

@Cf("NASServerIpInterface")
public abstract class NASServerIpInterface extends DiscoveredDataObject {
    private String interfaceName;
    private String ipAddress;
    private String ipMask;
    private String ipBroadCastAddress;

    @Name("interfaceName")
    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    @Name("ipAddress")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @Name("ipMask")
    public String getIpMask() {
        return ipMask;
    }

    public void setIpMask(String ipMask) {
        this.ipMask = ipMask;
    }

    @Name("ipBroadCastAddress")
    public String getIpBroadCastAddress() {
        return ipBroadCastAddress;
    }

    public void setIpBroadCastAddress(String ipBroadCastAddress) {
        this.ipBroadCastAddress = ipBroadCastAddress;
    }

}
