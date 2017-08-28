/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import static com.emc.vipr.client.core.filters.CompatibilityFilter.INCOMPATIBLE;
import static com.emc.vipr.client.core.filters.RegistrationFilter.REGISTERED;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.HostTypeFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

@Component
@AssetNamespace("vipr")
public class HostProvider extends BaseHostProvider {

    public static List<URI> getHostIds(ViPRCoreClient client, URI hostOrClusterId) {
        if (BlockStorageUtils.isHost(hostOrClusterId)) {
            return Lists.newArrayList(hostOrClusterId);
        } else {
            return ResourceUtils.refIds(client.hosts().listByCluster(hostOrClusterId));
        }
    }

    public List<HostRestRep> getHosts(AssetOptionsContext context, URI clusterId) {
        return api(context).hosts().getByCluster(clusterId);
    }

    protected List<HostRestRep> getHosts(AssetOptionsContext context) {
        debug("getting hosts");
        return api(context).hosts().getByTenant(context.getTenant(), REGISTERED.and(INCOMPATIBLE.not()));
    }

    protected List<HostRestRep> getLinuxHosts(AssetOptionsContext context) {
        debug("getting linuxHosts");
        return api(context).hosts().getByTenant(context.getTenant(), HostTypeFilter.LINUX.and(REGISTERED).and(INCOMPATIBLE.not()));
    }

    protected List<HostRestRep> getLinuxFileHosts(AssetOptionsContext context) {
        debug("getting linuxFileHosts");
        return api(context).hosts().getByTenant(context.getTenant(), HostTypeFilter.LINUX.and(REGISTERED));
    }

    protected List<HostRestRep> getWindowsHosts(AssetOptionsContext context) {
        debug("getting windowsHosts");
        return api(context).hosts().getByTenant(context.getTenant(), HostTypeFilter.WINDOWS.and(REGISTERED).and(INCOMPATIBLE.not()));
    }

    protected List<HostRestRep> getAixHosts(AssetOptionsContext context) {
        debug("getting aixHosts");
        return api(context).hosts().getByTenant(context.getTenant(), HostTypeFilter.AIX.and(REGISTERED).and(INCOMPATIBLE.not()));
    }

    protected List<HostRestRep> getHpuxHosts(AssetOptionsContext context) {
        debug("getting hpuxHosts");
        return api(context).hosts().getByTenant(context.getTenant(), HostTypeFilter.HPUX.and(REGISTERED).and(INCOMPATIBLE.not()));
    }

    @Asset("host")
    public List<AssetOption> getHostOptions(AssetOptionsContext context) {
        debug("getting hosts");
        return createHostOptions(context, getHosts(context));
    }

    @Asset("linuxHost")
    public List<AssetOption> getLinuxHostOptions(AssetOptionsContext context) {
        debug("getting linuxHosts");
        return createHostOptions(context, getLinuxHosts(context));
    }

    @Asset("linuxFileHost")
    public List<AssetOption> getLinuxFileHostOptions(AssetOptionsContext context) {
        debug("getting linuxFileHosts");
        return createFileHostOptions(context, getLinuxFileHosts(context));
    }

    @Asset("windowsHost")
    public List<AssetOption> getWindowsHostOptions(AssetOptionsContext context) {
        debug("getting windowsHosts");
        return createHostOptions(context, getWindowsHosts(context));
    }

    @Asset("aixHost")
    public List<AssetOption> getAixHostOptions(AssetOptionsContext context) {
        debug("getting aixHosts");
        return createHostOptions(context, getAixHosts(context));
    }

    @Asset("hpuxHost")
    public List<AssetOption> getHpuxHostOptions(AssetOptionsContext context) {
        debug("getting hpuxHosts");
        return createHostOptions(context, getHpuxHosts(context));
    }

    @Asset("host")
    @AssetDependencies({ "blockStorageType" })
    public List<AssetOption> getHostsOrClusterOptions(AssetOptionsContext context, String storageType) {
        return getHostOrClusterOptions(context, getHosts(context), storageType);
    }

    @Asset("linuxHost")
    @AssetDependencies({ "blockStorageType" })
    public List<AssetOption> getLinuxHostsOrClusterOptions(AssetOptionsContext context, String storageType) {
        return getHostOrClusterOptions(context, getLinuxHosts(context), storageType);
    }

    @Asset("windowsHost")
    @AssetDependencies({ "blockStorageType" })
    public List<AssetOption> getWindowsHostsOrClusterOptions(AssetOptionsContext context, String storageType) {
        return getHostOrClusterOptions(context, getWindowsHosts(context), storageType);
    }

    @Asset("hostsByCluster")
    @AssetDependencies({ "cluster" })
    public List<AssetOption> getHostsByCluster(AssetOptionsContext context, URI clusterId) {
        debug("getting hosts");
        return createHostOptions(context, getHosts(context, clusterId));
    }

    @Asset("hostsByVblockCluster")
    @AssetDependencies({ "vblockCluster" })
    public List<AssetOption> getVblockHostsByCluster(AssetOptionsContext context, URI clusterId) {
        debug("getting all hosts for a vblock cluster");
        return createHostOptions(context, getHosts(context, clusterId));
    }

    @Asset("hostsWithCEAndSPByVblockCluster")
    @AssetDependencies({ "vblockCluster" })
    public List<AssetOption> getHostsWithCEAndSP(AssetOptionsContext context, URI clusterId) {
        debug("getting all hosts that have a CE and SP association from a vblock cluster");
        List<HostRestRep> hostList = getHosts(context, clusterId);
        List<AssetOption> options = Lists.newArrayList();
        for (HostRestRep host : hostList) {
            if (host.getComputeElement() != null &&
                (!NullColumnValueGetter.isNullURI(host.getComputeElement().getId())
                        && !NullColumnValueGetter.isNullValue(host.getServiceProfileName()))) {
                options.add(createHostCVPOption(context, host, false));
            }
        }
        return options;
    }

    @Asset("releasedHostsWithSPByVblockCluster")
    @AssetDependencies({ "vblockCluster" })
    public List<AssetOption> getReleasedHostsWithSP(AssetOptionsContext context, URI clusterId) {
        debug("getting all hosts having only SP association from a vblock cluster");
        List<HostRestRep> hostList = getHosts(context, clusterId);
        List<AssetOption> options = Lists.newArrayList();
        for (HostRestRep host : hostList) {
            if (!NullColumnValueGetter.isNullValue(host.getServiceProfileName()) && host.getComputeElement() == null) {
                options.add(createHostCVPOption(context, host, true));
            }
        }
        return options;
    }

    private AssetOption createHostCVPOption(AssetOptionsContext ctx, HostRestRep host, boolean isReleasedHost) {
        String discoveryMessage = getDiscoveryError(host);
        String hostCVP = null;
        String hostCE = null;
        if (host.getComputeVirtualPool() != null) {
            ComputeVirtualPoolRestRep cvp = api(ctx).computeVpools().get(host.getComputeVirtualPool());
            if (cvp != null) {
                hostCVP = cvp.getName();
            }
        }
        if (host.getComputeElement() != null) {
            ComputeElementRestRep ce = api(ctx).computeElements().get(host.getComputeElement());
            if (ce != null) {
                hostCE = ce.getName();
            }
        }
        String label = host.getName();
        if (StringUtils.isNotBlank(hostCVP)) {
            if(isReleasedHost) {
                label = getMessage("host.withOldComputeVirtualPool", label, hostCVP);
            } else {
                label = getMessage("host.withComputeVirtualPool", label, hostCVP);
            }
        }
        if (StringUtils.isNotBlank(hostCE)) {
            label = getMessage("host.withComputeElement", label, hostCE);
        }
        if (discoveryMessage != null) {
            label = getMessage("host.withDiscovery", label, discoveryMessage);
        }
        return new AssetOption(host.getId(), label);
    }

}