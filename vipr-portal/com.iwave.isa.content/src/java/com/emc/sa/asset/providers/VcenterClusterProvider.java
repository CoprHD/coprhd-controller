/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

@Component
@AssetNamespace("vipr")
public class VcenterClusterProvider extends BaseAssetOptionsProvider {

    protected List<ClusterRestRep> getClusters(AssetOptionsContext context) {
        debug("getting clusters");
        return api(context).clusters().getByTenant(context.getTenant());
    }

    protected Map<URI,String> getVcenters(AssetOptionsContext context) {
        debug("getting vcenters");
        List<VcenterRestRep> vcenterList =  api(context).vcenters().getByTenant(context.getTenant());
        Map<URI,String> vcMap = new HashMap<URI,String>();
        for (VcenterRestRep vc : vcenterList) {
        	vcMap.put(vc.getId(),vc.getName());
        }
        return vcMap;
    }
    
    @Asset("vcenterCluster")
    public List<AssetOption> getClusterOptions(AssetOptionsContext ctx) {
        debug("getting clusters");
        Collection<ClusterRestRep> clusters = getClusters(ctx);
        List<AssetOption> options = Lists.newArrayList();
        Map<URI,String> vcenterMap = getVcenters(ctx);
        Map<URI,String> dataCenterNameMap = new HashMap<URI,String>();
        Map<URI,String> dataCenterVcenterMap = new HashMap<URI,String>();
        String vcenterName;
        String datacenterName;
        for (ClusterRestRep value : clusters) {
        	if (value.getVcenterDataCenter() != null) {
           		RelatedResourceRep datacenterVal = value.getVcenterDataCenter();
        		if (datacenterVal != null) {
        			if (dataCenterVcenterMap.get(datacenterVal.getId()) == null) {
        		        VcenterDataCenterRestRep restResponse = api(ctx).vcenterDataCenters().get(datacenterVal.getId());
        		        datacenterName = restResponse.getName();
        		        dataCenterNameMap.put(datacenterVal.getId(), datacenterName);
        		        RelatedResourceRep vcenterResp = restResponse.getVcenter();
        		        if (vcenterMap.get(vcenterResp.getId()) == null) {
        		            VcenterRestRep vcenterRep = api(ctx).vcenters().get(vcenterResp);
        		            vcenterMap.put(vcenterResp.getId(), vcenterRep.getName());
        		            vcenterName = vcenterRep.getName();
        		            dataCenterVcenterMap.put(datacenterVal.getId(),vcenterName);
        		        } 
        		        else {
        		        	vcenterName = vcenterMap.get(vcenterResp.getId());
        		        	dataCenterVcenterMap.put(datacenterVal.getId(),vcenterName);
        		        }
        			} 
        			else {
        				datacenterName = dataCenterNameMap.get(datacenterVal.getId());
        				vcenterName = dataCenterVcenterMap.get(datacenterVal.getId());
        			}
        			options.add(createClusterOption(ctx, value, true, vcenterName, datacenterName));
        		} 
        	}
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }    

    @Asset("newVcenterCluster")
    public List<AssetOption> getNewClusterOptions(AssetOptionsContext ctx) {
        debug("getting clusters");
        Collection<ClusterRestRep> clusters = getClusters(ctx);
        List<AssetOption> options = Lists.newArrayList();
        for (ClusterRestRep value : clusters) {
        	if (value.getVcenterDataCenter() == null) {
                options.add(createClusterOption(ctx, value, false, "", ""));
        	}
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }    
    
    protected AssetOption createClusterOption(AssetOptionsContext ctx, ClusterRestRep value, boolean appendLabel, String vcenterName, String datacenterName) {
        String label = value.getName();
        if (appendLabel) {    		
        	label = label + "        (" + vcenterName + " /  " + datacenterName + ")";
        }
        return new AssetOption(value.getId(), label);
    }
    
}
