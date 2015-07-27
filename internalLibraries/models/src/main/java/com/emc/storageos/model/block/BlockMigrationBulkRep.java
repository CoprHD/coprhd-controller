/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.BulkRestRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response class for bulk requests for block migrations.
 */
@XmlRootElement(name = "bulk_block_migrations")
public class BlockMigrationBulkRep extends BulkRestRep {
    
    // List of migration responses.
    private List<MigrationRestRep> migrations;

    /**
     * Default constructor.
     */
    public BlockMigrationBulkRep() {
    }
    
    /**
     * Constructor takes the list of migration response instances.
     * 
     * @param list The list of migration response instances.
     */
    public BlockMigrationBulkRep(List<MigrationRestRep> migrations) {
        this.migrations = migrations;
    }

    /**
     * The list of migration response instances.
     * 
     * @valid none
     * 
     * @return The list of migration response instances. 
     */
    @XmlElement(name = "block_migration")
    public List<MigrationRestRep> getMigrations() {
        if (migrations == null) {
            migrations = new ArrayList<MigrationRestRep>();
        }
        return migrations;
    }

    /**
     * Setter for the list of migration response instances.
     * 
     * @param list The list of migration response instances.
     */
    public void setMigrations(List<MigrationRestRep> list) {
        migrations = list;
    }
}
