/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.NamedRelatedResourceRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of {@link com.emc.storageos.model.NamedRelatedResourceRep} representing Migrations.
 */
@XmlRootElement(name = "migrations")
public class MigrationList {
    private List<NamedRelatedResourceRep> migrations;

    public MigrationList() {
    }

    public MigrationList(List<NamedRelatedResourceRep> migrations) {
        this.migrations = migrations;
    }

    /**
     * The list of migrations.
     * 
     */
    @XmlElement(name = "migration")
    public List<NamedRelatedResourceRep> getMigrations() {
        if (migrations == null) {
            migrations = new ArrayList<NamedRelatedResourceRep>();
        }
        return migrations;
    }

    public void setMigrations(List<NamedRelatedResourceRep> migrations) {
        this.migrations = migrations;
    }
}
