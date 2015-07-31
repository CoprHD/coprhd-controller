/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.systems;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.emc.storageos.model.NamedRelatedResourceRep;

import java.util.LinkedHashSet;
import java.util.Set;

@XmlRootElement(name = "storage_connectivity")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class StorageSystemConnectivityRestRep {
    // the system protecting them
    private NamedRelatedResourceRep protectionSystem;

    // the storage-system it is connected to
    private NamedRelatedResourceRep storageSystem;

    // the type of protection
    private Set<String> connectionTypes;

    public StorageSystemConnectivityRestRep() {
    }

    public StorageSystemConnectivityRestRep(
            NamedRelatedResourceRep protectionSystem,
            NamedRelatedResourceRep storageSystem, Set<String> connectionTypes) {
        this.protectionSystem = protectionSystem;
        this.storageSystem = storageSystem;
        this.connectionTypes = connectionTypes;
    }

    /**
     * The type of connection that exists between two storage systems
     * 
     * @valid rp
     * @valid vplex
     * 
     */
    @XmlElement(name = "connection_type")
    public Set<String> getConnectionTypes() {
        if (connectionTypes == null) {
            connectionTypes = new LinkedHashSet<String>();
        }
        return connectionTypes;
    }

    public void setConnectionTypes(Set<String> connectionTypes) {
        this.connectionTypes = connectionTypes;
    }

    /**
     * The system protecting the primary storage system
     * 
     * @valid none
     */
    @XmlElement(name = "protection_system")
    public NamedRelatedResourceRep getProtectionSystem() {
        return protectionSystem;
    }

    public void setProtectionSystem(NamedRelatedResourceRep protectionSystem) {
        this.protectionSystem = protectionSystem;
    }

    /**
     * The storage system , to which the protection system is connected.
     * 
     * @valid none
     */
    @XmlElement(name = "storage_system")
    public NamedRelatedResourceRep getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(NamedRelatedResourceRep storageSystem) {
        this.storageSystem = storageSystem;
    }
}
