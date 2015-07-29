/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.model.network.FCEndpointRestRep;
import com.emc.storageos.model.network.FCZoneReferenceRestRep;
import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.NetworkSystem;

import static com.emc.storageos.api.mapper.DbObjectMapper.*;

public class NetworkMapper {
    public static NetworkSystemRestRep map(NetworkSystem from) {
        if (from == null) {
            return null;
        }
        NetworkSystemRestRep to = new NetworkSystemRestRep();
        mapDiscoveredSystemObjectFields(from, to);
        to.setSerialNumber(from.getSerialNumber());
        to.setIpAddress(from.getIpAddress());
        to.setSecondaryIP(from.getSecondaryIP());
        to.setPortNumber(from.getPortNumber());
        to.setUsername(from.getUsername());
        // CD - This was present in the old model but was XmlTransient
        // to.setPassword(from.setPassword());
        to.setSmisProviderIP(from.getSmisProviderIP());
        to.setSmisPortNumber(from.getSmisPortNumber());
        to.setSmisUserName(from.getSmisUserName());
        // to.setSmisPassword();
        to.setSmisUseSSL(from.getSmisUseSSL());
        to.setVersion(from.getVersion());
        to.setUptime(from.getUptime());
        return to;
    }

    public static FCEndpointRestRep map(FCEndpoint from) {
        if (from == null) {
            return null;
        }
        FCEndpointRestRep to = new FCEndpointRestRep();
        mapDataObjectFields(from, to);
        to.setFabricId(from.getFabricId());
        to.setFabricWwn(from.getFabricWwn());
        to.setSwitchName(from.getSwitchName());
        to.setSwitchInterface(from.getSwitchInterface());
        to.setSwitchPortName(from.getSwitchPortName());
        to.setFcid(from.getFcid());
        to.setRemoteNodeName(from.getRemoteNodeName());
        to.setRemotePortName(from.getRemotePortName());
        if (!StringUtils.isEmpty(from.getRemotePortAlias())) {
            to.setRemotePortAlias(from.getRemotePortAlias());
        }
        to.setNetworkDevice(toRelatedResource(ResourceTypeEnum.NETWORK_SYSTEM, from.getNetworkDevice()));
        return to;
    }

    public static FCZoneReferenceRestRep map(FCZoneReference from) {
        if (from == null) {
            return null;
        }
        FCZoneReferenceRestRep to = new FCZoneReferenceRestRep();
        to.setPwwnKey(from.getPwwnKey());
        to.setZoneName(from.getZoneName());
        to.setNetworkSystemUri(from.getNetworkSystemUri());
        to.setFabricId(from.getFabricId());
        to.setVolumeUri(from.getVolumeUri());
        to.setGroupUri(from.getGroupUri());
        to.setCreatedByUser(from.getExistingZone());
        return to;
    }
}
