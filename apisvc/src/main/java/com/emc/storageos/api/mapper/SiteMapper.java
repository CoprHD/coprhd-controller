/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import java.util.Date;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteMonitorResult;
import com.emc.storageos.coordinator.client.model.SiteNetworkState.NetworkHealth;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.model.dr.SiteParam;
import com.emc.storageos.model.dr.SiteRestRep;

public class SiteMapper {
    public SiteRestRep map(Site from) {
        if (from == null) {
            return null;
        }
        SiteRestRep to = new SiteRestRep();
        map(from, to);
        return to;
    }

    public SiteRestRep mapWithNetwork(Site from, DrUtil drUtil) {
        if (from == null) {
            return null;
        }
        SiteRestRep to = new SiteRestRep();
        map(from, to);
        NetworkHealth networkHealth = drUtil.getSiteNetworkState(from.getUuid()).getNetworkHealth();
        SiteState state = from.getState();
        // Skip network health state amid ADDING/RESUMING
        if ( networkHealth != null && SiteState.STANDBY_ADDING != state && SiteState.STANDBY_RESUMING != state) {
            to.setNetworkHealth(networkHealth.toString());
        }
        
        // check if syssvc are up
        boolean runningState = drUtil.isSiteUp(from.getUuid());
        if (runningState && !from.getState().equals(SiteState.ACTIVE)) {
            // check if dbsvc are up
            SiteMonitorResult monitorResult = drUtil.getCoordinator().getTargetInfo(from.getUuid(), SiteMonitorResult.class);
            if (monitorResult != null && monitorResult.getDbQuorumLostSince() > 0) {
                runningState = false;
            }
        }
        to.setRunningState(runningState);

        Date lastSyncTime = drUtil.getLastSyncTime(from);
        if (lastSyncTime != null) {
            to.setLastSyncTime(lastSyncTime.getTime());
        }

        return to;
    }
    
    public void map(Site from, SiteRestRep to) {
        if (from == null) {
            return;
        }
        
        to.setUuid(from.getUuid());
        to.setVdcShortId(from.getVdcShortId());
        to.setName(from.getName());
        to.setVipEndpoint(from.getVipEndPoint());
        to.setDescription(from.getDescription());
        to.setState(from.getState().toString());
        to.setCreateTime(from.getCreationTime());
    }

    public void map(Site from, SiteParam to) {
        to.setHostIPv4AddressMap(new StringMap(from.getHostIPv4AddressMap()));
        to.setHostIPv6AddressMap(new StringMap(from.getHostIPv6AddressMap()));
        to.setName(from.getName()); // this is the name for the standby site
        to.setUuid(from.getUuid());
        to.setVip(from.getVip());
        to.setVip6(from.getVip6());
        to.setShortId(from.getSiteShortId());
        to.setState(from.getState().toString());
        to.setNodeCount(from.getNodeCount());
        to.setCreationTime(from.getCreationTime());
    }

    public void map(SiteParam from, Site to) {
        to.setUuid(from.getUuid());
        to.setName(from.getName());
        to.setVip(from.getVip());
        to.setVip6(from.getVip6());
        to.getHostIPv4AddressMap().putAll(from.getHostIPv4AddressMap());
        to.getHostIPv6AddressMap().putAll(from.getHostIPv6AddressMap());
        to.setSiteShortId(from.getShortId());
        to.setState(SiteState.valueOf(from.getState()));
        to.setNodeCount(from.getNodeCount());
        to.setCreationTime(from.getCreationTime());
    }

}
