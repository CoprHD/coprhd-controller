/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.model.EndpointAliasRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.StringHashMapEntry;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.varray.NetworkCreate;
import com.emc.storageos.model.varray.NetworkEndpointParam;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.varray.NetworkUpdate;
import com.emc.vipr.client.core.filters.NetworkVirtualArrayFilter;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import models.BlockProtocols;
import models.FileProtocols;
import models.TransportProtocols;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.emc.vipr.client.core.util.ResourceUtils.mapNames;
import static com.emc.vipr.client.core.util.ResourceUtils.refIds;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

public class NetworkUtils {
    public static CachedResources<NetworkRestRep> createCache() {
        return new CachedResources<NetworkRestRep>(getViprClient().networks());
    }

    public static NetworkRestRep getNetwork(String id) {
        return getNetwork(uri(id));
    }

    public static NetworkRestRep getNetwork(URI id) {
        try {
            return getViprClient().networks().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<NetworkRestRep> getNetworks() {
        return getViprClient().networks().getAll();
    }

    public static List<NetworkRestRep> getNetworks(List<URI> ids) {
        return getViprClient().networks().getByIds(ids);
    }

    public static Map<URI, String> getNetworkNames() {
        return mapNames(getViprClient().networks().list());
    }

    public static Map<URI, String> getNetworkNamesByVirtualArray(String virtualArrayId) {
        return mapNames(getViprClient().networks().listByVirtualArray(uri(virtualArrayId)));
    }

    public static List<NetworkRestRep> getNetworksByVirtualArray(String virtualArrayId) {
        return getViprClient().networks().getByVirtualArray(uri(virtualArrayId));
    }

    public static List<NetworkRestRep> getNetworksAssignableToVirtualArray(String virtualArrayId) {
        return getViprClient().networks().getAll(new NetworkVirtualArrayFilter(uri(virtualArrayId)).not());
    }

    public static List<IpInterfaceRestRep> getIpInterfaces(URI networkId) {
        return getViprClient().ipInterfaces().getByNetwork(networkId);
    }

    public static List<InitiatorRestRep> getInitiators(URI networkId) {
        return getViprClient().initiators().getByNetwork(networkId);
    }

    public static List<IpInterfaceRestRep> getEligibleIpInterfaces(URI networkId) {
        final Set<URI> idsInNetwork = Sets.newHashSet();
        if (networkId != null) {
            List<NamedRelatedResourceRep> refs = getViprClient().ipInterfaces().listByNetwork(networkId);
            idsInNetwork.addAll(refIds(refs));
        }

        List<URI> allIds = getViprClient().ipInterfaces().listBulkIds();
        return getViprClient().ipInterfaces().getByIds(allIds, new ResourceFilter<IpInterfaceRestRep>() {
            private Set<String> loopbacks = Sets.newHashSet("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

            @Override
            public boolean acceptId(URI id) {
                // Allow any IP interfaces not in the network
                return !idsInNetwork.contains(id);
            }

            @Override
            public boolean accept(IpInterfaceRestRep item) {
                // Skip any loopback interfaces
                return !loopbacks.contains(item.getIpAddress());
            }
        });
    }

    public static Set<String> getSupportedProtocols(NetworkRestRep network) {
        Set<String> protocols = Sets.newHashSet();
        if (TransportProtocols.isFc(network.getTransportType())) {
            protocols.add(BlockProtocols.FC);
        }
        else if (TransportProtocols.isIp(network.getTransportType())) {
            protocols.add(BlockProtocols.iSCSI);
            protocols.add(BlockProtocols.RBD);
            protocols.add(FileProtocols.CIFS);
            protocols.add(FileProtocols.NFS);
            protocols.add(FileProtocols.NFSV4);
        }
        else if (TransportProtocols.isEthernet(network.getTransportType())) {
            protocols.add(BlockProtocols.FCoE);
        } else if (TransportProtocols.isScaleIO(network.getTransportType())) {
            protocols.add(BlockProtocols.ScaleIO);
        }
        return protocols;
    }

    public static List<InitiatorRestRep> getEligibleInitiators(URI networkId, final Set<String> protocols) {
        if (protocols.isEmpty()) {
            return Lists.newArrayList();
        }

        final Set<URI> idsInNetwork = Sets.newHashSet();
        if (networkId != null) {
            List<NamedRelatedResourceRep> refs = getViprClient().initiators().listByNetwork(networkId);
            idsInNetwork.addAll(refIds(refs));
        }
        List<URI> allIds = getViprClient().initiators().listBulkIds();
        return getViprClient().initiators().getByIds(allIds, new ResourceFilter<InitiatorRestRep>() {
            @Override
            public boolean acceptId(URI id) {
                // Allow any initiators not in the network
                return !idsInNetwork.contains(id);
            }

            @Override
            public boolean accept(InitiatorRestRep item) {
                // Allow initiators with the supported protocols
                return protocols.contains(item.getProtocol());
            }
        });
    }

    public static Set<String> getDiscoveredEndpoints(NetworkRestRep networks) {
        Set<String> endpoints = Sets.newHashSet();
        for (StringHashMapEntry entry : networks.getEndpointsDiscovered()) {
            if ("true".equals(entry.getValue())) {
                endpoints.add(entry.getName());
            }
        }
        return endpoints;
    }

    public static Map<String, String> getAliases(NetworkRestRep networks) {
        Map<String, String> aliases = Maps.newHashMap();
        for (EndpointAliasRestRep endpoint : networks.getEndpointsDiscovered()) {
            aliases.put(endpoint.getName(), endpoint.getAlias());
        }
        return aliases;
    }

    public static List<String> getEndPoints(StoragePortRestRep port) {
        String endPointId = StringUtils.substringBefore(port.getPortEndPointId(), ",");
        if (StringUtils.isNotBlank(endPointId)) {
            return Arrays.asList(port.getPortNetworkId(), endPointId);
        }
        else {
            return Arrays.asList(port.getPortNetworkId());
        }
    }

    public static String getEndPoint(IpInterfaceRestRep ipInterface) {
        return ipInterface.getIpAddress();
    }

    public static String getEndPoint(InitiatorRestRep initiator) {
        return initiator.getInitiatorPort();
    }

    public static NetworkRestRep create(NetworkCreate param) {
        return getViprClient().networks().create(param);
    }

    public static NetworkRestRep update(String id, NetworkUpdate param) {
        return update(uri(id), param);
    }

    public static NetworkRestRep update(URI id, NetworkUpdate param) {
        return getViprClient().networks().update(id, param);
    }

    public static void deactivate(URI id) {
        deactivate(id, false);
    }

    public static void deactivate(URI id, boolean force) {
        getViprClient().networks().deactivate(id, force);
    }

    public static void register(URI id) {
        getViprClient().networks().register(id);
    }

    public static void deregister(URI id) {
        getViprClient().networks().deregister(id);
    }

    public static NetworkRestRep addEndpoints(String networkId, Collection<String> endpoints) {
        NetworkEndpointParam param = new NetworkEndpointParam();
        param.setOp("add");
        param.setEndpoints(Lists.newArrayList(endpoints));
        return getViprClient().networks().updateEndpoints(uri(networkId), param);
    }

    public static NetworkRestRep removeEndpoints(String networkId, Collection<String> endpoints) {
        NetworkEndpointParam param = new NetworkEndpointParam();
        param.setOp("remove");
        param.setEndpoints(Lists.newArrayList(endpoints));
        return getViprClient().networks().updateEndpoints(uri(networkId), param);
    }

    public static List<String> getHostEndpoints(Collection<String> ids) {
        List<String> endpoints = Lists.newArrayList();
        for (String id : ids) {
            if (isIpInterfaceId(id)) {
                IpInterfaceRestRep ipInterface = getViprClient().ipInterfaces().get(uri(id));
                if (ipInterface != null) {
                    endpoints.add(ipInterface.getIpAddress());
                }
            }
            else if (isInitiatorId(id)) {
                InitiatorRestRep initiator = getViprClient().initiators().get(uri(id));
                if (initiator != null) {
                    endpoints.add(initiator.getInitiatorPort());
                }
            }
            else if (!isId(id) && StringUtils.isNotBlank(id)) {
                endpoints.add(id);
            }
        }
        return endpoints;
    }

    public static List<String> getStoragePortEndpoints(Collection<String> ids) {
        List<String> endpoints = Lists.newArrayList();
        for (String id : ids) {
            if (isStoragePortId(id)) {
                endpoints.add(id);
            }
        }
        return endpoints;
    }

    private static boolean isId(String id) {
        return StringUtils.startsWith(id, "urn:");
    }

    private static boolean isIpInterfaceId(String id) {
        return isId(id) && StringUtils.contains(id, "IpInterface");
    }

    private static boolean isInitiatorId(String id) {
        return isId(id) && StringUtils.contains(id, "Initiator");
    }

    private static boolean isStoragePortId(String id) {
        return isId(id) && StringUtils.contains(id, "StoragePort");
    }
}
