package com.emc.vipr.sanity.setup

import static Sanity.*
import com.emc.storageos.model.smis.StorageProviderCreateParam
import com.emc.storageos.model.vpool.BlockVirtualPoolParam
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam
import com.emc.storageos.model.auth.ACLAssignmentChanges
import com.emc.storageos.model.auth.ACLEntry

class VNXSetup {
    static final String VNX_PROVIDER_NAME = "VNX-PROVIDER"
    static final String VNXBLOCK_VPOOL = "cosvnxb"

    static def vnxBlockVpoolId

    static void setupSimulator() {
        println "Setting up VNX Block simulators"
        if (client.storageProviders().search().byExactName(VNX_PROVIDER_NAME).first()) {
            return
        }

        client.storageProviders().create(new StorageProviderCreateParam(
            name: VNX_PROVIDER_NAME,
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

        setupVpool()
        updateAcls()
    }

    static void setupVpool() {
        println "Setting up VNX Block Vpools"
        vnxBlockVpoolId = client.blockVpools().create(new BlockVirtualPoolParam(
            name: VNXBLOCK_VPOOL,
            description: VNXBLOCK_VPOOL,
            protocols: ['FC'],
            numPaths: 2,
            protection: new BlockVirtualPoolProtectionParam(snapshots: new VirtualPoolProtectionSnapshotsParam(10)),
            systemType: "vmax",
            provisionType: "Thin",
            varrays: [VirtualArraySetup.varrayId]
        )).id
    }

    static void updateAcls() {
        client.blockVpools().updateACLs(vnxBlockVpoolId, new ACLAssignmentChanges(
            add: [
                new ACLEntry(tenant: client.getUserTenantId(), aces: ["USE"])
            ]
        ))
    }
}
