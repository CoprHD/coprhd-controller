/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.Collections;
import java.util.List;
import util.StringOption;
import com.google.common.collect.Lists;
import controllers.security.Security;

public class Roles {
    public static String[] VDC_ROLES = { Security.SYSTEM_ADMIN, Security.SECURITY_ADMIN, Security.SYSTEM_AUDITOR,
            Security.SYSTEM_MONITOR };
    public static String[] TENANT_ROLES = { Security.TENANT_ADMIN, Security.PROJECT_ADMIN, Security.TENANT_APPROVER };
    public static String[] ALL_ROLES = { Security.SYSTEM_ADMIN, Security.SECURITY_ADMIN, Security.SYSTEM_AUDITOR,
            Security.SYSTEM_MONITOR, Security.TENANT_ADMIN, Security.PROJECT_ADMIN,
            Security.TENANT_APPROVER };

    public static boolean isSecurityAdmin(String role) {
        return Security.SECURITY_ADMIN.equals(role);
    }

    public static boolean isSystemAdmin(String role) {
        return Security.SYSTEM_ADMIN.equals(role);
    }

    public static boolean isSystemAuditor(String role) {
        return Security.SYSTEM_AUDITOR.equals(role);
    }

    public static boolean isSystemMonitor(String role) {
        return Security.SYSTEM_MONITOR.equals(role);
    }

    public static boolean isTenantAdmin(String role) {
        return Security.TENANT_ADMIN.equals(role);
    }

    public static boolean isProjectAdmin(String role) {
        return Security.PROJECT_ADMIN.equals(role);
    }

    public static boolean isTenantApprover(String role) {
        return Security.TENANT_APPROVER.equals(role);
    }

    public static boolean isVdcRole(String role) {
        return isSecurityAdmin(role) || isSystemAdmin(role) || isSystemAuditor(role) || isSystemMonitor(role);
    }

    public static boolean isTenantRole(String role) {
        return isTenantAdmin(role) || isProjectAdmin(role) || isTenantApprover(role);
    }

    public static StringOption option(String type) {
        return new StringOption(type, getDisplayValue(type));
    }

    public static List<StringOption> options(String... types) {
        List<StringOption> options = Lists.newArrayList();
        for (String type : types) {
            options.add(option(type));
        }
        Collections.sort(options);
        return options;
    }

    public static String getDisplayValue(String type) {
        return StringOption.getDisplayValue(type, "Role");
    }
}
