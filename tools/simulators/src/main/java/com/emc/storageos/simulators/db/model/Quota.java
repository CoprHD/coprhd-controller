/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators.db.model;

import com.emc.storageos.db.client.model.AbstractSerializableNestedObject;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * Quota
 */
@XmlRootElement(name = "quota")
public class Quota extends AbstractSerializableNestedObject {
    private static final String ID = "id";
    private static final String LIMIT = "limit";
    private static final String COUNT = "count";
    private static final String DIR = "dir";

    /**
     * JAXB requirement
     */
    public Quota() {
    }

    public Quota(String id, long limit, int count, URI dir) {
        setId(id);
        setLimit(limit);
        setCount(count);
        setDirectory(dir);
    }

    @XmlElement
    public String getId() {
        return getStringField(ID);
    }

    public void setId(String id) {
        setField(ID, id);
    }

    @XmlElement
    public long getLimit() {
        return getLongField(LIMIT);
    }

    public void setLimit(long limit) {
        setField(LIMIT, limit);
    }

    @XmlElement
    public int getCount() {
        return getIntField(COUNT);
    }

    public void setCount(int count) {
        setField(COUNT, count);
    }

    @XmlElement
    public URI getDirectory() {
        return getURIField(DIR);
    }

    public void setDirectory(URI dir) {
        setField(DIR, dir);
    }

}
