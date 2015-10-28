/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.migrationtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CGRelationshipEstablisher extends Executor {
    private static final Logger log = LoggerFactory.getLogger(CGRelationshipEstablisher.class);

    private static final boolean DEBUG = false;

    @Override
    public boolean execute() {
        // Write logic to update CG Relationships between Source CG & target CG.
        return false;
    }

}
