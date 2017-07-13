/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.setup

import com.emc.storageos.model.NamedRelatedResourceRep
import java.net.URI

import static com.emc.vipr.sanity.Sanity.*

class RemoteReplicationSetup {

    static void loadTopology() {

        println "Setting up remote replication"

        def helper = new com.emc.vipr.sanity.catalog.RemoteReplicationHelper()
        if (!helper.topologyLoadedTest()) { 
            println "Running ViPR sanity script for SB SDK to load topology for tests..."
            String workspace = System.getenv("CatalogWorkspace")
            String sanityConf = System.getenv("CatalogSanityConf")
            def viprSanityScript = workspace + "/tools/tests/sanity" 
            def viprSanityTestCategory = "sbsdk_remote_replication" 
            def host = "localhost" 
            ProcessBuilder pb = new ProcessBuilder().inheritIO() 
            pb.command(viprSanityScript,sanityConf,host,viprSanityTestCategory) 
            println "Running ViPR sanity for SB SDK Remote Replication: " + pb.command().join(" ") 
            Process p = pb.start()
            p.waitFor()
            println "Finished ViPR sanity for SB SDK Remote Replication.  Topology loaded"
        }

        URI currentTenant = client.tenants().currentId()
        List<NamedRelatedResourceRep> tenants = client.tenants().listSubtenants(currentTenant)
        for (NamedRelatedResourceRep tenant : tenants) { 
            if(tenant.getName().equals("linux")) { 
                helper.setTenant(tenant.getId());
            }
        }
        if (helper.getTenant() == null) { 
            println "FAILED TO LOCATE TENANT 'linux'"
        }
        
        // create pair in set
        
        // create pair in group
        
        // create cg
        
        // create pair in cg
        
        // create pair in cg that's in group
        
        
        
        
        
        
    }
}
