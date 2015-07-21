/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import util.BourneUtil;

import com.emc.storageos.model.block.tier.AutoTieringPolicyRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.DefaultResourceFilter;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

public class AutoTierPolicyNamesCall extends ViPRCall<List<String>> {
    private Collection<URI> varrayIds;
    private String provisioningType;
    private boolean namesOnly;
    private String systemType;

    public AutoTierPolicyNamesCall(Collection<URI> varrayIds, String provisioningType, String systemType, boolean uniqueNames) {
        this(BourneUtil.getViprClient(), varrayIds, provisioningType, systemType, uniqueNames);
    }

    public AutoTierPolicyNamesCall(ViPRCoreClient client, Collection<URI> varrayIds, String provisioningType, String systemType,
            boolean uniqueNames) {
        super(client);
        this.varrayIds = varrayIds;
        this.provisioningType = provisioningType;
        // Whether to return the policy name only, or to return the GUID (which includes storage system)
        this.namesOnly = uniqueNames;
        this.systemType = systemType;
    }

    @Override
    public List<String> call() {
        List<String> names = Lists.newArrayList();
        ResourceFilter<AutoTieringPolicyRestRep> filter = null;
        if (systemType != null) {
            filter = new SystemTypeFilter();
        }
        
        Collection<AutoTieringPolicyRestRep> policies = 
                client.autoTierPolicies().getByVirtualArrays(varrayIds, provisioningType, Boolean.valueOf(namesOnly), filter);
         
        if (namesOnly) {
            names.addAll(ResourceUtils.names(policies));
        }
        else {
            for (AutoTieringPolicyRestRep policy : policies) {
                names.add(policy.getNativeGuid());
            }
        }
        
        return names;
    }
    
    class SystemTypeFilter extends DefaultResourceFilter<AutoTieringPolicyRestRep> {
        @Override
        public boolean accept(AutoTieringPolicyRestRep item) {
            return StringUtils.equalsIgnoreCase(item.getSystemType(), systemType);
        }
    }
}
