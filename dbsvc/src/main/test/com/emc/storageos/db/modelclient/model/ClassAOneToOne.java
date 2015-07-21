/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.modelclient.model;

import java.net.URI;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.RelationIndex;

/**
 * @author cgarber
 *
 */
@Cf("ClassA")
public class ClassAOneToOne extends DataObject {
    
    private URI bid;
    private ClassBOneToOne binstance;
    
    @RelationIndex(cf = "RelationIndex", type = ClassBOneToOne.class)
    @Name("bId")
    public URI getBid() {
        return bid;
    }

    public void setBid(URI bid) {
        this.bid = bid;
        setChanged("bId");
    }

    @Relation(type = ClassBOneToOne.class, mappedBy = "bId")
    @Name("bInstance")
    public ClassBOneToOne getBinstance() {
        return binstance;
    }

    public void setBinstance(ClassBOneToOne binstance) {
        this.binstance = binstance;
    }

}
