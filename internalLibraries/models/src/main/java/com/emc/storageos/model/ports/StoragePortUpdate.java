/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.ports;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.varray.VirtualArrayResourceUpdateParam;

import java.net.URI;

/**
 * Class encapsulates the parameters for a storage port update request.
 */
@XmlRootElement(name = "storage_port_update")
public class StoragePortUpdate extends VirtualArrayResourceUpdateParam {

    private URI network;

    private String portNetworkId;

    public StoragePortUpdate() {
    }

    public StoragePortUpdate(URI network) {
        super(new VirtualArrayAssignmentChanges());
        this.network = network;
    }

    public StoragePortUpdate(URI network, VirtualArrayAssignmentChanges varrayChanges) {
        super(varrayChanges);
        this.network = network;
    }

    /**
     * The new network for a storage port update request
     * 
     * @valid example: a valid URI.
     */
    @XmlElement(name = "network")
    public URI getNetwork() {
        return network;
    }

    public void setNetwork(URI network) {
        this.network = network;
    }

    /**
     * Storage port network identifier.
     * 
     * This is only applicable to Cinder storage system
     * as currently there is no API to discover it from Cinder.
     * 
     * @valid example: FC - port WWN,
     *        IP - iSCSI Qualified Name (IQN) or Extended Unique Identifier (EUI)
     */
    @XmlElement(name = "port_network_id", nillable = true)
    public String getPortNetworkId() {
        return portNetworkId;
    }

    public void setPortNetworkId(String portNetworkId) {
        this.portNetworkId = portNetworkId;
    }
}
