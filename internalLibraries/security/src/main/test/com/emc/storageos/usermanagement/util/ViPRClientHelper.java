/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.util;

import com.emc.storageos.model.auth.*;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.project.ProjectUpdateParam;
import com.emc.storageos.model.tenant.*;
import com.emc.storageos.usermanagement.model.RoleOrAcl;
import com.emc.vipr.client.ViPRCoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ViPRClientHelper {

    private static Logger logger = LoggerFactory.getLogger(ViPRClientHelper.class);
    private ViPRCoreClient viPRCoreClient;

    public ViPRClientHelper(ViPRCoreClient viPRCoreClient) {
        this.viPRCoreClient = viPRCoreClient;
    }

    public ViPRCoreClient getViPRCoreClient() {
        return viPRCoreClient;
    }

    // /////////////////
    //
    // AuthnProvider related methods
    //
    // ////////////////

    /**
     * create AuthnProvider from xml file
     * 
     * @return
     */
    public AuthnProviderRestRep createAuthnProvider(String filePath) throws Exception {
        InputStream adFileInputStream = this.getClass().getClassLoader().getResourceAsStream(filePath);
        AuthnCreateParam input = XmlUtil.unmarshal(adFileInputStream, AuthnCreateParam.class);
        return createAuthnProvider(input);
    }

    public AuthnProviderRestRep createAuthnProvider(AuthnCreateParam input) {
        boolean isExisted = isAuthnProviderExisted(input);

        if (isExisted) {
            for (String domain : input.getDomains()) {
                AuthnProviderRestRep authnProviderRestRep = getAuthnProviderByDomain(domain);
                if (authnProviderRestRep != null) {
                    return authnProviderRestRep;
                }
            }

        } else {
            return viPRCoreClient.authnProviders().create(input);
        }

        return null;
    }

    /**
     * check if AuthProvider already existed
     * 
     * @param authnCreateParam
     * @return
     */
    public boolean isAuthnProviderExisted(AuthnCreateParam authnCreateParam) {
        List<AuthnProviderRestRep> authnProviderRestReps = viPRCoreClient.authnProviders().getAll();

        boolean bExisted = false;
        AuthnProviderRestRep restRep = null;
        for (AuthnProviderRestRep authnProviderRestRep : authnProviderRestReps) {
            Set<String> domains = authnProviderRestRep.getDomains();
            for (String existedDomain : domains) {
                for (String inputDomain : authnCreateParam.getDomains()) {
                    if (existedDomain.equalsIgnoreCase(inputDomain)) {
                        bExisted = true;
                        break;
                    }
                }

                if (bExisted) {
                    break;
                }
            }

            if (bExisted) {
                break;
            }
        }

        return bExisted;
    }

    /**
     * get AuthPrivder by domain
     * 
     * @param domain
     * @return
     */
    public AuthnProviderRestRep getAuthnProviderByDomain(String domain) {
        List<AuthnProviderRestRep> authnProviderRestReps = viPRCoreClient.authnProviders().getAll();

        for (AuthnProviderRestRep authnProviderRestRep : authnProviderRestReps) {
            for (String existedDomain : authnProviderRestRep.getDomains()) {
                if (existedDomain.equalsIgnoreCase(domain)) {
                    return authnProviderRestRep;
                }

            }
        }

        return null;
    }

    public void removeAuthnProviders() {

        List<AuthnProviderRestRep> providerList = viPRCoreClient.authnProviders().getAll();
        for (AuthnProviderRestRep providerRestRep : providerList) {
            viPRCoreClient.authnProviders().delete(providerRestRep.getId());
        }
    }

    // /////////////////
    //
    // tenant related methods
    //
    // ////////////////

    // create tenant from xml file
    public TenantOrgRestRep createTenant(String filePath) throws Exception {
        TenantCreateParam input = XmlUtil.unmarshal(new FileInputStream(new File(filePath)), TenantCreateParam.class);
        return viPRCoreClient.tenants().create(input);
    }

    /**
     * create tenant from 1 domain + 1 attribute
     */
    public TenantOrgRestRep createTenant(String name, String domain,
            String attributeName, String attributeValue) {

        // construct attribute: attributeName = attributeValue
        UserMappingAttributeParam userMappingAttributeParam = new UserMappingAttributeParam();
        userMappingAttributeParam.setKey(attributeName);
        List<String> valueList = new ArrayList<String>();
        valueList.add(attributeValue);
        userMappingAttributeParam.setValues(valueList);

        // add attribute constructed above into user mapping list
        List<UserMappingAttributeParam> userMappingAttributeParamList = new ArrayList<UserMappingAttributeParam>();
        userMappingAttributeParamList.add(userMappingAttributeParam);

        UserMappingParam userMappingParam = new UserMappingParam();
        userMappingParam.setDomain(domain);
        userMappingParam.setAttributes(userMappingAttributeParamList);

        List<UserMappingParam> userMappingList = new ArrayList<UserMappingParam>();
        userMappingList.add(userMappingParam);

        TenantCreateParam tenantInput = new TenantCreateParam();
        tenantInput.setLabel(name);
        tenantInput.setUserMappings(userMappingList);

        return viPRCoreClient.tenants().create(tenantInput);
    }

    public List<UserMappingParam> removeTenantUserMapping(URI tenantID) {
        List<UserMappingParam> userMappingParams = viPRCoreClient.tenants().get(tenantID).getUserMappings();

        UserMappingChanges userMappingChanges = new UserMappingChanges();
        userMappingChanges.setRemove(userMappingParams);
        TenantUpdateParam tenantUpdateParam = new TenantUpdateParam();
        tenantUpdateParam.setUserMappingChanges(userMappingChanges);
        viPRCoreClient.tenants().update(tenantID, tenantUpdateParam);

        return userMappingParams;

    }

    public void addUserMappingToTenant(URI tenantID,
            List<UserMappingParam> userMappingParams) {
        UserMappingChanges userMappingChanges = new UserMappingChanges();
        userMappingChanges.setAdd(userMappingParams);
        TenantUpdateParam tenantUpdateParam = new TenantUpdateParam();
        tenantUpdateParam.setUserMappingChanges(userMappingChanges);
        viPRCoreClient.tenants().update(tenantID, tenantUpdateParam);
    }

    // /////////////////
    //
    // role assignment related methods
    //
    // ////////////////

    public void removeAllVDCRoles() {
        List<RoleAssignmentEntry> list = viPRCoreClient.vdc().getRoleAssignments();
        RoleAssignmentChanges roleChanges = new RoleAssignmentChanges();
        roleChanges.setRemove(list);
        viPRCoreClient.vdc().updateRoleAssignments(roleChanges);

    }

    public void addVdcRoles(List<RoleAssignmentEntry> list) {
        RoleAssignmentChanges roleChanges = new RoleAssignmentChanges();
        roleChanges.setAdd(list);
        viPRCoreClient.vdc().updateRoleAssignments(roleChanges);
    }

    public void addRoleAssignment(
            URI projectOrTenantURI,
            String subjectId,
            String role) {
        updateRoleAssignment(projectOrTenantURI, subjectId, null, role, "add");
    }

    public void removeRoleAssignment(URI projectOrTenantURI,
            String subjectId,
            String role) {
        updateRoleAssignment(projectOrTenantURI, subjectId, null, role, "remove");
    }

    public List<RoleAssignmentEntry> removeAllTenantRoles(URI tenantURI) {
        List<RoleAssignmentEntry> list = viPRCoreClient.tenants().getRoleAssignments(tenantURI);
        RoleAssignmentChanges roleChanges = new RoleAssignmentChanges();
        roleChanges.setRemove(list);
        viPRCoreClient.tenants().updateRoleAssignments(tenantURI, roleChanges);
        return list;
    }

    public void addTenantRoles(URI tenantURI, List<RoleAssignmentEntry> list) {
        RoleAssignmentChanges roleChanges = new RoleAssignmentChanges();
        roleChanges.setAdd(list);
        viPRCoreClient.tenants().updateRoleAssignments(tenantURI, roleChanges);
    }

    // private methods
    private void updateRoleAssignment(URI projectOrTenantURI,
            String subjectId,
            String group,
            String role,
            String operationType) {

        if (projectOrTenantURI == null) {
            RoleAssignmentChanges changes = prepareRoleAssignmentChange(operationType, subjectId, group, role);
            viPRCoreClient.vdc().updateRoleAssignments(changes);
            return;
        }

        if (projectOrTenantURI.toString().contains("TenantOrg")) {
            RoleAssignmentChanges changes = prepareRoleAssignmentChange(operationType, subjectId, group, role);
            viPRCoreClient.tenants().updateRoleAssignments(projectOrTenantURI, changes);
            return;
        }

        if (projectOrTenantURI.toString().contains("Project")) {
            if (role.equals(RoleOrAcl.ProjectAclOwn.toString())) {
                ProjectUpdateParam projectUpdateParam = new ProjectUpdateParam();
                projectUpdateParam.setOwner(subjectId);
                viPRCoreClient.projects().update(projectOrTenantURI, projectUpdateParam);
            } else {
                ACLAssignmentChanges aclChanges = prepareACLAssignmentChange(operationType, subjectId, group, role);
                viPRCoreClient.projects().updateACLs(projectOrTenantURI, aclChanges);
            }
        }

    }

    private RoleAssignmentChanges prepareRoleAssignmentChange(String operationType,  // add or remove
            String subjectId,
            String group,
            String role
            ) {
        RoleAssignmentEntry roleAssignmentEntry = new RoleAssignmentEntry();

        if (subjectId != null) {
            roleAssignmentEntry.setSubjectId(subjectId);
        }

        if (group != null) {
            roleAssignmentEntry.setGroup(group);
        }

        List<String> roles = new ArrayList<String>();
        roles.add(role);
        roleAssignmentEntry.setRoles(roles);

        List<RoleAssignmentEntry> entries = new ArrayList<RoleAssignmentEntry>();
        entries.add(roleAssignmentEntry);

        RoleAssignmentChanges roleChanges = new RoleAssignmentChanges();
        if (operationType.equalsIgnoreCase("add")) {
            roleChanges.setAdd(entries);
        } else {
            roleChanges.setRemove(entries);
        }

        return roleChanges;
    }

    private static ACLAssignmentChanges prepareACLAssignmentChange(String operationType,  // add or remove
            String subjectId,
            String group,
            String role
            ) {
        ACLEntry roleAssignmentEntry = new ACLEntry();

        if (subjectId != null) {
            roleAssignmentEntry.setSubjectId(subjectId);
        }

        if (group != null) {
            roleAssignmentEntry.setGroup(group);
        }

        List<String> roles = new ArrayList<String>();
        roles.add(role);
        roleAssignmentEntry.setAces(roles);

        List<ACLEntry> entries = new ArrayList<ACLEntry>();
        entries.add(roleAssignmentEntry);

        ACLAssignmentChanges roleChanges = new ACLAssignmentChanges();
        if (operationType.equalsIgnoreCase("add")) {
            roleChanges.setAdd(entries);
        } else {
            roleChanges.setRemove(entries);
        }

        return roleChanges;
    }

    public void deactiveAllTenants() {

        URI rootTenantID = viPRCoreClient.getUserTenantId();
        List<String> roles = new ArrayList<String>();
        roles.add("TENANT_ADMIN");

        RoleAssignmentEntry roleAssignmentEntry = new RoleAssignmentEntry();
        roleAssignmentEntry.setSubjectId("root");
        roleAssignmentEntry.setRoles(roles);

        List<RoleAssignmentEntry> add = new ArrayList<RoleAssignmentEntry>();
        add.add(roleAssignmentEntry);

        RoleAssignmentChanges roleChanges = new RoleAssignmentChanges();
        roleChanges.setAdd(add);

        // grant TenantAdmin to root on provider tenant
        viPRCoreClient.tenants().updateRoleAssignments(rootTenantID, roleChanges);

        List<TenantOrgRestRep> tenantList = viPRCoreClient.tenants().getAllSubtenants(rootTenantID);
        for (TenantOrgRestRep tenantOrgRestRep : tenantList) {

            // grant TenantAdmin to root on the Tenant
            viPRCoreClient.tenants().updateRoleAssignments(tenantOrgRestRep.getId(), roleChanges);

            List<ProjectRestRep> projectList = viPRCoreClient.projects().getByTenant(tenantOrgRestRep.getId());
            for (ProjectRestRep projectRestRep : projectList) {
                viPRCoreClient.projects().deactivate(projectRestRep.getId());
            }

            viPRCoreClient.tenants().deactivate(tenantOrgRestRep.getId());
        }
    }

}
