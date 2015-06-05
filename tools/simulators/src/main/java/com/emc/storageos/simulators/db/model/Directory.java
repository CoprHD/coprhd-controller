/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators.db.model;

import com.emc.storageos.db.client.model.*;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * Class of directory data object
 */
@XmlRootElement(name = "Directory")
@Cf("Directory")
public class Directory extends DataObject {
    private URI _quota;
    private URI _parent;

    @XmlElement
    @AlternateId("AltIdIndex")
    @Name("quota")
    public URI getQuota() {
        return _quota;
    }

    public void setQuota(URI quota) {
        this._quota = quota;
        setChanged("quota");
    }

    @XmlElement
    @RelationIndex(cf = "RelationIndex", type = Directory.class)
    @Name("parent")
    public URI getParent() {
        return _parent;
    }

    public void setParent(URI parent) {
        this._parent = parent;
        setChanged("parent");
    }
}
