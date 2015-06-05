package com.emc.vipr.client.labsetup

import com.emc.vipr.client.ViPRCoreClient
import com.emc.vipr.client.ViPRSystemClient
import com.emc.vipr.client.core.filters.VplexVolumeFilter;
import com.emc.storageos.model.auth.AuthnCreateParam
import com.emc.storageos.model.tenant.UserMappingParam
import com.emc.storageos.model.tenant.UserMappingAttributeParam
import com.emc.storageos.model.auth.RoleAssignmentEntry
import com.emc.storageos.model.network.NetworkSystemCreate
import com.emc.storageos.model.smis.StorageProviderCreateParam
import com.emc.storageos.model.systems.StorageSystemRequestParam
import com.emc.storageos.model.host.HostCreateParam
import com.emc.storageos.model.host.vcenter.VcenterCreateParam
import com.emc.storageos.model.auth.RoleAssignmentChanges
import com.emc.storageos.model.varray.NetworkCreate
import com.emc.storageos.model.ports.StoragePortUpdate
import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges
import com.emc.storageos.model.pools.VirtualArrayAssignments
import com.emc.storageos.model.protection.ProtectionSystemRequestParam

import com.emc.storageos.services.util.EnvConfig


def host = args.length > 0 ? args[0] : "localhost"
ViPRCoreClient client = new ViPRCoreClient(host, true)
ViPRSystemClient sysClient = new ViPRSystemClient(host, true)

new SetupUtils(client: client, sysClient: sysClient).with {
    println "Loading lab configuration on ViPR Instance: $host"
    login("root", "ChangeMe")
    license(License.LICENSE)
    
    if (!client.authnProviders().all.isEmpty()) {
        println "AD Already active, logging back in"
        client.auth().login(EnvConfig.get("viprclientsanity","ldap.administrator"), EnvConfig.get("viprclientsanity","ldap.managerPassword"))
    }

    URI tenantId = client.userTenant.tenant
    println "Tenant: $tenantId"

    // Create Active Directory Provider
    createAuthProvider(new AuthnCreateParam(
        mode: "ad",
        label: EnvConfig.get("viprclientsanity","ldap.label"),
        description: "Authentication provider for corp.sean.com",
        serverUrls: [EnvConfig.get("viprclientsanity","ldap.serverUrls")],
        domains: [EnvConfig.get("viprclientsanity","ldap.domains")],
        managerDn: EnvConfig.get("viprclientsanity","ldap.managerDn"),
        managerPassword: EnvConfig.get("viprclientsanity","ldap.managerPassword"),
        searchBase: EnvConfig.get("viprclientsanity","ldap.searchBase"),
        searchFilter: EnvConfig.get("viprclientsanity","ldap.searchFilter"),
        groupWhitelistValues: [EnvConfig.get("viprclientsanity","ldap.groupWhitelistValues")],
        groupAttribute: "CN",
    ))

    // If we want to set up AD integration. Set up all assets in the created tenant
    // Update Root tenant to be linked to AD
    updateTenant(tenantId, "Primary", [
        new UserMappingParam(domain: "corp.sean.com", attributes: [new UserMappingAttributeParam("department", ["Development"])])
    ], [])

    // Create secondary tenant to use
    URI secondTenant = createTenant(tenantId, "QA", [
        new UserMappingParam(domain: "corp.sean.com", attributes: [new UserMappingAttributeParam("department", ["QA"])])
    ])

    // Set up tenant level Roles
    client.tenants().updateRoleAssignments(tenantId, new RoleAssignmentChanges(add: [
        new RoleAssignmentEntry(subjectId: EnvConfig.get("viprclientsanity","ldap.aapprover"),     roles: ["TENANT_APPROVER"]),
        new RoleAssignmentEntry(subjectId: EnvConfig.get("viprclientsanity","ldap.administrator"), roles: ["TENANT_ADMIN", "PROJECT_ADMIN"]),
        new RoleAssignmentEntry(subjectId: EnvConfig.get("viprclientsanity","ldap.ttenant"),       roles: ["TENANT_ADMIN"])
    ]))

    client.tenants().updateRoleAssignments(secondTenant, new RoleAssignmentChanges(add: [
        new RoleAssignmentEntry(subjectId: EnvConfig.get("viprclientsanity","ldap.qauser"), roles: ["TENANT_APPROVER", "TENANT_ADMIN", "PROJECT_ADMIN"])
    ]))

    // Set up zone level roles
    client.vdc().updateRoleAssignments(new RoleAssignmentChanges(add: [
        new RoleAssignmentEntry(subjectId: EnvConfig.get("viprclientsanity","ldap.administrator"), roles: ["SECURITY_ADMIN", "SYSTEM_ADMIN", "SYSTEM_AUDITOR"]),
        new RoleAssignmentEntry(subjectId: EnvConfig.get("viprclientsanity","ldap.mmonitor"),      roles: ["SYSTEM_MONITOR"]),
        new RoleAssignmentEntry(subjectId: EnvConfig.get("viprclientsanity","ldap.aauditor"),      roles: ["SYSTEM_AUDITOR"]),
        new RoleAssignmentEntry(subjectId: EnvConfig.get("viprclientsanity","ldap.ssecurity"),     roles: ["SECURITY_ADMIN"])
    ]))

    // Create Neighborhood
    URI varray = createVarray("BrocadeVArray")
    URI varrayVplex1Id = createVarray("va-vplex154-cluster1")
    URI varrayVplex2Id = createVarray("va-vplex154-cluster2")
    URI varrayVplexVnxId = createVarray("va-vplex-vnx")

    // Transport Zones
    URI network = createNetwork(new NetworkCreate(label: "IP Network", transportType: "IP"))

    // Brocade
    tryCreateNetworkSystem(new NetworkSystemCreate(
        name: EnvConfig.get("viprclientsanity","network.name")
        systemType: "brocade",
        ipAddress: EnvConfig.get("viprclientsanity","network.ipAddress"),
        userName: EnvConfig.get("viprclientsanity","network.userName"),
        password: EnvConfig.get("viprclientsanity","network.password"),
        portNumber: 22,
        smisProviderIp: EnvConfig.get("viprclientsanity","network.smisProviderIp"),
        smisPortNumber: 5988,
        smisUserName: EnvConfig.get("viprclientsanity","network.smisUserName"),
        smisPassword: EnvConfig.get("viprclientsanity","network.smisPassword"),
        smisUseSsl: false
    ))

    // VNX
    tryCreateStorageProvider(new StorageProviderCreateParam(
        name: EnvConfig.get("viprclientsanity","vnx018.name"),
        ipAddress: EnvConfig.get("viprclientsanity","vnx018.ipAddress"),
        portNumber: 5988,
        userName: EnvConfig.get("viprclientsanity","vnx018.userName"),
        password: EnvConfig.get("viprclientsanity","vnx018.password"),
        useSSL: false,
        interfaceType: "smis"
    ))
    tryCreateStorageProvider(new StorageProviderCreateParam(
        name: EnvConfig.get("viprclientsanity","vnx480.name"),
        ipAddress: EnvConfig.get("viprclientsanity","vnx480.ipAddress"),
        portNumber: 5988,
        userName: EnvConfig.get("viprclientsanity","vnx480.userName"),
        password: EnvConfig.get("viprclientsanity","vnx480.password"),
        useSSL: false,
        interfaceType: "smis"
    ))

    // VMAX
    tryCreateStorageProvider(new StorageProviderCreateParam(
        name: EnvConfig.get("viprclientsanity","vmax01.name"),
        ipAddress: EnvConfig.get("viprclientsanity","vmax01.ipAddress"),
        portNumber: 5989,
        userName: EnvConfig.get("viprclientsanity","vmax01.userName"),
        password: EnvConfig.get("viprclientsanity","vmax01.password"),
        useSSL: true,
        interfaceType: "smis"
    ))
    tryCreateStorageProvider(new StorageProviderCreateParam(
        name: EnvConfig.get("viprclientsanity","vmax02.name"),
        ipAddress: EnvConfig.get("viprclientsanity","vmax02.ipAddress"),
        portNumber: 5989,
        userName: EnvConfig.get("viprclientsanity","vmax02.userName"),
        password: EnvConfig.get("viprclientsanity","vmax02.password"),
        useSSL: true,
        interfaceType: "smis"
    ))
    
    // VPLEX
    // VPLEX 154 cluster1: VMAX(235,505,573), VNX(0480,0018,1630,1392)
    // VPLEX 154 cluster2: VMAX(406,505,573), VNX(0480,0018,1630,1392)
    tryCreateStorageProvider(new StorageProviderCreateParam(
        name: EnvConfig.get("viprclientsanity","vplex.name"),
        ipAddress: EnvConfig.get("viprclientsanity","vplex.ipAddress"),
        portNumber: 443,
        userName: EnvConfig.get("viprclientsanity","vplex.userName"),
        password: EnvConfig.get("viprclientsanity","vplex.password"),
        useSSL: true,
        interfaceType: "vplex"
    ))
    
    tryCreateProtectionSystem(new ProtectionSystemRequestParam(
        label: EnvConfig.get("viprclientsanity","rp.label"), 
        systemType: "rp",
        ipAddress: EnvConfig.get("viprclientsanity","rp.ipAddress"), 
        portNumber: 7225, 
        userName: EnvConfig.get("viprclientsanity","rp.userName"), 
        password: EnvConfig.get("viprclientsanity","rp.password"), 
        registrationMode: null
    ))

    // VNX File
    URI vnxFileId = tryCreateStorageSystem(new StorageSystemRequestParam(
        name: EnvConfig.get("viprclientsanity","vnxfile.name"),
        systemType: "vnxfile",
        ipAddress: EnvConfig.get("viprclientsanity","vnxfile.ipAddress"),
        portNumber: 8080,
        userName: EnvConfig.get("viprclientsanity","vnxfile.userName"),
        password: EnvConfig.get("viprclientsanity","vnxfile.password"),
        smisProviderIP: EnvConfig.get("viprclientsanity","vnxfile.smisProviderIP"),
        smisPortNumber: 5989,
        smisUserName: EnvConfig.get("viprclientsanity","vnxfile.smisUserName"),
        smisPassword: EnvConfig.get("viprclientsanity","vnxfile.smisPassword"),
        smisUseSSL: true
    ))
    if (vnxFileId) {
        addStoragePortsToNetwork(vnxFileId, network)
    }

    // Isilon
    URI isilonId = tryCreateStorageSystem(new StorageSystemRequestParam(
        name: EnvConfig.get("viprclientsanity","isilon.name"),
        systemType: "isilon",
        ipAddress: EnvConfig.get("viprclientsanity","isilon.ipAddress"),
        portNumber: 8080,
        userName: EnvConfig.get("viprclientsanity","isilon.userName"),
        password: EnvConfig.get("viprclientsanity","isilon.password")
    ))
    if (isilonId) {
        addStoragePortsToNetwork(isilonId, network)
    }

    // Create Block Classes of Service
    createBlockVirtualPool("VMAX-Thin", "Thin", "vmax", varray)
    createBlockVirtualPool("VNX-Thin", "Thin", "vnxblock", varray)
    createBlockVirtualPool("VNX-Thick", "Thick", "vnxblock", varray)

    // Create File Classes of Service
    createFileVirtualPool("isilon", "Thin", "isilon", varray)
    createFileVirtualPool("vnxfile", "Thin", "vnxfile", varray)

    // Associate Transport Zones
    associateNetworks(varray)

    addStoragePortsToVirtualArray(varrayVplex1Id, EnvConfig.get("viprclientsanity","symport.arrayId1"), 'FC')
    addStoragePortsToVirtualArray(varrayVplex1Id, EnvConfig.get("viprclientsanity","symport.varrayVplex1Id"), 'FC', 'director-1-1-A')
    addStoragePortsToVirtualArray(varrayVplex1Id, EnvConfig.get("viprclientsanity","symport.varrayVplex1Id"), 'FC', 'director-1-1-B')
    
    addStoragePortsToVirtualArray(varrayVplex2Id, EnvConfig.get("viprclientsanity","symport.arrayId2"), 'FC')
    addStoragePortsToVirtualArray(varrayVplex2Id, EnvConfig.get("viprclientsanity","symport.varrayVplex1Id"), 'FC', 'director-2-1-A')
    addStoragePortsToVirtualArray(varrayVplex2Id, EnvConfig.get("viprclientsanity","symport.varrayVplex1Id"), 'FC', 'director-2-1-B')
        
    createBlockVirtualPoolForVplex("vp-vplex-local-1", "Thin", varrayVplex1Id, "vplex_local", null, false)
    createBlockVirtualPoolForVplex("vp-vplex-local-2", "Thin", varrayVplex2Id, "vplex_local", null, false)
    createBlockVirtualPoolForVplex("vp-vplex-dist-1", "Thin", varrayVplex1Id, "vplex_distributed", varrayVplex2Id, false)
    
    addStoragePortsToVirtualArray(varrayVplexVnxId, EnvConfig.get("viprclientsanity","clarion.arrayId1"), 'FC')
    addStoragePortsToVirtualArray(varrayVplexVnxId, EnvConfig.get("viprclientsanity","clarion.arrayId2"), 'FC')
    addStoragePortsToVirtualArray(varrayVplexVnxId, EnvConfig.get("viprclientsanity","clarion.VplexVnxId"), 'FC', 'director-1-1-A')
    addStoragePortsToVirtualArray(varrayVplexVnxId, EnvConfig.get("viprclientsanity","clarion.VplexVnxId"), 'FC', 'director-1-1-B')
    
    createBlockVirtualPoolForVplex("vp-vplex-vnx-mirror", "Thin", varrayVplexVnxId, "vplex_local", null, true)
    

    // Tasks as Tenant Admin/Project Admin
    client.auth().login(EnvConfig.get("viprclientsanity","ldap.administrator"), EnvConfig.get("viprclientsanity","ldap.managerPassword"))

    // Create Projects
    createProject(tenantId, "ViPR Project 1")
    createProject(tenantId, "ViPR Project 2")

    // Create Test lab hosts/Vcenters
    tryCreateHost(new HostCreateParam(
        tenant: tenantId,
        type: "Linux",
        name: EnvConfig.get("viprclientsanity","host1.name"),
        hostName: EnvConfig.get("viprclientsanity","host1.hostName"),
        portNumber: 22,
        userName: EnvConfig.get("viprclientsanity","host.userName"),
        password: EnvConfig.get("viprclientsanity","host.password")
    ))
    tryCreateHost(new HostCreateParam(
        tenant: tenantId,
        type: "Linux",
        name: EnvConfig.get("viprclientsanity","host2.userName"),
        hostName: EnvConfig.get("viprclientsanity","host2.hostName"),
        portNumber: 22,
        userName: EnvConfig.get("viprclientsanity","host.userName"),
        password: EnvConfig.get("viprclientsanity","host.password")
    ))
    tryCreateHost(new HostCreateParam(
        tenant: tenantId,
        type: "Windows",
        name: EnvConfig.get("viprclientsanity","winhost1.userName"),
        hostName: EnvConfig.get("viprclientsanity","winhost1.hostName"),
        useSsl: true,
        portNumber: 5986,
        userName: EnvConfig.get("viprclientsanity","winhost.userName"),
        password: EnvConfig.get("viprclientsanity","winhost.password")
    ))
	tryCreateHost(new HostCreateParam(
        tenant: tenantId,
		type: "Windows",
		name: EnvConfig.get("viprclientsanity","winhost2.userName"),
        hostName: EnvConfig.get("viprclientsanity","winhost2.hostName"),
		useSsl: false,
		portNumber: 5985,
		userName: EnvConfig.get("viprclientsanity","winhost.userName"),
        password: EnvConfig.get("viprclientsanity","winhost.password")
	))
	tryCreateHost(new HostCreateParam(
        tenant: tenantId,
		type: "Windows",
		name: EnvConfig.get("viprclientsanity","winhost3.userName"),
        hostName: EnvConfig.get("viprclientsanity","winhost3.hostName"),
		useSsl: false,
		portNumber: 5985,
		userName: EnvConfig.get("viprclientsanity","winhost.userName"),
        password: EnvConfig.get("viprclientsanity","winhost.password")
	))
    tryCreateVcenter(tenantId, new VcenterCreateParam(
        name: EnvConfig.get("viprclientsanity","vcenter.name"),
        ipAddress: EnvConfig.get("viprclientsanity","vcenter.ipAddress"),
        portNumber: 443,
        userName: EnvConfig.get("viprclientsanity","vcenter.userName"),
        password: EnvConfig.get("viprclientsanity","vcenter.password")
    ))
}

client.auth().logout()

println "done"


