/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;

import com.emc.vipr.client.exceptions.ServiceErrorException;
import controllers.Common;
import play.Logger;
import play.Play;
import play.exceptions.ActionNotFoundException;
import play.mvc.Controller;
import play.mvc.Util;
import util.TenantUtils;

import com.emc.storageos.model.tenant.TenantOrgRestRep;

import controllers.security.Security;
import controllers.util.Models;

public class TenantSelector extends Controller {
    public static String CURRENT_TENANT_ID = "currentTenantId";
    public static String CURRENT_TENANT_NAME = "currentTenantName";

    public static void selectTenant(String tenantId, String url) {
        Models.setAdminTenantId(tenantId);

        if (url != null) {
            try {
                redirect(Common.toSafeRedirectURL(url));
            }  catch(ActionNotFoundException noAction) {
                Logger.error(noAction, "Action not found for %s",url);
                badRequest();
            }
        }
    }

    @Util public static void addRenderArgs() {
        if (Security.isSecurityAdmin()) {
            renderArgs.put("tenants", TenantUtils.getSubTenantOptions());
        }
        else if (Security.isTenantAdmin()) {
            renderArgs.put("tenants", TenantUtils.getUserSubTenantOptions());
        }

        String tenantId = Models.currentAdminTenant();
        String tenantName = "Tenant";

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
}
