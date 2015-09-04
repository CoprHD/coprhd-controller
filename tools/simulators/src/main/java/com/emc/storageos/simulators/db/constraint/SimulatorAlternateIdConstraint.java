/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators.db.constraint;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.simulators.db.model.Directory;

/**
 * Constraint for querying a record by alias
 */
public interface SimulatorAlternateIdConstraint {
    /**
     * Factory for creating alternate ID constraint
     */
    public static class Factory {
        public static AlternateIdConstraint getDirectoryIdConstraint(String altId) {
            DataObjectType doType = TypeMap.getDoType(Directory.class);
            return new AlternateIdConstraintImpl(doType.getColumnField("quota"), altId);
        }
    }
}
