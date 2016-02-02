/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class captures add/remove protocol details.
 */
public class ProtocolChanges {

    private ProtocolAssignments add;
    private ProtocolAssignments remove;

    public ProtocolChanges() {
    }

    public ProtocolChanges(ProtocolAssignments add, ProtocolAssignments remove) {
        this.add = add;
        this.remove = remove;
    }

    /**
     * Protocols to be added to the virtual pool
     * 
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
     */
    @XmlElement(name = "remove")
    public ProtocolAssignments getRemove() {
        return remove;
    }

    public void setRemove(ProtocolAssignments remove) {
        this.remove = remove;
    }

}
