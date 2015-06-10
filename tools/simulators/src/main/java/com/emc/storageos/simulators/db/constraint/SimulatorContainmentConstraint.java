/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators.db.constraint;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.simulators.db.model.Directory;

import java.net.URI;

/**
 * Containment constraint.
 */
public interface SimulatorContainmentConstraint {
    public static class Factory {
        public static ContainmentConstraint getDirectoryByParentConstraint(URI dir) {
            DataObjectType doType = TypeMap.getDoType(Directory.class);
            ColumnField field = doType.getColumnField("parent");
            return new ContainmentConstraintImpl(dir, Directory.class, field);
        }
    }
}
