/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("ComputeSanBootImage")
public class ComputeSanBootImage extends DiscoveredDataObject {

    private String type;
    private String dn;
    private String vnicName;
    private URI computeSanBoot;

    @Name("type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        setChanged("type");
    }

    @Name("dn")
    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
        setChanged("dn");
    }

    @Name("vnicName")
    public String getVnicName() {
        return vnicName;
    }

    public void setVnicName(String vnicName) {
        this.vnicName = vnicName;
        setChanged("vnicName");
    }

    @RelationIndex(cf = "RelationIndex", type = ComputeSanBoot.class)
    @Name("computeSanBoot")
    public URI getComputeSanBoot() {
        return computeSanBoot;
    }

    public void setComputeSanBoot(URI computeSanBoot) {
        this.computeSanBoot = computeSanBoot;
        setChanged("computeSanBoot");
    }

}
