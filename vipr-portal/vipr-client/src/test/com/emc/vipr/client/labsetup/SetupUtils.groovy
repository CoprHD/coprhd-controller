package com.emc.vipr.client.labsetup

import com.emc.storageos.model.network.NetworkSystemCreate
import com.emc.storageos.model.tenant.UserMappingParam
import com.emc.storageos.model.auth.AuthnCreateParam
import com.emc.storageos.model.tenant.TenantCreateParam
import com.emc.storageos.model.tenant.TenantUpdateParam
import com.emc.storageos.model.tenant.UserMappingChanges
import com.emc.vipr.client.exceptions.ServiceErrorException
import com.emc.storageos.model.varray.VirtualArrayCreateParam
import com.emc.storageos.model.project.ProjectParam
import com.emc.storageos.model.protection.ProtectionSystemRequestParam;
import com.emc.storageos.model.auth.ACLAssignmentChanges
import com.emc.storageos.model.auth.ACLEntry
import com.emc.storageos.model.smis.StorageProviderCreateParam
import com.emc.storageos.model.systems.StorageSystemRequestParam
import com.emc.storageos.model.vpool.BlockVirtualPoolParam
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionMirrorParam
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam
import com.emc.storageos.model.vpool.FileVirtualPoolParam
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam.VirtualArrayVirtualPoolMapEntry;
import com.emc.storageos.model.host.HostCreateParam
import com.emc.storageos.model.host.vcenter.VcenterCreateParam
import com.emc.storageos.model.varray.NetworkUpdate
import com.emc.storageos.model.ports.StoragePortUpdate
import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges
import com.emc.storageos.model.pools.VirtualArrayAssignments
import com.emc.vipr.client.ViPRCoreClient
import com.emc.vipr.client.ViPRSystemClient
import com.emc.storageos.model.varray.NetworkCreate

/**
 * Utilities to make writing script for setting up bourne configurations.
 */
class SetupUtils {
    static int DUPLICATE_LABEL_ERROR_CODE = 1503

    ViPRCoreClient client
    ViPRSystemClient sysClient
    
    def login(String username, String password) {
        String authToken = client.auth().login(username, password)
        if (sysClient != null) {
            sysClient.withAuthToken(authToken)
        }
    }
    
    def getLicenseFeature(String type) {
        return sysClient.license().get()?.licenseFeatures.findAll { it.modelId == type };
    }
    
    def setLicenseText(String text) {
        println "Setting license:\n$text"
        sysClient.license().set(text)
    }

    def license(String text) {
        if (!getLicenseFeature(License.CONTROLLER_FEATURE)) {
            setLicenseText(text)
        }
        else {
            println "Already licensed"
        }
    }

    def createAuthProvider(AuthnCreateParam create) {
        def res = client.authnProviders().search().byExactName(create.label).first()
        if (res != null) {
            println "Found Authentication Provider: $res.name $res.id"
            return res.id
        }

        def id = client.authnProviders().create(create).id
        println "Created Authentication Provider: $id"
        return id
    }

    def createTenant(URI parentId, String name, List<UserMappingParam> users) {
        // Note, we can list the subtenant but we may not have access to query it
        def res = client.tenants().listSubtenants(parentId).find {it.name == name}
        if (res != null) {
            println "Found Tenant: $res.name $res.id"
            return res.id
        }

        def id = client.tenants().create(new TenantCreateParam(label: name, userMappings: users)).id
        println "Created Tenant: $id"
        return id
    }

    public void updateTenant(URI tenantId, String name, List<UserMappingParam> add, List<UserMappingParam> remove) {
        try {
            client.tenants().update(tenantId, new TenantUpdateParam(label: name, userMappingChanges:
                new UserMappingChanges(add: add, remove: remove)))
        }
        catch(ServiceErrorException e) {
            // We get Duplicate label errors if this is the second time the script is run... ignore them
            if(e.getCode() != DUPLICATE_LABEL_ERROR_CODE) {
                throw e
            }
        }
        println "Updated Tenant: $tenantId"
    }

    def createVarray(String name) {
        def res = client.varrays().search().byExactName(name).first()
        if (res != null) {
            println "Found VirtualArray: $name $res.id"
            return res.id
        }

        def id = client.varrays().create(new VirtualArrayCreateParam(label:  name)).id
        println "Created VirtualArray: $id"
        return id
    }

    def createProject(URI tenantId, String name) {
        def res = client.projects().search().byExactName(name).first()
        if (res != null) {
            println "Found Project: $name $res.id"
            return res.id
        }

        def id = client.projects().create(tenantId, new ProjectParam(name: name)).id

        println "Adding Project ACL"
        client.projects().updateACLs(id, new ACLAssignmentChanges(add: [
            new ACLEntry(aces: ["ALL"], group: "Domain Users@corp.sean.com")
        ]))

        println "Created Project $name $id"
        return id
    }

    def createNetwork(NetworkCreate param) {
        def res = client.networks().search().byExactName(param.label).first()
        if (res != null) {
            println "Found Network: $res.name $res.id"
            return res.id
        }
        else {
            def id = client.networks().create(param).id
            println "Created Network: $param.label $id"
            return id
        }
    }

    def waitForDiscovery(task) {
        def id = task.get().id
        println "  Discovery complete"
        return id
    }
    
    def createNetworkSystem(NetworkSystemCreate create) {
        def res = client.networkSystems().search().byExactName(create.name).first()
        if (res != null) {
            println "Found Switch: $create.name $res.id"
            return res.id
        }
        else {
            def task = client.networkSystems().create(create)
            println "Created Switch: $create.name $task.resourceId, waiting for discovery..."
            return waitForDiscovery(task)
        }
    }

    def tryCreateNetworkSystem(NetworkSystemCreate create) {
        try {
            return createNetworkSystem(create)
        }
        catch (e) {
            println "Failed to find/create Switch: $create.name\n\t${e.message}"
            return null
        }
    }

    def createStorageProvider(StorageProviderCreateParam create) {
        def res = client.storageProviders().search().byExactName(create.name).first()
        if (res != null) {
            println "Found Storage Provider: $create.name $res.id"
            return res.id
        }
        else {
            def task = client.storageProviders().create(create)
            println "Created Storage Provider: $create.name $task.resourceId, waiting for discovery..."
            return waitForDiscovery(task)
        }
    }

    def tryCreateStorageProvider(StorageProviderCreateParam create) {
        try {
            return createStorageProvider(create)
        }
        catch (e) {
            println "Failed to find/create Storage Provider: $create.name\n\t${e.message}"
            return null
        }
    }

    def createProtectionSystem(ProtectionSystemRequestParam create) {
        def res = client.protectionSystems().search().byExactName(create.label).first()
        if (res != null) {
            println "Found Protection System: $create.label $res.id"
            return res.id
        }
        else {
            def task = client.protectionSystems().create(create);
            println "Created Protection System: $create.label $task.resourceId, waiting for discovery..."
            return waitForDiscovery(task)
        }
    }

    def tryCreateProtectionSystem(ProtectionSystemRequestParam create) {
        try {
            return createProtectionSystem(create)
        }
        catch (e) {
            println "Failed to find/create Protection System: $create.label\n\t${e.message}"
            return null
        }
    }

    def createStorageSystem(StorageSystemRequestParam create) {
        def res = client.storageSystems().search().byExactName(create.name).first()
        if (res != null) {
            println "Found Storage System: $res.name $res.id"
            return res.id
        }
        else {
            def task = client.storageSystems().create(create)
            println "Created Storage System: $create.name $task.resourceId, waiting for discovery..."
            return waitForDiscovery(task)
        }
    }

    def tryCreateStorageSystem(StorageSystemRequestParam create) {
        try {
            return createStorageSystem(create)
        }
        catch (e) {
            println "Failed to find/create Storage System: $create.name\n\t${e.message}"
            return null
        }

    }

    def addStoragePortsToNetwork(URI storageSystemId, URI networkId) {
        client.storagePorts().getByStorageSystem(storageSystemId).each {port->
            if (port.network != null && port.network.id == networkId) {
                println "Found Storage Port $port.name ($port.portNetworkId) in Network"
            }
            else {
                println "Adding Storage Port $port.name ($port.portNetworkId) to Network $networkId"
                client.storagePorts().update(port.id, new StoragePortUpdate(network: networkId))
            }
        }
    }

    def createBlockVirtualPool(String name, String provisioningType, String systemType, URI varrayId) {

        BlockVirtualPoolProtectionParam protection = new BlockVirtualPoolProtectionParam(
            snapshots: new VirtualPoolProtectionSnapshotsParam(maxSnapshots: 10),
            continuousCopies: new VirtualPoolProtectionMirrorParam(maxMirrors: 10));
        
        BlockVirtualPoolParam create = new BlockVirtualPoolParam(
            name: name, description: name, protocols: ["FC"],
            provisionType: provisioningType, numPaths: 1, systemType: systemType, varrays: [varrayId], useMatchedPools: true,
            protection: protection, multiVolumeConsistency: false, expandable: false)

        def res = client.blockVpools().search().byExactName(create.name).first()
        if (res != null) {
            println "Found Block Virtual Pool: $create.name $res.id"
            return res.id
        }
        else {
            def id = client.blockVpools().create(create).id
            println "Created Block Virtual Pool: $create.name $id"
            return id
        }
    }
    
    def createBlockVirtualPoolForVplex(String name, String provisioningType, URI varrayId, String highAvailabilityType, URI haTargetVirtualArrayId, boolean addProtection) {
        
        VirtualArrayVirtualPoolMapEntry haVirtualArrayVirtualPool = null;
        if (haTargetVirtualArrayId != null) {
            haVirtualArrayVirtualPool = new VirtualArrayVirtualPoolMapEntry();
            haVirtualArrayVirtualPool.virtualArray = haTargetVirtualArrayId
        }
        
        VirtualPoolHighAvailabilityParam highAvailability = new VirtualPoolHighAvailabilityParam(highAvailabilityType, haVirtualArrayVirtualPool)
        
        BlockVirtualPoolParam create = new BlockVirtualPoolParam(
            name: name, description: name, protocols: ["FC"],
            provisionType: provisioningType, numPaths: 1, varrays: [varrayId], useMatchedPools: true,
            multiVolumeConsistency: false, expandable: false, highAvailability: highAvailability)
        
        if (addProtection) {
            create.protection = new BlockVirtualPoolProtectionParam(
                snapshots: new VirtualPoolProtectionSnapshotsParam(maxSnapshots: 10),
                continuousCopies: new VirtualPoolProtectionMirrorParam(maxMirrors: 1)
            );
        }

        def res = client.blockVpools().search().byExactName(create.name).first()
        if (res != null) {
            println "Found Block Virtual Pool: $create.name $res.id"
            return res.id
        }
        else {
            def id = client.blockVpools().create(create).id
            println "Created Block Virtual Pool: $create.name $id"
            return id
        }
    }
    
    def createFileVirtualPool(String name, String provisioningType, String systemType, URI varrayId) {
        FileVirtualPoolParam create = new FileVirtualPoolParam(name: name, description: name,
            protocols: ["CIFS", "NFS"], systemType: systemType,
            varrays: [varrayId], useMatchedPools: true, provisionType: provisioningType,
            protection: new FileVirtualPoolProtectionParam(snapshots: new VirtualPoolProtectionSnapshotsParam(maxSnapshots: 10)))

        def res = client.fileVpools().search().byExactName(name).first()
        if (res != null) {
            println "Found File Virtual Pool: $create.name $res.id"
            return res.id
        }
        else {
            def id = client.fileVpools().create(create).id
            println "Created File Virtual Pool: $create.name $id"
            return id
        }
    }

    def void addBlockVirtualPoolAcl(URI tenantId, URI virtualPoolId) {
        ACLEntry acl = client.blockVpools().getACLs(virtualPoolId).find {it.tenant == tenantId}
        if (acl != null) {
            println "Found Block VirtualPool ACL: $virtualPoolId"
        }
        else {
            client.blockVpools().updateACLs(virtualPoolId, new ACLAssignmentChanges(add: [new ACLEntry(tenant: tenantId, aces: ["USE"])]))
            println "Created Block VirtualPool ACL"
        }
    }
    
    def void addFileVirtualPoolAcl(URI tenantId, URI virtualPoolId) {
        ACLEntry acl = client.fileVpools().getACLs(virtualPoolId).find {it.tenant == tenantId}
        if (acl != null) {
            println "Found File VirtualPool ACL: $virtualPoolId"
        }
        else {
            client.fileVpools().updateACLs(virtualPoolId, new ACLAssignmentChanges(add: [new ACLEntry(tenant: tenantId, aces: ["USE"])]))
            println "Created File VirtualPool ACL"
        }
    }

    def associateNetworks(URI varrayId) {
        client.networks().getAll().each {
            if (it.virtualArray != varrayId) {
                println "Associating Network $it.name with Virtual Array"
                client.networks().update(it.id, new NetworkUpdate(varrays: [varrayId]))
            }
        }
    }
    
    def addStoragePortsToVirtualArray(def virtualArrayId, String serialNumber, String transportType) {
        addStoragePortsToVirtualArray(virtualArrayId, serialNumber, transportType, null)
    }
    
    def addStoragePortsToVirtualArray(def virtualArrayId, String serialNumber, String transportType, String portGroup) {
        if (portGroup != null) {
            println "Adding $serialNumber Ports to Virtual Array $virtualArrayId"
        }
        else {
            println "Adding $serialNumber Ports, Port Group $portGroup to Virtual Array $virtualArrayId"
        }
        def storageSystemRefs = client.storageSystems().list()
        def storageSystems = client.storageSystems().getByRefs(storageSystemRefs)
        def storageSystem = storageSystems.find { it?.serialNumber && serialNumber?.endsWith(it?.serialNumber) };
        if (storageSystem != null ) {
            def storagePortRefs = client.storagePorts().listByStorageSystem(storageSystem.id)
            def storagePorts = client.storagePorts().getByRefs(storagePortRefs)
            def filteredStoragePorts = storagePorts.findAll { transportType.equalsIgnoreCase(it.transportType) }
            if (portGroup != null) {
                filteredStoragePorts = filteredStoragePorts.findAll { portGroup.equalsIgnoreCase(it.portGroup) }
            }
            
            filteredStoragePorts?.each {
                def addVarray = new VirtualArrayAssignments([virtualArrayId] as Set);
                def varrayChanges = new VirtualArrayAssignmentChanges(addVarray, null)
                client.storagePorts().update(it.id, new StoragePortUpdate(null, varrayChanges))
            }
        }
    }

    def createHost(HostCreateParam create) {
        def res = client.hosts().search().byExactName(create.name).first()
        if (res != null) {
            println "Found Host: $res.name $res.id"
        }
        else {
            def task = client.hosts().create(create)
            println "Created Host: $create.name $task.resourceId, waiting for discovery..."
            return waitForDiscovery(task)
        }
    }

    def tryCreateHost(HostCreateParam create) {
        try {
            return createHost(create)
        }
        catch (e) {
            println "Failed to find/create Host: $create.name\n\t${e.message}"
            return null
        }
    }

    def createVcenter(URI tenantId, VcenterCreateParam create) {
        def res = client.vcenters().search().byExactName(create.name).first()
        if (res != null) {
            println "Found vCenter: $res.name $res.id"
        }
        else {
            def task = client.vcenters().create(tenantId, create)
            println "Created VCenter: $create.name $task.resourceId, waiting for discovery..."
            return waitForDiscovery(task)
        }
    }

    def tryCreateVcenter(URI tenantId, VcenterCreateParam create) {
        try {
            return createVcenter(tenantId, create)
        }
        catch (e) {
            println "Failed to find/create Host: $create.name\n\t${e.message}"
            return null
        }
    }
}
