/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Class captures add/remove protocol details.
 */
public class ProtocolChanges {

    private ProtocolAssignments add;
    private ProtocolAssignments remove;

    public ProtocolChanges() {}
    
    public ProtocolChanges(ProtocolAssignments add, ProtocolAssignments remove) {
        this.add = add;
        this.remove = remove;
    }

    /**
     * Protocols to be added to the virtual pool
     * 
     * @valid none
     */
    @XmlElement(name = "add")
    public ProtocolAssignments getAdd() {
        return add;
    }

    public void setAdd(ProtocolAssignments add) {
        this.add = add;
    }

    /**
     * Protocols top be removed from the virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "remove")
    public ProtocolAssignments getRemove() {
        return remove;
    }

    public void setRemove(ProtocolAssignments remove) {
        this.remove = remove;
    }
    
}
