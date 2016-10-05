/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.mapById;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.model.host.ArrayAffinityHostParam;
import com.emc.storageos.model.host.HostCreateParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.storageos.model.host.InitiatorCreateParam;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.IpInterfaceCreateParam;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.host.PairedInitiatorCreateParam;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.core.filters.HostTypeFilter;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Sets;

public class HostUtils {
    public static List<HostRestRep> getHostsExcludingEsx() {
        return getViprClient().hosts().getByUserTenant(HostTypeFilter.ESX.not());
    }

    public static List<HostRestRep> getHosts(String tenantId) {
        return getViprClient().hosts().getByTenant(uri(tenantId));
    }

    public static List<HostRestRep> getHostsByDataCenter(VcenterDataCenterRestRep vcenterDataCenter) {
        return getViprClient().hosts().getByDataCenter(id(vcenterDataCenter));
    }

    public static List<HostRestRep> getHostsByCluster(ClusterRestRep cluster) {
        return getViprClient().hosts().getByCluster(id(cluster));
    }

    public static CachedResources<HostRestRep> createCache() {
        return new CachedResources<HostRestRep>(getViprClient().hosts());
    }

    public static HostRestRep getHost(URI id) {
        try {
            return (id != null) ? getViprClient().hosts().get(id) : null;
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<InitiatorRestRep> getInitiators(URI hostId) {
        return getViprClient().initiators().getByHost(hostId);
    }

    public static void deactivateInitiators(URI hostId) {
        for (InitiatorRestRep initiator : getInitiators(hostId)) {
            deactivateInitiator(initiator);
        }
    }

    public static Task<InitiatorRestRep> createInitiator(URI hostId, InitiatorCreateParam hostInitiatorCreateParam) {
        return getViprClient().initiators().create(hostId, hostInitiatorCreateParam);
    }

    public static Task<InitiatorRestRep> createInitiatorPair(URI hostId, PairedInitiatorCreateParam hostInitiatorCreateParam) {
        return getViprClient().initiators().createPair(hostId, hostInitiatorCreateParam);
    }

    public static IpInterfaceRestRep createIpInterface(URI hostId, IpInterfaceCreateParam ipInterfaceCreateParam) {
        return getViprClient().ipInterfaces().create(hostId, ipInterfaceCreateParam);
    }

    public static void deactivateIpInterface(IpInterfaceRestRep ipInterface) {
        getViprClient().ipInterfaces().deactivate(id(ipInterface));
    }

    public static Task<InitiatorRestRep> deactivateInitiator(InitiatorRestRep initiator) {
        return getViprClient().initiators().deactivate(id(initiator));
    }

    public static void deactivateIpInterfaces(URI hostId) {
        for (IpInterfaceRestRep ipInterface : getIpInterfaces(hostId)) {
            deactivateIpInterface(ipInterface);
        }
    }

    public static List<IpInterfaceRestRep> getIpInterfaces(URI hostId) {
        return getViprClient().ipInterfaces().getByHost(hostId);
    }

    public static Task<HostRestRep> createHost(HostCreateParam hostCreateParam, boolean validateConnection) {
        return getViprClient().tenants().createHost(hostCreateParam.getTenant(), hostCreateParam, validateConnection);
    }

    public static Task<HostRestRep> updateHost(URI hostId, HostUpdateParam hostUpdateParam, boolean validateConnection) {
        return getViprClient().hosts().update(hostId, hostUpdateParam, validateConnection);
    }

    public static Task<HostRestRep> deactivate(URI hostId) {
        return getViprClient().hosts().deactivate(hostId);
    }

    public static Task<HostRestRep> deactivate(URI hostId, boolean detachStorage) {
        return getViprClient().hosts().deactivate(hostId, detachStorage);
    }

    public static Task<HostRestRep> discover(URI hostId) {
        return getViprClient().hosts().discover(hostId);
    }

    public static Tasks<HostRestRep> discoverHostArrayAffinity(ArrayAffinityHostParam param) {
        return getViprClient().hosts().discoverHostArrayAffinity(param);
    }

    public static Set<URI> getDataCenterIds(List<HostRestRep> hosts) {
        Set<URI> dataCenterIds = Sets.newHashSet();
        if (hosts != null) {
            for (HostRestRep host : hosts) {
                if (host != null && host.getvCenterDataCenter() != null) {
                    dataCenterIds.add(host.getvCenterDataCenter().getId());
                }
            }
        }
        return dataCenterIds;
    }

    public static Map<URI, VcenterDataCenterRestRep> getVcenterDataCenters(Collection<URI> dataCenterIds) {
        return mapById(getViprClient().vcenterDataCenters().getByIds(dataCenterIds));
    }
}
