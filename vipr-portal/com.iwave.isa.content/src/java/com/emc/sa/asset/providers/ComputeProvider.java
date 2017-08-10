/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

@Component
@AssetNamespace("vipr")
public class ComputeProvider extends BaseAssetOptionsProvider {

    protected List<ComputeVirtualPoolRestRep> getComputeVirtualPools(AssetOptionsContext context) {
        debug("getting virtual compute pools");
        return api(context).computeVpools().getAll();
    }

    @Asset("computeVirtualPool")
    public List<AssetOption> getComputeVirtualPoolOptions(AssetOptionsContext ctx) {
        debug("getting compute virtual pools");
        Collection<ComputeVirtualPoolRestRep> computeVirtualPools = getComputeVirtualPools(ctx);
        List<AssetOption> options = Lists.newArrayList();
        for (ComputeVirtualPoolRestRep value : computeVirtualPools) {
            options.add(createComputeVirtualPoolOption(ctx, value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("computeVirtualPool")
    @AssetDependencies({ "blockVirtualArray" })
    public List<AssetOption> getComputeVirtualPoolForVirtualArray(AssetOptionsContext ctx, URI virtualArray) {
        debug("getting compute virtual pools");

        Collection<ComputeVirtualPoolRestRep> computeVirtualPools =
                api(ctx).computeVpools().getByVirtualArrayAndTenant(virtualArray,ctx.getTenant());
        List<AssetOption> options = Lists.newArrayList();
        for (ComputeVirtualPoolRestRep value : computeVirtualPools) {
            options.add(createComputeVirtualPoolOption(ctx, value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected AssetOption createComputeVirtualPoolOption(AssetOptionsContext ctx, ComputeVirtualPoolRestRep value) {
        String label = value.getName();
        return new AssetOption(value.getId(), label);
    }

    @Asset("computeVirtualPoolByHostComputeSystem")
    @AssetDependencies({"hostsWithCEAndSPByVblockCluster"})
    public List<AssetOption> getComputeVirtualPoolByHostComputeSystem(AssetOptionsContext context, URI hostID) {
        debug("get compute virtual pool that has blades from computesystem that host belongs to.");
        return getCVPByHostCS(context, hostID);
    }

    @Asset("associateCVPByHostComputeSystem")
    @AssetDependencies({"releasedHostsWithSPByVblockCluster"})
    public List<AssetOption> getCVPByHostComputeSystem(AssetOptionsContext context, URI hostID) {
        debug("get compute virtual pool that has blades from computesystem that released host belongs to.");
        return getCVPByHostCS(context, hostID);
    }

    private List<AssetOption> getCVPByHostCS(AssetOptionsContext context, URI hostID) {
        debug("get compute virtual pool that has blades from computesystem that host belongs to.");
        HostRestRep hostRep = api(context).hosts().get(hostID);
        ComputeVirtualPoolRestRep hostCVP = null;
        if (hostRep.getComputeVirtualPool() != null) {
            hostCVP = api(context).computeVpools().get(hostRep.getComputeVirtualPool());
        }
        List<AssetOption> options = Lists.newArrayList();
        List<ComputeElementRestRep> ceListRestRep = api(context).computeSystems()
                .getComputeElements(hostRep.getComputeSystem().getId());
        List<NamedRelatedResourceRep> cvpList = api(context).computeVpools().list();
        for (NamedRelatedResourceRep namedRelatedResourceRep : cvpList) {
            ComputeVirtualPoolRestRep cvpRestRep = api(context).computeVpools().get(namedRelatedResourceRep);
            boolean isMatched = false;
            List<RelatedResourceRep> cvpArrays = cvpRestRep.getVirtualArrays();
            //ingested host wont have a CVP so let them through this check
            if (hostCVP != null) {
                List<RelatedResourceRep> hostArrays = hostCVP.getVirtualArrays();
                // list only those cvps whose varrays matches with that of the
                // host varrays.
                boolean hasCommonVArrays = CollectionUtils.containsAny(cvpArrays, hostArrays);
                if (!hasCommonVArrays) {
                    continue;
                }
            }
            for (RelatedResourceRep matchedBlades : cvpRestRep.getMatchedComputeElements()) {
                for (ComputeElementRestRep ce : ceListRestRep) {
                    if (ce.getId().equals(matchedBlades.getId())) {
                        options.add(createComputeVirtualPoolOption(context, cvpRestRep));
                        isMatched = true;
                        break;
                    }
                }
                if (isMatched) {
                    break;
                }
            }

        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("cvpMatchedBlades")
    @AssetDependencies({ "computeVirtualPoolByHostComputeSystem", "hostsWithCEAndSPByVblockCluster" })
    public List<AssetOption> getMatchedComputeElementsForHost(AssetOptionsContext context, URI newCVPId, URI hostID) {
        debug("get matched blades for selected compute virtual pool");
        return getMatchingCEsForHost(context, newCVPId, hostID);
    }

    @Asset("associateCVPMatchedBlades")
    @AssetDependencies({ "associateCVPByHostComputeSystem", "releasedHostsWithSPByVblockCluster" })
    public List<AssetOption> getMatchedCEsForHost(AssetOptionsContext context, URI newCVPId, URI hostID) {
        debug("get matched blades for selected compute virtual pool");
        return getMatchingCEsForHost(context, newCVPId, hostID);
    }

    private List<AssetOption> getMatchingCEsForHost(AssetOptionsContext context, URI newCVPId, URI hostID) {
        HostRestRep hostRep = api(context).hosts().get(hostID);
        NamedRelatedResourceRep csRep = hostRep.getComputeSystem();
        List<ComputeElementRestRep> ceListRestRep = api(context).computeSystems().getComputeElements(csRep.getId());
        ComputeVirtualPoolRestRep cvpRestRep = api(context).computeVpools().get(newCVPId);
        List<AssetOption> options = Lists.newArrayList();
        for (ComputeElementRestRep ce : ceListRestRep) {
            for (RelatedResourceRep matchedBlade : cvpRestRep.getMatchedComputeElements()) {
                if (matchedBlade != null && ce.getId().equals(matchedBlade.getId())) {
                    ComputeElementRestRep matchedCE = api(context).computeElements().get(matchedBlade);
                    if (matchedCE != null && matchedCE.getAvailable()) {
                        if (hostRep.getComputeElement() == null) {
                            options.add(createComputeElementOption(context, matchedCE, csRep.getName()));
                        } else if (!hostRep.getComputeElement().getId().equals(matchedCE.getId())) {
                            options.add(createComputeElementOption(context, matchedCE, csRep.getName()));
                        }
                    }
                }
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    private AssetOption createComputeElementOption(AssetOptionsContext ctx, ComputeElementRestRep value,
            String computeSystemName) {
        StringBuilder sb = new StringBuilder();
        if (null != computeSystemName) {
            sb.append("[ ").append(computeSystemName).append(" ] ");
        }
        sb.append(value.getName());
        return new AssetOption(value.getId(), sb.toString());
    }
    
    @Asset("templatesForComputeVpool")
    @AssetDependencies({"computeVirtualPool", "blockVirtualArray"})
    public List<AssetOption> getTemplatesForCVP(AssetOptionsContext context, URI cvpId, URI varrayId) {
       List<AssetOption> options = Lists.newArrayList();
       ComputeVirtualPoolRestRep cvpRestRep = api(context).computeVpools().get(cvpId);
       List<ComputeSystemRestRep> computeSystemsForVarray = api(context).varrays().getComputeSystems(varrayId);

       Set<URI> computeSystemsForCVP = new HashSet<URI>();
       for (RelatedResourceRep matchedBlade : cvpRestRep.getMatchedComputeElements()) {
           if (matchedBlade!=null){
               ComputeElementRestRep matchedCE = api(context).computeElements().get(matchedBlade);
               computeSystemsForCVP.add(matchedCE.getComputeSystem().getId());
           }
       }
       
       for (ComputeSystemRestRep computeSystem : computeSystemsForVarray) {
            if (computeSystemsForCVP.contains(computeSystem.getId())){
                //add templates from this CS to options list
                List<NamedRelatedResourceRep> spts = computeSystem.getServiceProfileTemplates();
                for (NamedRelatedResourceRep spt : spts){
                    options.add(createServiceProfileTemplateOption(context,spt, computeSystem.getName()));
                }
            }
       }


       return options;
   }
   
    private AssetOption createServiceProfileTemplateOption(AssetOptionsContext ctx, NamedRelatedResourceRep value, String computeSystemName) {
        StringBuilder sb = new StringBuilder();
        if (null != computeSystemName) {
            sb.append("[ ").append(computeSystemName).append(" ] ");
        }
        sb.append(value.getName());
        return new AssetOption(value.getId(), sb.toString());
    }

}
