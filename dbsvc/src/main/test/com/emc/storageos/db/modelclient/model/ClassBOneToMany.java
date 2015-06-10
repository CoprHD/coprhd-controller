/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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