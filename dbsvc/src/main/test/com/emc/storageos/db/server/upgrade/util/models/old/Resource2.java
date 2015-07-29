/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util.models.old;

import com.emc.storageos.db.client.model.*;

@Cf("Resource2")
public class Resource2 extends DataObject {
    private NamedURI res1;
    private StringSet associated;

    @Name("res1")
    public NamedURI getRes1() {
        return res1;
    }

    public void setRes1(NamedURI res1) {
        this.res1 = res1;
        setChanged("res1");
    }

    @Name("associated")
    public StringSet getAssociated() {
        return associated;
    }

    public void setAssociated(StringSet associated) {
        this.associated = associated;
        setChanged("associated");
    }
}
