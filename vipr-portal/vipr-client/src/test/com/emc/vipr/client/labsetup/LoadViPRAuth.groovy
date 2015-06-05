package com.emc.vipr.client.labsetup

import com.emc.vipr.client.ViPRCoreClient
import com.emc.vipr.client.ViPRSystemClient
import com.emc.storageos.model.auth.AuthnCreateParam
import com.emc.storageos.model.tenant.UserMappingParam
import com.emc.storageos.model.tenant.UserMappingAttributeParam
import com.emc.storageos.model.auth.RoleAssignmentEntry
import com.emc.storageos.model.network.NetworkSystemCreate
import com.emc.storageos.model.smis.SMISProviderCreateParam
import com.emc.storageos.model.systems.StorageSystemRequestParam
import com.emc.storageos.model.host.HostCreateParam
import com.emc.storageos.model.host.vcenter.VcenterCreateParam
import com.emc.storageos.model.auth.RoleAssignmentChanges

import com.emc.storageos.services.util.EnvConfig


def host = args.length > 0 ? args[0] : "localhost"
ViPRCoreClient client = new ViPRCoreClient(host, true)
ViPRSystemClient sysClient = new ViPRSystemClient(host, true)

new SetupUtils(client: client, sysClient: sysClient).with {
    println "Loading auth configuration on ViPR Instance: $host"
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
}
client.auth().logout()

println "done"


