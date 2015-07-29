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
public class ClassBOneToOne extends DataObject {

    private ClassAOneToOne ainstance;

    @Relation(type = ClassAOneToOne.class, mappedBy = "bId")
    @Name("aInstance")
    public ClassAOneToOne getAinstance() {
        return ainstance;
    }

    public void setAinstance(ClassAOneToOne ainstance) {
        this.ainstance = ainstance;
    }

}