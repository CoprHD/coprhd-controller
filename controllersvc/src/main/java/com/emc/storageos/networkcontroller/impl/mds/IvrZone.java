/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.NetworkSystem;

public class IvrZone extends BaseZoneInfo {
    private static final Logger _log = LoggerFactory.getLogger(IvrZone.class);

    private List<IvrZoneMember> members;
    private NetworkSystem ivrNetworkSystem = null; // ivr network system that manage this ivr zone

    public IvrZone(String name) {
        super(name);
    }

    public List<IvrZoneMember> getMembers() {
        if (members == null) {
            members = new ArrayList<IvrZoneMember>();
        }
        return members;
    }

    public void setMembers(List<IvrZoneMember> members) {
        this.members = members;
    }

    public void print() {
        _log.info("zone: " + name + " (" + instanceID + ") " + (active ? "active" : ""));
        for (IvrZoneMember member : members) {
            member.print();
        }
    }

    /**
     * Verify if given ivr zone member is a member of ivr zone
     * 
     * @param zoneMember
     * @return
     */
    public boolean contains(IvrZoneMember zoneMember) {
    	for (IvrZoneMember ivrZoneMember : getMembers()) {
    		if (ivrZoneMember.equals(zoneMember)) {
    			return true;
    		}
    	}
        return false;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IvrZone) {
            IvrZone ivrZone = (IvrZone) obj;
            List<IvrZoneMember> tmpMembers = new ArrayList<IvrZoneMember>(getMembers());
            tmpMembers.removeAll(ivrZone.getMembers());

            return ivrZone.getName().equalsIgnoreCase(getName()) && tmpMembers.isEmpty();
        }

        return false;
    }

    public NetworkSystem getIvrNetworkSystem() {
        return ivrNetworkSystem;
    }

    public void setIvrNetworkSystem(NetworkSystem ivrNetworkSystem) {
        this.ivrNetworkSystem = ivrNetworkSystem;
    }
}
