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

import java.util.List;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.StringSet;

/**
 * @author cgarber
 *
 */
@Cf("ClassB")
public class ClassBManyToMany extends DataObject {
    
    private StringSet aids;
    private List<ClassAManyToMany> ainstances;
    
    @Name("aIds")
    public StringSet getAids() {
        return aids;
    }

    public void setAids(StringSet aids) {
        this.aids = aids;
    }

    @Relation(type = ClassAManyToMany.class, mappedBy = "aIds")
    @Name("aInstances")
    public List<ClassAManyToMany> getAinstances() {
        return ainstances;
    }

    public void setAinstances(List<ClassAManyToMany> ainstances) {
        this.ainstances = ainstances;
    }

}
