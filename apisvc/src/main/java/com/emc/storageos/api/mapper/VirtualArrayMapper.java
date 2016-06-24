/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDiscoveredDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.model.EndpointAliasRestRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.StringHashMapEntry;
import com.emc.storageos.model.adapters.StringMapAdapter;
import com.emc.storageos.model.varray.BlockSettings;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.varray.ObjectSettings;
import com.emc.storageos.model.varray.VirtualArrayRestRep;

public class VirtualArrayMapper {
    public static VirtualArrayRestRep map(VirtualArray from) {
        if (from == null) {
            return null;
        }
        VirtualArrayRestRep to = new VirtualArrayRestRep();
        mapDataObjectFields(from, to);

        if (from.getAutoSanZoning() != null || from.getNoNetwork() != null) {
            BlockSettings blockSettings = new BlockSettings();
            if(from.getAutoSanZoning() != null) {
            	blockSettings.setAutoSanZoning(from.getAutoSanZoning());
            }

            if(from.getNoNetwork() != null) {
            	blockSettings.setNoNetwork(from.getNoNetwork());
            }
            to.setBlockSettings(blockSettings);
        }

        ObjectSettings objectSettings = null;
        if (from.getProtectionType() != null || from.getDeviceRegistered() != null) {
            objectSettings = new ObjectSettings();
            if (from.getDeviceRegistered() != null) {
                objectSettings.setDeviceRegistered(from.getDeviceRegistered());
            }
            if (from.getProtectionType() != null) {
                objectSettings.setProtectionType(from.getProtectionType());
            }
        }
        if (objectSettings != null) {
            to.setObjectSettings(objectSettings);
        }

        return to;
    }

    public static NetworkRestRep map(Network from) {
        if (from == null) {
            return null;
        }
        NetworkRestRep to = new NetworkRestRep();
        mapDiscoveredDataObjectFields(from, to);
        StringSet assignedVirtualArrays = from.getAssignedVirtualArrays();
        if ((assignedVirtualArrays != null) && (assignedVirtualArrays.size() == 1)) {
            to.setVirtualArray(toRelatedResource(ResourceTypeEnum.VARRAY,
                    URI.create(assignedVirtualArrays.iterator().next())));
        }
        to.setTransportType(from.getTransportType());
        to.setEndpoints(from.retrieveEndpoints());

        List<StringHashMapEntry> endpointsMap = new StringMapAdapter().marshal(from.getEndpointsMap());

        /*
         * Translated network endpoint to its corresponded EndpointAliasRestRep. At this point, only
         * "name" and "value" attribute are filled. "alias" attribute will filled by the caller.
         */
        to.setEndpointsDiscovered(new ArrayList<EndpointAliasRestRep>());
        for (StringHashMapEntry endpointEntry : endpointsMap) {
            to.getEndpointsDiscovered().add(new EndpointAliasRestRep(endpointEntry.getName(), endpointEntry.getValue()));
        }

        to.setFabricId(from.getNativeId());
        to.setDiscovered(from.getDiscovered());
        to.setNetworkSystems(from.getNetworkSystems());
        to.setRegistrationStatus(from.getRegistrationStatus());
        to.setAssignedVirtualArrays(assignedVirtualArrays);
        to.setConnectedVirtualArrays(from.getConnectedVirtualArrays());
        to.setRoutedNetworks(from.getRoutedNetworks());
        return to;
    }
}
