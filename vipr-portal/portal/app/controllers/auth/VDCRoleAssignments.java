/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.auth;

import static util.RoleAssignmentUtils.createRoleAssignmentEntry;
import static util.RoleAssignmentUtils.deleteVDCRoleAssignment;
import static util.RoleAssignmentUtils.getVDCRoleAssignment;
import static util.RoleAssignmentUtils.getVDCRoleAssignments;
import static util.RoleAssignmentUtils.putVdcRoleAssignmentChanges;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.util.FlashException;
import models.ACLs;
import models.RoleAssignmentType;
import models.Roles;
import models.datatable.VDCRoleAssignmentDataTable;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.EnumOption;
import util.MessagesUtils;
import util.RoleAssignmentUtils;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class VDCRoleAssignments extends Controller {

    @Util
    private static void addRolesToRenderArgs() {
        renderArgs.put("roles", Roles.options(Roles.VDC_ROLES));
    }

    public static void list() {
        VDCRoleAssignmentDataTable dataTable = new VDCRoleAssignmentDataTable();
        render(dataTable);
    }

    @FlashException("list")
    public static void listJson() {
        List<RoleAssignmentEntry> viprRoleAssignments = getVDCRoleAssignments();

        List<VDCRoleAssignmentDataTable.RoleInfo> roles = Lists.newArrayList();
        for (RoleAssignmentEntry viprRoleAssignment : viprRoleAssignments) {
            roles.add(new VDCRoleAssignmentDataTable.RoleInfo(viprRoleAssignment));
        }
        renderJSON(DataTablesSupport.createJSON(roles, params));
    }

    @FlashException("list")
    public static void create() {
        addRolesToRenderArgs();
        VDCRoleAssignmentForm roleAssignment = new VDCRoleAssignmentForm();
        render("@edit", roleAssignment);
    }

    @FlashException("list")
    public static void edit(@Required String id) {
        String name = VDCRoleAssignmentForm.extractNameFromId(id);
        RoleAssignmentType type = VDCRoleAssignmentForm.extractTypeFromId(id);
        RoleAssignmentEntry roleAssignmentEntry = getVDCRoleAssignment(name, type);
        if (roleAssignmentEntry != null) {
            addRolesToRenderArgs();

            Boolean isRootUser = RoleAssignmentUtils.isRootUser(roleAssignmentEntry);

            VDCRoleAssignmentForm roleAssignment = new VDCRoleAssignmentForm();
            roleAssignment.id = id;
            roleAssignment.readFrom(roleAssignmentEntry);
            render(roleAssignment, isRootUser);
        }
        else {
            flash.error(MessagesUtils.get("roleAssignments.unknown", name));
            list();
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(VDCRoleAssignmentForm roleAssignment) {
        roleAssignment.validate("roleAssignment");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        roleAssignment.save();
        flash.success(MessagesUtils.get("roleAssignments.saved", roleAssignment.name));

        if (RoleAssignmentType.USER.equals(roleAssignment.type)
                && Security.getUserInfo().getIdentifier().equalsIgnoreCase(roleAssignment.name)) {
            Security.clearUserInfo();
        }
        list();
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        boolean wasCurrentUserRoleAssignmentsDeleted = false;
        if (ids != null && ids.length > 0) {
            boolean deletedRoleAssignment = false;
            for (String id : ids) {
                String name = VDCRoleAssignmentForm.extractNameFromId(id);
                RoleAssignmentType type = VDCRoleAssignmentForm.extractTypeFromId(id);

                if (RoleAssignmentUtils.isRootUser(type, name)) {
                    flash.put("warningMessage", MessagesUtils.get("roleAssignments.rootNotDeleted"));
                }
                else {
                    if (name.equalsIgnoreCase(Security.getUserInfo().getIdentifier())) {
                        wasCurrentUserRoleAssignmentsDeleted = true;
                    }
                    deleteVDCRoleAssignment(type, name);
                    deletedRoleAssignment = true;
                }

            }
            if (deletedRoleAssignment) {
                flash.success(MessagesUtils.get("roleAssignments.deleted"));
            }
        }
        if (wasCurrentUserRoleAssignmentsDeleted) {
            Security.clearUserInfo();
            redirect("/");
        }
        else {
            list();
        }
    }

    @Util
    private static void addRenderArgs() {
        renderArgs.put("acls", ACLs.options(ACLs.ALL, ACLs.BACKUP));

        Set<EnumOption> aclTypes = new TreeSet<EnumOption>();
        aclTypes.addAll(Arrays.asList(EnumOption.options(RoleAssignmentType.values(), "RoleAssignmentType")));
        renderArgs.put("aclTypes", aclTypes);
    }

    public static class VDCRoleAssignmentForm {

        private static final String ID_DELIMITER = "~~~~";

        public String id;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        public RoleAssignmentType type = RoleAssignmentType.GROUP;

        public Boolean systemAdmin = Boolean.FALSE;

        public Boolean securityAdmin = Boolean.FALSE;

        public Boolean systemMonitor = Boolean.FALSE;

        public Boolean systemAuditor = Boolean.FALSE;

        public static String createId(String name, RoleAssignmentType type) {
            return name + ID_DELIMITER + type.name();
        }

        public static String extractNameFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 2) {
                    return parts[0];
                }
                else {
                    return id;
                }
            }
            return null;
        }

        public static RoleAssignmentType extractTypeFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 2) {
                    return RoleAssignmentType.valueOf(parts[1]);
                }
            }
            return null;
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String formName) {
            Validation.valid(formName, this);

            if (isNew()) {
                // Ensure we aren't overwriting an existing role assignment with this new one
                RoleAssignmentEntry roleAssignmentEntry = getVDCRoleAssignment(name, type);
                if (roleAssignmentEntry != null) {
                    Validation.addError(formName + ".name", Messages.get("roleAssignments." + type + ".alreadyExists"));
                }
                
                boolean atLeastOneChecked = systemAdmin || securityAdmin || systemMonitor || systemAuditor;
                if (atLeastOneChecked == false) {
                    flash.error(Messages.get("roleAssignments.atLeastOneChecked"));
                    Validation.addError(formName, Messages.get("roleAssignments.atLeastOneChecked"));  // here to fail validation
                }
            }

        }

        public void save() {
            RoleAssignmentEntry roleAssignmentEntry = getVDCRoleAssignment(name, type);

            if (Security.isSecurityAdminOrRestrictedSecurityAdmin()) {
                List<RoleAssignmentEntry> vdcRolesToAdd = Lists.newArrayList();
                List<RoleAssignmentEntry> vdcRolesToRemove = Lists.newArrayList();
                writeVdcRoleChangesTo(roleAssignmentEntry, vdcRolesToAdd, vdcRolesToRemove);
                if (!vdcRolesToAdd.isEmpty() || !vdcRolesToRemove.isEmpty()) {
                    putVdcRoleAssignmentChanges(vdcRolesToAdd, vdcRolesToRemove);
                }
            }
        }

        public void readFrom(RoleAssignmentEntry bourneRoleAssignment) {
            if (StringUtils.isNotBlank(bourneRoleAssignment.getSubjectId())) {
                name = bourneRoleAssignment.getSubjectId();
                type = RoleAssignmentType.USER;
            }
            else {
                name = bourneRoleAssignment.getGroup();
                type = RoleAssignmentType.GROUP;
            }
            id = createId(name, type);

            systemAdmin = isRoleAssigned(bourneRoleAssignment, Security.SYSTEM_ADMIN);
            securityAdmin = isRoleAssigned(bourneRoleAssignment, Security.SECURITY_ADMIN);
            systemMonitor = isRoleAssigned(bourneRoleAssignment, Security.SYSTEM_MONITOR);
            systemAuditor = isRoleAssigned(bourneRoleAssignment, Security.SYSTEM_AUDITOR);
        }

        private boolean isRoleAssigned(RoleAssignmentEntry roleAssignParamEntry, String findRole) {
            if (roleAssignParamEntry.getRoles() != null && findRole != null) {
                return roleAssignParamEntry.getRoles().contains(findRole);
            }
            return false;
        }

        private boolean hasRoleChanged(RoleAssignmentEntry roleAssignParamEntry, String findRole) {
            if (roleAssignParamEntry != null) {
                boolean value = isRoleAssigned(roleAssignParamEntry, findRole);
                if (Roles.isSystemAdmin(findRole)) {
                    return systemAdmin != value;
                }
                else if (Roles.isSecurityAdmin(findRole)) {
                    return securityAdmin != value;
                }
                else if (Roles.isSystemMonitor(findRole)) {
                    return systemMonitor != value;
                }
                else if (Roles.isSystemAuditor(findRole)) {
                    return systemAuditor != value;
                }
            }
            return true;
        }

        public void writeVdcRoleChangesTo(RoleAssignmentEntry roleAssignmentEntry, List<RoleAssignmentEntry> add,
                List<RoleAssignmentEntry> remove) {
            RoleAssignmentEntry rae = createRoleAssignmentEntry(type, name, Security.SYSTEM_ADMIN);
            if (hasRoleChanged(roleAssignmentEntry, Security.SYSTEM_ADMIN)) {
                if (systemAdmin != null && systemAdmin == true) {
                    add.add(rae);
                }
                else {
                    remove.add(rae);
                }
            }

            rae = createRoleAssignmentEntry(type, name, Security.SYSTEM_MONITOR);
            if (hasRoleChanged(roleAssignmentEntry, Security.SYSTEM_MONITOR)) {
                if (systemMonitor != null && systemMonitor == true) {
                    add.add(rae);
                }
                else {
                    remove.add(rae);
                }
            }

            rae = createRoleAssignmentEntry(type, name, Security.SECURITY_ADMIN);
            if (hasRoleChanged(roleAssignmentEntry, Security.SECURITY_ADMIN)) {
                if (securityAdmin != null && securityAdmin == true) {
                    add.add(rae);
                }
                else {
                    remove.add(rae);
                }
            }

            rae = createRoleAssignmentEntry(type, name, Security.SYSTEM_AUDITOR);
            if (hasRoleChanged(roleAssignmentEntry, Security.SYSTEM_AUDITOR)) {
                if (systemAuditor != null && systemAuditor == true) {
                    add.add(rae);
                }
                else {
                    remove.add(rae);
                }
            }
        }

    }

}