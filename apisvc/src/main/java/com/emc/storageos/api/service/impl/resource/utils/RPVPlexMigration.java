/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;

import java.net.URI;
import com.emc.storageos.db.client.model.VirtualPool;

public class RPVPlexMigration {
    private Volume.PersonalityTypes personality;
    private Volume.PersonalityTypes subType;
    private URI varrayId;
    private VirtualPool migrateFromVpool;
    private VirtualPool migrateToVpool;

    public RPVPlexMigration() {
        super();
    }

    public RPVPlexMigration(PersonalityTypes personality, URI varrayId, VirtualPool migrateFromVpool, VirtualPool migrateToVpool) {
        super();
        this.personality = personality;
        this.varrayId = varrayId;
        this.migrateFromVpool = migrateFromVpool;
        this.migrateToVpool = migrateToVpool;
    }
    
    public RPVPlexMigration(PersonalityTypes personality, PersonalityTypes subType, URI varrayId, VirtualPool migrateFromVpool, VirtualPool migrateToVpool) {
        super();
        this.personality = personality;
        this.subType = subType;
        this.varrayId = varrayId;
        this.migrateFromVpool = migrateFromVpool;
        this.migrateToVpool = migrateToVpool;
    }

    public Volume.PersonalityTypes getPersonality() {
        return personality;
    }

    public void setPersonality(Volume.PersonalityTypes personality) {
        this.personality = personality;
    }

    public URI getVarray() {
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
