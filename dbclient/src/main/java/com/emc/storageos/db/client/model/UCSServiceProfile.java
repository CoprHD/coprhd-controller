/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("UCSServiceProfile")
public class UCSServiceProfile extends DiscoveredSystemObject {

    private String name;
    private URI computeSystem;
    private String dn;
    private String uuid;
    private String computeElementDn;
    private URI host;

    @Name("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        setChanged("name");
    }

    @Name("host")
    public URI getHost() {
        return host;
    }

    public void setHost(URI host) {
        this.host = host;
        setChanged("host");
    }

    @RelationIndex(cf = "RelationIndex", type = ComputeSystem.class)
    @Name("computeSystem")
    public URI getComputeSystem() {
        return computeSystem;
    }

    public void setComputeSystem(URI computeSystem) {
        this.computeSystem = computeSystem;
        setChanged("computeSystem");
    }

    @Name("dn")
    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
        setChanged("dn");
    }

    @Name("uuid")
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
        setChanged("uuid");
    }

    @Name("computeElementDn")
    public String getComputeElementDn() {
        return computeElementDn;
    }

    public void setComputeElementDn(String computeElementDn) {
        this.computeElementDn = computeElementDn;
        setChanged("computeElementDn");
    }


}
