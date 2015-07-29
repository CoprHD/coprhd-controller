/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.security.validator.StorageOSPrincipal;
import com.emc.storageos.security.validator.Validator;
import com.emc.vipr.client.core.ACLResources;
import com.google.common.collect.Lists;
import controllers.tenant.AclEntryForm;
import controllers.util.Models;
import models.RoleAssignmentType;
import org.apache.commons.lang.StringUtils;
import play.data.validation.Validation;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ACLUtils {

    public static boolean isValidPrincipal(RoleAssignmentType type, String name) {
        StorageOSPrincipal principal = new StorageOSPrincipal();
        principal.setName(name);
        if (RoleAssignmentType.GROUP.equals(type)) {
            principal.setType(StorageOSPrincipal.Type.Group);
        }
        else if (RoleAssignmentType.USER.equals(type)) {
            principal.setType(StorageOSPrincipal.Type.User);
        }

        String tenant = Models.currentAdminTenant();
        return Validator.isValidPrincipal(principal, URI.create(tenant));
    }

    public static String extractDomainName(String name) {
        String domain = null;
        if (StringUtils.isNotBlank(name) && name.contains("@")) {
            domain = name.substring(name.indexOf("@") + 1, name.length());
        }
        return domain;
    }

    public static String stripDomainName(String name) {
        String userOrGroupName = null;
        if (StringUtils.isNotBlank(name) && name.contains("@")) {
            userOrGroupName = name.substring(0, name.indexOf("@"));
        }
        return userOrGroupName;
    }

    public static boolean validateDomain(String name) {
        return StringUtils.isNotBlank(extractDomainName(name));
    }

    public static boolean validateAuthProviderDomain(String name) {
        String domain = extractDomainName(name);
        List<AuthnProviderRestRep> authnProviderRestReps = AuthnProviderUtils.getAuthProvidersByDomainName(domain);
        return authnProviderRestReps != null && !authnProviderRestReps.isEmpty();
    }

    private static Pattern[] getCompiledPatterns(Set<String> valueSet) {
        String[] values = valueSet.toArray(new String[0]);
        Pattern[] compiledPatterns = null;
        if (values != null && values.length > 0) {
            compiledPatterns = new Pattern[values.length];
            int i = 0;
            for (String value : values) {
                compiledPatterns[i] = Pattern.compile(value.replace("*", ".*"), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                i++;
            }
        }
        return compiledPatterns;
    }

    private static boolean isGroupOnWhiteList(Set<String> valueSet, String groupId) {
        Pattern[] patterns = getCompiledPatterns(valueSet);
        if (patterns != null && patterns.length > 0) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(groupId).matches()) {
                    return true;
                }
            }
        } else {
            return true;
        }
        return false;
    }

    public static boolean validateActiveDirectoryGroupName(String name) {
        if (StringUtils.isNotBlank(name)) {
            String group = stripDomainName(name);
            String domain = extractDomainName(name);
            if (StringUtils.isNotBlank(group) && StringUtils.isNotBlank(domain)) {
                List<AuthnProviderRestRep> authnProviderRestReps = AuthnProviderUtils.getAuthProvidersByDomainName(domain);
                for (AuthnProviderRestRep authnProviderRestRep : authnProviderRestReps) {
                    if (isActiveDirectoryProvider(authnProviderRestRep)
                            && isGroupOnWhiteList(authnProviderRestRep.getGroupWhitelistValues(), group)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isActiveDirectoryProvider(AuthnProviderRestRep authnProviderRestRep) {
        return authnProviderRestRep != null && AuthSourceType.ad.equals(AuthSourceType.valueOf(authnProviderRestRep.getMode()));
    }

    public static void validateAclEntries(String fieldName, List<AclEntryForm> aclEntries) {
        for (int i = 0; i < aclEntries.size(); i++) {
            AclEntryForm aclEntryForm = aclEntries.get(i);
            if (aclEntryForm != null) {
                String aclEntryPath = fieldName + "[" + i + "]";

                String aclNamePath = aclEntryPath + ".aclName";
                if (StringUtils.isBlank(aclEntryForm.aclName)) {
                    Validation.addError(aclNamePath, "security.accessControlList.aclName.required");
                }

                if (StringUtils.isBlank(aclEntryForm.access)) {
                    String fieldPath = aclEntryPath + ".access";
                    Validation.addError(fieldPath, "security.accessControlList.access.required");
                }

                if (StringUtils.isBlank(aclEntryForm.type)) {
                    String fieldPath = aclEntryPath + ".type";
                    Validation.addError(fieldPath, "security.accessControlList.type.required");
                }
                else if (StringUtils.isNotBlank(aclEntryForm.aclName)) {
                    RoleAssignmentType type = RoleAssignmentType.valueOf(aclEntryForm.type);
                    if (RoleAssignmentType.GROUP.name().equals(aclEntryForm.type)) {
                        // Removed all the "@domain" suffix validation here to allow the
                        // user group configuration from the portal.
                        // All the required validations done in isValidPrincipal().
                        if (ACLUtils.isValidPrincipal(type, aclEntryForm.aclName) == false) {
                            Validation.addError(aclNamePath, "security.accessControlList.group.notvalid");
                        }
                    }
                    else if (RoleAssignmentType.USER.name().equals(aclEntryForm.type)) {
                        if (LocalUser.isLocalUser(aclEntryForm.aclName)) {
                            Validation.addError(aclNamePath, "security.accessControlList.localuser.notpermitted");
                        }
                        else if (validateDomain(aclEntryForm.aclName) == false) {
                            Validation.addError(aclNamePath, "security.accessControlList.domain.required");
                        }
                        else if (validateAuthProviderDomain(aclEntryForm.aclName) == false) {
                            Validation.addError(aclNamePath, "security.accessControlList.domain.notfound");
                        }
                        else if (ACLUtils.isValidPrincipal(type, aclEntryForm.aclName) == false) {
                            Validation.addError(aclNamePath, "security.accessControlList.user.notvalid");
                        }
                    }
                }

            }
        }
    }

    public static List<AclEntryForm> convertToAclEntryForms(List<ACLEntry> acls) {
        List<AclEntryForm> aclEntries = Lists.newArrayList();
        if (acls != null && acls.isEmpty() == false) {
            for (ACLEntry acl : acls) {
                for (String role : acl.getAces()) {
                    AclEntryForm entry = new AclEntryForm();
                    if (StringUtils.isNotBlank(acl.getGroup())) {
                        entry.type = RoleAssignmentType.GROUP.name();
                        entry.aclName = acl.getGroup();
                    } else if (StringUtils.isNotBlank(acl.getSubjectId())) {
                        entry.type = RoleAssignmentType.USER.name();
                        entry.aclName = acl.getSubjectId();
                    }
                    entry.access = role;
                    aclEntries.add(entry);
                }
            }
        }
        return aclEntries;
    }

    public static void updateACLs(ACLResources aclResource, URI id, List<AclEntryForm> aclEntries) {
        if (aclResource != null) {
            List<ACLEntry> currentAcls = aclResource.getACLs(id);
            List<ACLEntry> addACLs = AclEntryForm.getAddedAcls(currentAcls, aclEntries);
            List<ACLEntry> removeACLs = AclEntryForm.getRemovedAcls(currentAcls, aclEntries);

            ACLAssignmentChanges aclChanges = new ACLAssignmentChanges(addACLs, removeACLs);
            aclResource.updateACLs(id, aclChanges);
        }
    }

}
