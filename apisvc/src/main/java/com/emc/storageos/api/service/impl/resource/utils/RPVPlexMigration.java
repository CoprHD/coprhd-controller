/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

import java.net.URI;
import com.emc.storageos.db.client.model.VirtualPool;

public class RPVPlexMigration {
    private Volume.PersonalityTypes type;
    private Volume.PersonalityTypes subType;
    private URI varrayId;
    private VirtualPool migrateFromVpool;
    private VirtualPool migrateToVpool;

    public RPVPlexMigration() {
        super();
    }

    public RPVPlexMigration(PersonalityTypes type, URI varrayId, VirtualPool migrateFromVpool, VirtualPool migrateToVpool) {
        super();
        this.type = type;
        this.varrayId = varrayId;
        this.migrateFromVpool = migrateFromVpool;
        this.migrateToVpool = migrateToVpool;
    }
    
    public RPVPlexMigration(PersonalityTypes type, PersonalityTypes subType, URI varrayId, VirtualPool migrateFromVpool, VirtualPool migrateToVpool) {
        super();
        this.type = type;
        this.subType = subType;
        this.varrayId = varrayId;
        this.migrateFromVpool = migrateFromVpool;
        this.migrateToVpool = migrateToVpool;
    }

    public Volume.PersonalityTypes getType() {
        return type;
    }

    public void setType(Volume.PersonalityTypes type) {
        this.type = type;
    }

    public URI getVarray() {
        if (varrayId == null) {
            varrayId = NullColumnValueGetter.getNullURI();
        }
        return varrayId;
    }

    public void setVarray(URI varray) {
        this.varrayId = varray;
    }

    public VirtualPool getMigrateFromVpool() {
        return migrateFromVpool;
    }

    public void setMigrateFromVpool(VirtualPool migrateFromVpool) {
        this.migrateFromVpool = migrateFromVpool;
    }

    public VirtualPool getMigrateToVpool() {
        return migrateToVpool;
    }

    public void setMigrateToVpool(VirtualPool migrateToVpool) {
        this.migrateToVpool = migrateToVpool;
    }

    public Volume.PersonalityTypes getSubType() {
        return subType;
    }

    public void setSubType(Volume.PersonalityTypes subType) {
        this.subType = subType;
    }
}
