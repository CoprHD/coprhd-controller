/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of {@link com.emc.storageos.model.block.NamedRelatedMigrationRep} representing Migrations.
 */
@XmlRootElement(name = "migrations")
public class MigrationList {
    private List<NamedRelatedMigrationRep> migrations;

    public MigrationList() {
    }

    public MigrationList(List<NamedRelatedMigrationRep> migrations) {
        this.migrations = migrations;
    }

    /**
     * The list of migrations.
     */
    @XmlElement(name = "migration")
    public List<NamedRelatedMigrationRep> getMigrations() {
        if (migrations == null) {
            migrations = new ArrayList<NamedRelatedMigrationRep>();
        }
        return migrations;
    }

    public void setMigrations(List<NamedRelatedMigrationRep> migrations) {
        this.migrations = migrations;
    }
}
