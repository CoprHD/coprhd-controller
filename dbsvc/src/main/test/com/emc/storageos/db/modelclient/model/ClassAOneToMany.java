/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.modelclient.model;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StringSet;

/**
 * @author cgarber
 *
 */
@Cf("ClassA")
public class ClassAOneToMany extends DataObject {
    
    private StringSet bids;
    private List<ClassBOneToMany> binstances;


    @RelationIndex(cf = "RelationIndex", type = ClassBOneToMany.class)
    @IndexByKey
    @Name("bIds")
    public StringSet getBids() {
        return bids;
    }

    public void setBids(StringSet bids) {
        this.bids = bids;
    }

    @Relation(type = ClassBOneToMany.class, mappedBy = "bIds")
    @Name("bInstances")
    public List<ClassBOneToMany> getBinstances() {
        return binstances;
    }

    public void setBinstances(List<ClassBOneToMany> binstances) {
        this.binstances = binstances;
    }

    public void addB(ClassBOneToMany b) {
        if (binstances == null) binstances = new ArrayList<ClassBOneToMany>();
        binstances.add(b);
    }

}
