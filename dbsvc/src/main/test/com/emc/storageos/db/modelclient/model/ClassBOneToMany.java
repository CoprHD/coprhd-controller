/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.modelclient.model;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;

/**
 * @author cgarber
 *
 */
@Cf("ClassB")
public class ClassBOneToMany extends DataObject {
    
    private ClassAOneToMany ainstance;
    
    @Relation(type = ClassAOneToMany.class, mappedBy = "bIds")
    @Name("aInstance")
    public ClassAOneToMany getAinstance() {
        return ainstance;
    }

    public void setAinstance(ClassAOneToMany aInstance) {
        this.ainstance = aInstance;
    }


}