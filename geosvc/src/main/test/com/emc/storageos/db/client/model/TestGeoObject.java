/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;

@Cf("TestGeoObject")
@DbKeyspace(Keyspaces.GLOBAL)
public class TestGeoObject extends DataObject {
    private static final long serialVersionUID = 1L;

    private URI vpool;
    private URI varray;

    @NamedRelationIndex(cf = "NamedRelation", type = VirtualPool.class)
    @Name("vpool")
    public URI getVpool() {
        return vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

    @NamedRelationIndex(cf = "NamedRelation", type = VirtualArray.class)
    @Name("varray")
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

}
