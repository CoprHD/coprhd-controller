/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.VirtualArrayMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.model.EndpointAliasRestRep;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.util.NetworkUtil;
import com.google.common.base.Function;

public class MapNetwork implements Function<Network, NetworkRestRep> {
    private static final Logger logger = LoggerFactory.getLogger(MapNetwork.class);
    private static final MapNetwork INSTANCE = new MapNetwork();

    private static final String REMOTE_PORT_NAME = "remotePortName";
    private static final String REMOTE_PORT_ALIAS = "remotePortAlias";

    public static MapNetwork getInstance() {
        return INSTANCE;
    }

    private MapNetwork() {
    }

    @Override
    public NetworkRestRep apply(Network resource) {
        return VirtualArrayMapper.map(resource);
    }

    /**
     * Map <code>Network</code> to <code>NetworkRestRep</code> object. Since <code>remote_port_alias</code> is not readily available, it
     * must be read from corresponded <code>FCEndpoint</code>.
     * 
     * @param network
     * @param dbClient
     * @return
     */
    public static NetworkRestRep toNetworkRestRep(Network network, DbClient dbClient) {
        NetworkRestRep networkRestRep = MapNetwork.getInstance().apply(network);
        List<EndpointAliasRestRep> endpoints = networkRestRep.getEndpointsDiscovered();
        if (endpoints.size() == 0 || !network.getDiscovered() ||
                !network.getTransportType().equalsIgnoreCase(TransportType.FC.name())) {
            return networkRestRep;
        }
        try {
            String fabricWwn = NetworkUtil.getNetworkWwn(network);
            if (fabricWwn != null && !fabricWwn.isEmpty()) {
                Map<String, EndpointAliasRestRep> aliasMap = new HashMap<String, EndpointAliasRestRep>();
                for (EndpointAliasRestRep endpointAliasRestRep : endpoints) {
                    aliasMap.put(endpointAliasRestRep.getName(), endpointAliasRestRep);
                }
                URIQueryResultList uriList = new URIQueryResultList();
                dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getFCEndpointByFabricWwnConstraint(NetworkUtil.getNetworkWwn(network)), uriList);
                Set<String> fields = new HashSet<String>();
                fields.add(REMOTE_PORT_NAME);
                fields.add(REMOTE_PORT_ALIAS);
                Iterator<FCEndpoint> iterator = dbClient.queryIterativeObjectFields(FCEndpoint.class,
                        fields, uriList);
                while (iterator.hasNext()) {
                    FCEndpoint fc = iterator.next();
                    if (fc != null && !StringUtils.isEmpty(fc.getRemotePortAlias())) {
                        String portWWN = fc.getRemotePortName();
                        EndpointAliasRestRep restRep = aliasMap.get(portWWN);
                        if (restRep != null) {
                            logger.debug("Found alias {} for WWN {} in network {}", new Object[] {
                                    fc.getRemotePortAlias(), portWWN, networkRestRep.getId() });
                            restRep.setAlias(fc.getRemotePortAlias());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Unable to display alias information because an error encountered while getting" +
                    " alias information for network " + networkRestRep.getId(), ex);
        }

        return networkRestRep;
    }
}
