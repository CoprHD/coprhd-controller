/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended from {@link Zone} to include  a list of zones to be removed. And, 
 * for clarity, getAddZones() and setAddZones() are convenient method to call
 * into getMembers() and setMembers()
 */
public class ZoneUpdate extends Zone {

    private List<ZoneMember> removeZones;
    
    public ZoneUpdate(String name) {
        super(name);
    }

    public List<ZoneMember> getRemoveZones() {
        if ( removeZones == null) {
            removeZones = new ArrayList<ZoneMember>();
        }
        
        return removeZones;
    }

    public void setRemoveZones(List<ZoneMember> removeZones) {
        this.removeZones = removeZones;
    }
    
    /**
     * For clarity, getAddZones() is same as {@link {@link #getMembers() getMembers}}
     * @return
     */
    public List<ZoneMember> getAddZones() {
        return getMembers();
    }
    
    /**
     * For clarity, setAddZones(List) is same as {@link #setMembers(List) setMembers}
     * @return
     */
    public void setAddZones(List<ZoneMember> addZones) {
        setMembers(addZones);
    }

}
