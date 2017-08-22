/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.setup

import com.emc.storageos.model.NamedRelatedResourceRep
import java.net.URI

import static com.emc.vipr.sanity.Sanity.*
import static com.emc.vipr.sanity.catalog.RemoteReplicationHelper.*
import static com.emc.vipr.sanity.catalog.CatalogServiceHelper.*
import static com.emc.vipr.sanity.catalog.BlockServicesHelper.*

import static com.emc.vipr.sanity.Sanity.printDebug
import static com.emc.vipr.sanity.Sanity.printVerbose
import static com.emc.vipr.sanity.Sanity.printInfo
import static com.emc.vipr.sanity.Sanity.printWarn
import static com.emc.vipr.sanity.Sanity.printError

import com.emc.storageos.model.block.BlockConsistencyGroupCreate
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam

class RemoteReplicationSetup {

    private static final tenantName = "linux"

    // constants for ViPR Sanity SB-SDK (used to load topology)
    private final static String VIPR_SANITY_SCRIPT = "/tools/tests/sanity"
    private final static String VIPR_SANITY_TEST_CATEGORY = "sbsdk_remote_replication"
    private final static String VIPR_SANITY_HOST = "localhost"

    // constants for volume creation
    private final static String VOL_IN_SET_NAME = "rr_vol_in_rr_set"
    private final static String VOL_IN_GRP_NAME = "rr_vol_in_rr_grp"
    private final static String CG_NAME_FOR_GRP = "cg_for_rr_grp"
    private final static String CG_NAME_FOR_SET = "cg_for_rr_set"
    private final static String VOL_IN_CG_NAME = "rr_vol_in_cg"
    private final static String VOL_IN_CG_IN_GRP_NAME = "rr_vol_in_cg_rr_grp"

    private final static String VOL_VARRAY_NAME = "nh"
    private final static String VOL_PROTECTED_VPOOL_NAME = "SBSDK_VPOOL_RR"
    private final static String VOL_PROJECT_NAME = "sanity"
    private final static String VOL_SIZE = "1"
    private final static String SYNC_MODE = "synchronous"
    private final static String ASYNC_MODE = "asynchronous"
    private final static String RR_GROUP = "replicationGroup1_set1 [ACTIVE] (synchronous)"
    private final static String RR_GROUP2 = "replicationGroup2_set1 [ACTIVE] (synchronous)"
    private final static String NONE = ""

    static void loadTopologyViprSanity() {
        // run sbsdk sanity script
        if (!topologyLoadedViprSanityTest()) {
            println "Running ViPR sanity script for SB SDK to load topology for tests..."
            String sanityConf = System.getenv("CatalogSanityConf") //set in startup shell script (catalog_sanity.sh)
            String workspace = System.getenv("CatalogWorkspace")   //set in startup shell script (catalog_sanity.sh)
            String fullWorkspacePath =  workspace + VIPR_SANITY_SCRIPT
            ProcessBuilder pb = new ProcessBuilder().inheritIO() 
            pb.command(fullWorkspacePath,sanityConf,VIPR_SANITY_HOST,VIPR_SANITY_TEST_CATEGORY)
            println "Running ViPR sanity for SB SDK Remote Replication: " + pb.command().join(" ") 
            Process p = pb.start()
            p.waitFor()
            println "Finished ViPR sanity for SB SDK Remote Replication.  Topology loaded"
        }
    }

    static void loadTopology() {
        if (!topologyLoadedTest()) {
            println "Setting up remote replication"
            // set tenant
            URI currentTenant = client.tenants().currentId()
            List<NamedRelatedResourceRep> tenants = client.tenants().listSubtenants(currentTenant)
            for (NamedRelatedResourceRep tenant : tenants) {
                if(tenant.getName().equals("linux")) {
                    setTenant(tenant.getId());
                }
            }
            if (getTenant() == null) {
                println "FAILED TO LOCATE TENANT 'linux'"
            }

            enableVpoolForCg(RR_VPOOL)
            createCg(CG_NAME_FOR_GRP,VOL_PROJECT_NAME)
            createCg(CG_NAME_FOR_SET,VOL_PROJECT_NAME)

            createRrVolume(VOL_IN_SET_NAME,VOL_SIZE,VOL_VARRAY_NAME,VOL_PROTECTED_VPOOL_NAME,VOL_PROJECT_NAME,SYNC_MODE,NONE,NONE)
            createRrVolume(VOL_IN_GRP_NAME,VOL_SIZE,VOL_VARRAY_NAME,VOL_PROTECTED_VPOOL_NAME,VOL_PROJECT_NAME,SYNC_MODE,RR_GROUP,NONE)
            createRrVolume(VOL_IN_CG_NAME,VOL_SIZE,VOL_VARRAY_NAME,VOL_PROTECTED_VPOOL_NAME,VOL_PROJECT_NAME,SYNC_MODE,NONE,CG_NAME_FOR_SET)
            createRrVolume(VOL_IN_CG_IN_GRP_NAME,VOL_SIZE,VOL_VARRAY_NAME,VOL_PROTECTED_VPOOL_NAME,VOL_PROJECT_NAME,SYNC_MODE,RR_GROUP2,CG_NAME_FOR_GRP)
        }
    }

    static clearTopology() {
        deleteRrVolume(VOL_IN_SET_NAME)
        deleteRrVolume(VOL_IN_GRP_NAME)
        deleteRrVolume(VOL_IN_CG_NAME)
        deleteRrVolume(VOL_IN_CG_IN_GRP_NAME)
        deleteCg(CG_NAME_FOR_GRP)
        deleteCg(CG_NAME_FOR_SET)

        // rm target vols left after STOP tests orphaned them
        deleteRrVolume(VOL_IN_SET_NAME + "_TARGET")
        deleteRrVolume(VOL_IN_CG_NAME + "_TARGET")
    }

    static enableVpoolForCg(String vpoolName) {
        def vpoolId = client.blockVpools().search().byExactName(vpoolName).first()?.id
        client.blockVpools().update(vpoolId,new BlockVirtualPoolUpdateParam(multiVolumeConsistency: true))
    }

    static createRrVolume(String name, String size, String varray, String vpool, String project, String rrMode, String rrGroup, String cgName) {

        // see if vol exists
        def volId = client.blockVolumes().search().byExactName(name).first()?.id
        if (volId != null) {
            println "Volume '" + name + "' exists.  Skipping creation"
            return
        }

        def overrideParameters = [:]
        overrideParameters.name = name
        overrideParameters.size = size
        overrideParameters.virtualArray = getOption(AO_VARRAY,varray)
        def vpoolParams = [(AO_VARRAY):overrideParameters.virtualArray]
        overrideParameters.virtualPool = getOption(AO_VPOOL,vpool,vpoolParams)
        overrideParameters.project = getOption(AO_PROJECT,project)
        overrideParameters.remoteReplicationMode = rrMode
        if (rrGroup == NONE) {
            overrideParameters.remoteReplicationGroup = NONE
        } else {
            def RR_DRIVER_TYPE_ID = getOption(AO_RR_STORAGE_TYPE,RR_DRIVER_TYPE)
            def params = [(AO_RR_STORAGE_TYPE):RR_DRIVER_TYPE_ID]
            def RR_SET_ID = getOption(AO_RR_SETS_FOR_TYPE,RR_SET,params)
            params = [(AO_RR_SETS_FOR_TYPE):RR_SET_ID]
            def RR_GROUP_ID = getOption(AO_RR_GROUPS_FOR_SET,rrGroup,params)
            overrideParameters.remoteReplicationGroup = RR_GROUP_ID
        }
        if(cgName == NONE) {
            overrideParameters.consistencyGroup = NONE
        } else {
            overrideParameters.consistencyGroup = client.blockConsistencyGroups().search().byExactName(cgName).first()?.id.toString()
        }
        printInfo "Creating Volume '" + name + "'"
        printVerbose formatMap(overrideParameters)
        return placeOrder(CREATE_BLOCK_VOLUME_SERVICE, overrideParameters)
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
        overrideParameters.project = getOption(AO_PROJECT,VOL_PROJECT_NAME)
        return placeOrder(REMOVE_BLOCK_VOLUME_SERVICE, overrideParameters)
    }

    static createCg(String name, String project) {
        def cgId = client.blockConsistencyGroups().search().byExactName(name).first()?.id
        if (cgId != null) {
        println "CG '" + name + "' exists.  Skipping creation"
            return
        }
        println "Creating CG '" + name + "'"
        URI VOL_PROJECT_URI = new URI(getOption(AO_PROJECT,project))
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

    static String formatMap(Map map){
        StringBuffer sb = new StringBuffer()
        for (i in map) {
            sb.append(" ").append(i.key).append(":").append(i.value).append(System.getProperty("line.separator"))
        }
        return sb.toString()
    }
}
