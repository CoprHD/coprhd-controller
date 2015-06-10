/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.util;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;

public class KeyspaceUtil {
    
    public static final String GLOBAL = "global";
    
    /**
     * returns true if the class is strictly local
     * @param clazz
     * @return
     */
    public static boolean isLocal(Class<? extends DataObject> clazz) {
        return (!clazz.isAnnotationPresent(DbKeyspace.class) ||
                clazz.getAnnotation(DbKeyspace.class).value().equals(Keyspaces.LOCAL));
    }
    
    /**
     * returns true if clazz is strictly global (non-hybrid)
     * @param clazz
     * @return
     */
    public static boolean isGlobal(Class<? extends DataObject> clazz) {
        return (clazz.isAnnotationPresent(DbKeyspace.class) &&
                clazz.getAnnotation(DbKeyspace.class).value().equals(Keyspaces.GLOBAL));
    }
    
}
