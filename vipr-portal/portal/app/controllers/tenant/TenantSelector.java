/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import com.emc.vipr.client.exceptions.ServiceErrorException;
import controllers.Common;
import play.Logger;
import play.exceptions.ActionNotFoundException;
import play.mvc.Controller;
import play.mvc.Util;
import util.TenantUtils;

import controllers.security.Security;
import controllers.util.Models;

public class TenantSelector extends Controller {
    public static String CURRENT_TENANT_ID = "currentTenantId";
    public static String CURRENT_TENANT_NAME = "currentTenantName";
    public static String TENANTS = "tenants";
    public static String TENANT = "Tenant";

    public static void selectTenant(String tenantId, String url) {
        Models.setAdminTenantId(tenantId);

        if (url != null) {
            try {
                redirect(Common.toSafeRedirectURL(url));
            } catch (ActionNotFoundException noAction) {
                Logger.error(noAction, "Action not found for %s", url);
                badRequest();
            }
        }
    }

    @Util
    public static void addRenderArgs() {
        if (Security.isSecurityAdmin()) {
            renderArgs.put(TENANTS, TenantUtils.getSubTenantOptions());
        }
        else if (Security.isTenantAdmin()) {
            renderArgs.put(TENANTS, TenantUtils.getUserSubTenantOptions());
        }

        String tenantId = Models.currentAdminTenant();
        String tenantName = TENANT;

        // Add currently selected tenant information
        if (Security.isSystemMonitor() || Security.isTenantAdmin() || Security.isSecurityAdmin()) {
            try {
                tenantId = Models.currentAdminTenant();
                tenantName = getViprClient().tenants().get(uri(tenantId)).getName();
            } catch (ServiceErrorException tenantNotFound) {
                Models.resetAdminTenantId();
                tenantId = Models.currentAdminTenant();
                tenantName = getViprClient().tenants().get(uri(tenantId)).getName();
            }
        }
        renderArgs.put(CURRENT_TENANT_ID, tenantId);
        renderArgs.put(CURRENT_TENANT_NAME, tenantName);
    }

    /**
     * Adds all the options for the tenant selector to the render args.
     * The options include, all the active tenants and "ALL" to indicate
     * all the tenants and "NONE" to indicate no tenants. The options
     * "ALL" and "NONE" should always be first two options in the list.
     *
     */
    @Util
    public static void addRenderArgsForVcenterObjects() {
        renderTenantOptionsForVcenters();

        String tenantId = Models.currentAdminTenantForVcenter();
        String tenantName = TENANT;

        // Add currently selected tenant information
        if (Security.isSystemMonitor() || Security.isTenantAdmin() ||
                Security.isSecurityAdmin() || Security.isSystemAdmin()) {
            try {
                tenantId = Models.currentAdminTenantForVcenter();
                if (TenantUtils.getNoTenantSelector().equalsIgnoreCase(tenantId) ||
                        TenantUtils.getTenantSelectorForUnassigned().equalsIgnoreCase(tenantId)) {
                    tenantName = tenantId;
                } else {
                    tenantName = getViprClient().tenants().get(uri(tenantId)).getName();
                }
            } catch (ServiceErrorException tenantNotFound) {
                Models.resetAdminTenantId();
                tenantId = Models.currentAdminTenantForVcenter();
                tenantName = getViprClient().tenants().get(uri(tenantId)).getName();
            }
        }
        renderArgs.put(CURRENT_TENANT_ID, tenantId);
        renderArgs.put(CURRENT_TENANT_NAME, tenantName);
    }

    private static void renderTenantOptionsForVcenters() {
        if (Security.isSecurityAdmin() || Security.isSystemAdmin()) {
            renderArgs.put(TENANTS, TenantUtils.getSubTenantOptionsWithAdditionalTenants());
        } else if (Security.isTenantAdmin()) {
            renderArgs.put(TENANTS, TenantUtils.getUserSubTenantOptions());
        }
    }

    public static void selectVcenterTenant(String tenantId, String url) {
        Models.setVcenterAdminTenantId(tenantId);

        if (url != null) {
            try {
                redirect(Common.toSafeRedirectURL(url));
            } catch (ActionNotFoundException noAction) {
                Logger.error(noAction, "Action not found for %s", url);
                badRequest();
            }
        }
    }
}
