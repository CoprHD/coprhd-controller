/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util.models.updated2;

import com.emc.storageos.db.client.model.*;
import java.net.URI;

@Cf("Resource2")
public class Resource2 extends DataObject {
    private NamedURI res1;
    private StringSet associated;
    private URI res3;

    @NamedRelationIndex(cf="TestRelationIndex", type=Resource1.class)
    @Name("res1")
    public NamedURI getRes1() {
        return res1;
    }

    public void setRes1(NamedURI res1) {
        this.res1 = res1;
        setChanged("res1");
    }

    @AlternateId("TestAltIdIndex")
    @Name("associated")
    public StringSet getAssociated() {
        return associated;
    }

    public void setAssociated(StringSet associated) {
        this.associated = associated;
        setChanged("associated");
    }

    @RelationIndex(cf="TestRelationIndex2", type = Resource3.class)
    @Name("res3")
    public URI getRes3() {
        return res3;
    }

    public void setRes3(URI res3) {
        this.res3 = res3;
        setChanged("res3");
    }
}
