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
 * Class of snapshot data object
 */
@XmlRootElement(name = "Snapshot")
@Cf("Snapshot")
public class Snapshot extends DataObject {
    private URI _directory;
    private URI _path;

    @XmlElement
    @RelationIndex(cf = "RelationIndex", type = Directory.class)
    @Name("directory")
    public URI getDirectory() {
        return _directory;
    }

    public void setDirectory(URI directory) {
        this._directory = directory;
        setChanged("directory");
    }

    @XmlElement
    @Name("path")
    public URI getPath() {
        return _path;
    }

    public void setPath(URI path) {
        this._path = path;
        setChanged("path");
    }
}
