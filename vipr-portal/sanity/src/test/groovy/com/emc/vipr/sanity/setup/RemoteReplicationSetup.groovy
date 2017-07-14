/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.setup

import com.emc.storageos.model.NamedRelatedResourceRep
import java.net.URI

import com.emc.vipr.sanity.catalog.RemoteReplicationHelper
import com.emc.vipr.sanity.catalog.CatalogServiceHelper
import com.emc.vipr.sanity.catalog.BlockServicesHelper
import com.emc.storageos.model.block.BlockConsistencyGroupCreate

import static com.emc.vipr.sanity.Sanity.*

class RemoteReplicationSetup {

    private static final tenantName = "linux"

    // constants for volume creation
    private final static String VOL_IN_SET_NAME = "rr_vol_in_rr_set"
    private final static String VOL_IN_GRP_NAME = "rr_vol_in_rr_grp"
    private final static String CG_NAME = "cg_for_rr"
    private final static String VOL_IN_CG_NAME = "rr_vol_in_cg"
    private final static String VOL_IN_CG_IN_GRP_NAME = "rr_vol_in_cg_rr_grp"

    private final static String VOL_VARRAY_NAME = "nh"
    private final static String VOL_PROTECTED_VPOOL_NAME = "SBSDK_VPOOL_RR"
    private final static String VOL_PROJECT_NAME = "sanity"
    private final static String VOL_SIZE = "1"
    private final static String SYNC_MODE = "synchronous"
    private final static String ASYNC_MODE = "asynchronous"
    private final static String RR_GROUP = "replicationGroup1_set1 [ACTIVE] (synchronous)"
    private final static String NONE = ""

    static void loadTopology() {
        println "Setting up remote replication"

        // run sbsdk sanity script
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

        // set tenant
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

        createRrVolume(VOL_IN_SET_NAME,VOL_SIZE,VOL_VARRAY_NAME,VOL_PROTECTED_VPOOL_NAME,VOL_PROJECT_NAME,SYNC_MODE,NONE)
        createRrVolume(VOL_IN_GRP_NAME,VOL_SIZE,VOL_VARRAY_NAME,VOL_PROTECTED_VPOOL_NAME,VOL_PROJECT_NAME,SYNC_MODE,RR_GROUP)
        createCg(CG_NAME,VOL_PROJECT_NAME)
        createRrVolume(VOL_IN_CG_NAME,VOL_SIZE,VOL_VARRAY_NAME,VOL_PROTECTED_VPOOL_NAME,VOL_PROJECT_NAME,SYNC_MODE,RR_GROUP)
        createRrVolume(VOL_IN_CG_IN_GRP_NAME,VOL_SIZE,VOL_VARRAY_NAME,VOL_PROTECTED_VPOOL_NAME,VOL_PROJECT_NAME,SYNC_MODE,RR_GROUP)
    }

    static createRrVolume(String name, String size, String varray, String vpool, String project, String rrMode, String rrGroup) {

        // see if vol exists
        def volId = client.blockVolumes().search().byExactName(name).first()?.id
        if (volId != null) {
            println "Volume '" + name + "' exists.  Skipping creation"
            return
        }

        println "Creating Volume '" + name + "'"
        def overrideParameters = [:]
        overrideParameters.name = name
        overrideParameters.size = size
        overrideParameters.virtualArray = RemoteReplicationHelper.getOption(RemoteReplicationHelper.AO_VARRAY,varray)
        def vpoolParams = [(RemoteReplicationHelper.AO_VARRAY):overrideParameters.virtualArray]
        overrideParameters.virtualPool = RemoteReplicationHelper.getOption(RemoteReplicationHelper.AO_VPOOL,vpool,vpoolParams)
        overrideParameters.project = RemoteReplicationHelper.getOption(RemoteReplicationHelper.AO_PROJECT,project)
        overrideParameters.remoteReplicationMode = rrMode
        if (rrGroup == NONE) {
            overrideParameters.remoteReplicationGroup = NONE
        } else {
            def RR_DRIVER_TYPE_ID = RemoteReplicationHelper.getOption(RemoteReplicationHelper.AO_RR_STORAGE_TYPE,RemoteReplicationHelper.RR_DRIVER_TYPE)
            def params = [(RemoteReplicationHelper.AO_RR_STORAGE_TYPE):RR_DRIVER_TYPE_ID]
            def RR_SET_ID = RemoteReplicationHelper.getOption(RemoteReplicationHelper.AO_RR_SETS_FOR_TYPE,RemoteReplicationHelper.RR_SET,params)
            params = [(RemoteReplicationHelper.AO_RR_SETS_FOR_TYPE):RR_SET_ID]
            def RR_GROUP_ID = RemoteReplicationHelper.getOption(RemoteReplicationHelper.AO_RR_GROUPS_FOR_SET,rrGroup,params)
            overrideParameters.remoteReplicationGroup = RR_GROUP_ID
        }
        return CatalogServiceHelper.placeOrder(BlockServicesHelper.CREATE_BLOCK_VOLUME_SERVICE, overrideParameters)
    }

    static deleteRrVolume(String name) {
        def volId = client.blockVolumes().search().byExactName(name).first()?.id
        if (volId == null) {
            println "Volume '" + name + "' exists.  Skipping deletion"
            return
        }
        println "Deleting Volume '" + name + "' [" + volId + "]"
        def overrideParameters = [:]
        overrideParameters.volumes = volId.toString()
        overrideParameters.project = RemoteReplicationHelper.getOption(RemoteReplicationHelper.AO_PROJECT,VOL_PROJECT_NAME)
        return CatalogServiceHelper.placeOrder(BlockServicesHelper.REMOVE_BLOCK_VOLUME_SERVICE, overrideParameters)
    }

    static createCg(String name, String project) {
        def cgId = client.blockConsistencyGroups().search().byExactName(name).first()?.id
        if (cgId != null) {
        println "CG '" + name + "' exists.  Skipping creation"
            return
        }
        println "Creating CG '" + name + "'"
        URI VOL_PROJECT_URI = new URI(RemoteReplicationHelper.getOption(RemoteReplicationHelper.AO_PROJECT,project))
        client.blockConsistencyGroups().create(new BlockConsistencyGroupCreate(name,VOL_PROJECT_URI))
    }

    static deleteCg(String name) {
           def cgId = client.blockConsistencyGroups().search().byExactName(name).first()?.id
        if (cgId == null) {
        println "CG '" + name + "' not present.  Skipping deletion"
            return
        }
        println "Deleting CG '" + name + "'"
        client.blockConsistencyGroups().deactivate(cgId)
    }
}
