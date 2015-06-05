package com.emc.vipr.sanity.setup

import static com.emc.vipr.sanity.setup.Sanity.*
import com.emc.storageos.model.auth.AuthnCreateParam
import com.emc.storageos.model.tenant.TenantUpdateParam
import com.emc.storageos.model.tenant.UserMappingChanges
import com.emc.storageos.model.tenant.UserMappingParam
import com.emc.storageos.model.tenant.UserMappingAttributeParam
import com.emc.storageos.model.auth.RoleAssignmentChanges
import com.emc.storageos.model.auth.RoleAssignmentEntry
import static org.junit.Assert.*

class SecuritySetup {
    static final String PROVIDER_NAME = "ad configuration"

    static void setupActiveDirectory() {
        println "Setting Active Directory"
        if (client.authnProviders().search().byExactName(PROVIDER_NAME).first()) {
            login(properties.LDAP_SUPERUSER_USERNAME, properties.LDAP_SUPERUSER_PASSWORD)
            return
        }
        client.authnProviders().create(new AuthnCreateParam(
            mode: "ad",
            serverUrls: properties.getList("AUTHN_URLS"),
            domains: [properties.AUTHN_DOMAINS] as Set,
            groupWhitelistValues: properties.getList("AUTHN_WHITELIST") as Set,
            label: PROVIDER_NAME,
            searchBase: properties.AUTHN_SEARCH_BASE.join(','),
            searchFilter: properties.AUTHN_SEARCH_FILTER,
            managerDn: properties.AUTHN_MANAGER_DN.join(','),
            managerPassword: properties.AUTHN_MANAGER_PWD,
            groupAttribute: properties.AUTHN_GROUP_ATTR
        ))

        client.tenants().update(client.userTenantId, new TenantUpdateParam(
            userMappingChanges: new UserMappingChanges(
                add: [
                    new UserMappingParam(domain: properties.AUTHN_DOMAINS,
                        attributes: [new UserMappingAttributeParam('ou', ['sanity'])]),
                    new UserMappingParam(domain: properties.AUTHN_DOMAINS,
                        groups: ["  test Group    "])
                ]
            )
        ))

        client.tenants().updateRoleAssignments(client.userTenantId, new RoleAssignmentChanges(
            add: [new RoleAssignmentEntry(subjectId: properties.LDAP_SUPERUSER_USERNAME, roles: ['TENANT_ADMIN', 'TENANT_APPROVER'])]
        ))

        client.vdc().updateRoleAssignments(new RoleAssignmentChanges(
            add: [new RoleAssignmentEntry(subjectId: properties.LDAP_SUPERUSER_USERNAME,
                roles: ['SYSTEM_ADMIN', 'SYSTEM_MONITOR', 'SECURITY_ADMIN', 'SYSTEM_AUDITOR']
            )]
        ))

        login(properties.LDAP_SUPERUSER_USERNAME, properties.LDAP_SUPERUSER_PASSWORD)
    }
}
