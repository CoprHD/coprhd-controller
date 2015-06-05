/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.server.upgrade.util.models.old;

import com.emc.storageos.db.client.model.*;

import java.net.URI;

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
