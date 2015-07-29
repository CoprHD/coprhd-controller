/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
