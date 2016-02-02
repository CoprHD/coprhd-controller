/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class captures add/remove Raid Level details.
 */
public class RaidLevelChanges {

    private RaidLevelAssignments add;
    private RaidLevelAssignments remove;

    public RaidLevelChanges() {
    }

    public RaidLevelChanges(RaidLevelAssignments add,
            RaidLevelAssignments remove) {
        this.add = add;
        this.remove = remove;
    }

    /**
     * RAID levels to be added to the virtual pool.
     * 
     */
    @XmlElement(name = "add")
    public RaidLevelAssignments getAdd() {
        return add;
    }

    public void setAdd(RaidLevelAssignments add) {
        this.add = add;
    }

    /**
     * RAID levels to be removed from the virtual pool.
     * 
     */
    @XmlElement(name = "remove")
    public RaidLevelAssignments getRemove() {
        return remove;
    }

    public void setRemove(RaidLevelAssignments remove) {
        this.remove = remove;
    }

}
