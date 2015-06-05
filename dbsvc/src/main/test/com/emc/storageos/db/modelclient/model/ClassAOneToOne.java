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
