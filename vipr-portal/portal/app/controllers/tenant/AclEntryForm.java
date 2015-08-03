/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import java.util.List;

import models.RoleAssignmentType;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.google.common.collect.Lists;

import controllers.security.Security;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;

public class AclEntryForm {

    @MaxSize(128)
    @MinSize(2)
    public String aclName;

    public String type;

    public String access;

    public ACLEntry createACLEntry() {
        ACLEntry aclParamEntry = new ACLEntry();
        if (RoleAssignmentType.GROUP.name().equals(type)) {
            aclParamEntry.setGroup(aclName);
        }
        else if (RoleAssignmentType.USER.name().equals(type)) {
            aclParamEntry.setSubjectId(aclName);
        }

        if (StringUtils.isNotEmpty(access)) {
            aclParamEntry.getAces().add(access);
        }
        return aclParamEntry;
    }

    public PermissionsKey createPermissionKey() {
        String tenant = Security.getUserInfo().getTenant();
        PermissionsKey key = null;
        if (RoleAssignmentType.GROUP.name().equals(type)) {
            key = new PermissionsKey(PermissionsKey.Type.GROUP, aclName, tenant);
        }
        else if (RoleAssignmentType.USER.name().equals(type)) {
            key = new PermissionsKey(PermissionsKey.Type.SID, aclName, tenant);
        }
        return key;
    }

    public static List<AclEntryForm> loadAclEntryForms(List<ACLEntry> aclParamEntries) {
        List<AclEntryForm> aclEntries = Lists.newArrayList();

        if (aclParamEntries != null) {

            for (ACLEntry aclParamEntry : aclParamEntries) {
                for (String ace : aclParamEntry.getAces()) {

                    AclEntryForm aclEntryForm = new AclEntryForm();
                    if (StringUtils.isNotBlank(aclParamEntry.getGroup())) {
                        aclEntryForm.type = RoleAssignmentType.GROUP.name();
                        aclEntryForm.aclName = aclParamEntry.getGroup();
                    }
                    else if (StringUtils.isNotBlank(aclParamEntry.getSubjectId())) {
                        aclEntryForm.type = RoleAssignmentType.USER.name();
                        aclEntryForm.aclName = aclParamEntry.getSubjectId();
                    }
                    aclEntryForm.access = ace;

                    aclEntries.add(aclEntryForm);
                }
            }
        }

        return aclEntries;
    }

    public static List<ACLEntry> getAddedAcls(List<ACLEntry> currentAcls, List<AclEntryForm> aclEntries) {
        List<ACLEntry> added = Lists.newArrayList();
        for (AclEntryForm aclEntryForm : aclEntries) {
            if (aclEntryForm != null) {
                boolean found = false;
                ACLEntry aclParamEntry = aclEntryForm.createACLEntry();
                for (ACLEntry currentAcl : currentAcls) {
                    if (isSameACLEntry(aclParamEntry, currentAcl)) {
                        for (String acl : aclParamEntry.getAces()) {
                            if (currentAcl.getAces().contains(acl)) {
                                found = true;
                            }
                        }
                    }
                }
                if (found == false) {
                    added.add(aclParamEntry);
                }
            }
        }
        return added;
    }

    public static List<ACLEntry> getRemovedAcls(List<ACLEntry> currentAcls, List<AclEntryForm> aclEntries) {
        List<ACLEntry> removed = Lists.newArrayList();
        for (ACLEntry currentAcl : currentAcls) {
            boolean foundAclEntry = false;
            for (AclEntryForm aclEntryForm : aclEntries) {
                if (aclEntryForm != null) {
                    ACLEntry aclParamEntry = aclEntryForm.createACLEntry();
                    if (isSameACLEntry(aclParamEntry, currentAcl)) {
                        foundAclEntry = true;
                        for (String acl : currentAcl.getAces()) {
                            if (aclParamEntry.getAces().contains(acl) == false) {
                                ACLEntry removedACLEntry = new ACLEntry();
                                removedACLEntry.setTenant(currentAcl.getTenant());
                                removedACLEntry.setGroup(currentAcl.getGroup());
                                removedACLEntry.setSubjectId(currentAcl.getSubjectId());
                                removedACLEntry.getAces().add(acl);
                                removed.add(removedACLEntry);
                            }
                        }
                    }
                }
            }
            if (foundAclEntry == false) {
                for (String acl : currentAcl.getAces()) {
                    ACLEntry removedACLEntry = new ACLEntry();
                    removedACLEntry.setTenant(currentAcl.getTenant());
                    removedACLEntry.setGroup(currentAcl.getGroup());
                    removedACLEntry.setSubjectId(currentAcl.getSubjectId());
                    removedACLEntry.getAces().add(acl);
                    removed.add(removedACLEntry);
                }
            }
        }
        return removed;
    }

    public static boolean isSameACLEntry(ACLEntry left, ACLEntry right) {
        if (left == null && right == null) {
            return true;
        }
        else if (left != null && right != null) {
            if (StringUtils.equalsIgnoreCase(left.getTenant(), right.getTenant())) {
                if (StringUtils.isNotBlank(left.getGroup())
                        && StringUtils.equalsIgnoreCase(left.getGroup(), right.getGroup())) {
                    return true;
                }
                else if (StringUtils.isNotBlank(left.getSubjectId())
                        && StringUtils.equalsIgnoreCase(left.getSubjectId(), right.getSubjectId())) {
                    return true;
                }
            }
        }
        return false;
    }

}
