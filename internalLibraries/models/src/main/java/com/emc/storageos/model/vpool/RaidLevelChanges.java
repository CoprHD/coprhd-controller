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

/**
 * Class captures add/remove Raid Level details.
 */
public class RaidLevelChanges {
   
    private RaidLevelAssignments add;
    private RaidLevelAssignments remove;

    public RaidLevelChanges() {}
    
    public RaidLevelChanges(RaidLevelAssignments add,
            RaidLevelAssignments remove) {
        this.add = add;
        this.remove = remove;
    }

    /**
     * RAID levels to be added to the virtual pool.
     * 
     * @valid none
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
     * @valid none
     */
    @XmlElement(name = "remove")
    public RaidLevelAssignments getRemove() {
        return remove;
    }

    public void setRemove(RaidLevelAssignments remove) {
        this.remove = remove;
    }
    
}
