/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.setup

import static com.emc.vipr.sanity.Sanity.*

import com.emc.storageos.model.auth.ACLAssignmentChanges
import com.emc.storageos.model.auth.ACLEntry
import com.emc.storageos.model.smis.StorageProviderCreateParam
import com.emc.storageos.model.vpool.BlockVirtualPoolParam
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam

class VMAXSetup {
    static final String VMAX_PROVIDER_NAME = "VMAX-PROVIDER"
    static final String VMAXBLOCK_VPOOL = "vmaxpool"

    static def vmaxBlockVpoolId

    static void setupSimulator() {
        println "Setting up VMAX Block simulators"
        if (client.storageProviders().search().byExactName(VMAX_PROVIDER_NAME).first()) {
            return
        }

        client.storageProviders().create(new StorageProviderCreateParam(
                name: VMAX_PROVIDER_NAME,
                ipAddress: System.getenv("SIMULATOR_SMIS_IP"),
                portNumber: 7009,
                userName: System.getenv("HW_SIMULATOR_DEFAULT_USER"),
                password: System.getenv("HW_SIMULATOR_DEFAULT_PASSWORD"),
                interfaceType: "smis",
                useSSL: true
                )).waitFor(API_TASK_TIMEOUT)

        client.storageSystems().all.collect {
            client.storageSystems().discover(it.id)
        }.each {
            try {
                it.waitFor()
            }
            catch (e) {
                println "Warning - Discovery error: ${e.message}"
            }
        }

        client.storageSystems().all.collect {
            if (!it.nativeGuid.equalsIgnoreCase(System.getenv("SIMULATOR_VMAX3_NATIVEGUID"))) {
                println "Deleting storage system " + it.id + " with native guid = " + it.nativeGuid
                client.storageSystems().deregister(it.id)
                client.storageSystems().deactivate(it.id)
            }
        }

        setupVpool()
        updateAcls()
    }

    static void setupVpool() {
        println "Setting up VMAX Block Vpools"
        vmaxBlockVpoolId = client.blockVpools().create(new BlockVirtualPoolParam(
                name: VMAXBLOCK_VPOOL,
                description: VMAXBLOCK_VPOOL,
                protocols: ['FC'],
                numPaths: 2,
                protection: new BlockVirtualPoolProtectionParam(snapshots: new VirtualPoolProtectionSnapshotsParam(10)),
                systemType: "vmax",
                provisionType: "Thin",
                varrays: [
                    VirtualArraySetup.varrayId]
                )).id
    }

    static void updateAcls() {
        client.blockVpools().updateACLs(vmaxBlockVpoolId, new ACLAssignmentChanges(
                add: [
                    new ACLEntry(tenant: client.getUserTenantId(), aces: ["USE"])
                ]
                ))
    }
}
