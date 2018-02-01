/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.TenantSource;
import models.deadbolt.Role;
import models.security.UserInfo;

import org.apache.commons.lang.ObjectUtils;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Util;
import util.MessagesUtils;
import util.TenantUtils;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.google.common.collect.Lists;
import controllers.security.Security;

/**
 * Utility controller for handling many model-type queries.
 * 
 * @author jonnymiller
 */
public class Models extends Controller {

    public static String TENANT_ID = "currentTenant";
    private static String SOURCE = "currentSource";

    private static final Pattern TYPE_PATTERN = Pattern.compile("urn\\:storageos\\:([^\\:]+)");

    private static final Comparator<OrderLogRestRep> LOG_COMPARATOR = new Comparator<OrderLogRestRep>() {
        @Override
        public int compare(OrderLogRestRep arg0, OrderLogRestRep arg1) {
            return ObjectUtils.compare(arg0.getDate(), arg1.getDate());
        }
    };

    public static final CatalogServiceRestRep CORRUPTED_SERVICE = new CatalogServiceRestRep() {
        {
            setTitle(MessagesUtils.get("catalog.corrupted-service.title"));
            setImage("");
            setDescription(MessagesUtils.get("catalog.corrupted-service.description"));
        }
    };

    /**
     * Resets the AdminTenant in the session back to the users TenantId
     */
    @Util
    public static void resetAdminTenantId() {
        session.put(TENANT_ID, Security.getUserInfo().getTenant());
    }

    @Util
    public static void setAdminTenantId(String tenantId) {
        if (Models.canSelectTenant(tenantId)) {
            session.put(TENANT_ID, tenantId);
        } else {
            Logger.error("ACCESS-DENIED: User %s attempt to switch to tenant %s", Security.getUserInfo()
                    .getCommonName(), tenantId);
        }
    }

    @Util
    public static void setSource(String source) {
        session.put(SOURCE, source);
    }

    @Util
    public static String currentAdminTenant() {
        String sessionTenant = session.get(TENANT_ID);
        if (sessionTenant != null && canSelectTenant(sessionTenant)) {
            return validateSessionTenant(sessionTenant);
        } else {
            session.remove(TENANT_ID);
            UserInfo info = Security.getUserInfo();
            if (Security.isTenantAdmin() && !Security.isHomeTenantAdmin()) {
                for (URI tenant : info.getSubTenants()) {
                    String tenantId = tenant.toString();
                    if (info.hasSubTenantRole(tenantId, Security.TENANT_ADMIN)) {
                        return tenantId;
                    }
                }
            }
            // fallback to the home tenant if nothing else matches
            return info.getTenant();
        }
    }

    @Util
    public static String currentSource() {
            String sessionSource = session.get(SOURCE);
            if (sessionSource != null) {
                return sessionSource;
            } else {
                return TenantSource.TENANTS_SOURCE_ALL;
            }
    }

    public static String currentTenant() {
        return Security.getUserInfo().getTenant();
    }

    public static TenantOrgRestRep currentTenantOrg() {
        String tenant = Security.getUserInfo().getTenant();
        List<URI> ids = Lists.newArrayList();
        ids.add(URI.create(tenant));
        List<TenantOrgRestRep> tenants = util.TenantUtils.getAllTenants();
        if (tenants != null && tenants.isEmpty() == false) {
            return tenants.get(0);
        }
        return null;
    }

    private static URI getCurrentTenantId() {
        return URI.create(Security.getUserInfo().getTenant());
    }

    private static String getCurrentUserId() {
        return Security.getUserInfo().getIdentifier();
    }

    @Util
    public static String getTypeName(URI id) {
        return getTypeName(id.toString());
    }

    @Util
    public static String getTypeName(String id) {

        Matcher m = TYPE_PATTERN.matcher(id);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    @Util
    public static void checkAccess(URI tenantId) {
        if (!canAccessTenant(tenantId)) {
            forbidden();
        }
    }

    @Util
    public static void checkAccess(RelatedResourceRep tenantRep) {
        checkAccess(ResourceUtils.id(tenantRep));
    }

    @Util
    public static boolean canSelectTenant(String tenantId) {
        UserInfo info = Security.getUserInfo();
        if ((info.getTenant().equals(tenantId) && isAdministrator()) || Security.isSecurityAdmin()) {
            return true;
        }
        return info.hasSubTenantRole(tenantId, Security.TENANT_ADMIN);
    }

    @Util
    public static boolean canAccessTenant(URI tenantId) {
        if (tenantId == null) {
            return false;
        }
        return canAccessTenant(tenantId.toString());
    }

    @Util
    public static boolean canAccessTenant(String tenantId) {
        UserInfo info = Security.getUserInfo();
        if ((info.getTenant().equals(tenantId)) || Security.isSecurityAdmin()) {
            return true;
        }
        return info.containsTenant(tenantId);
    }

    @Util
    private static boolean isAdministrator() {
        for (Role role : Security.getUserInfo().getRoles()) {
            if (isRole(role, Security.SYSTEM_ADMIN) || isRole(role, Security.HOME_TENANT_ADMIN)) {
                return true;
            }
        }
        return false;
    }

    @Util
    private static boolean isRole(Role role, String value) {
        return role != null && role.getRoleName().equalsIgnoreCase(value);
    }

    @Util
    public static boolean canSelectTenantForVcenters(String tenantId) {
        UserInfo info = Security.getUserInfo();
        if ((info.getTenant().equals(tenantId) && isAdministrator()) || Security.isSystemAdmin()) {
            return true;
        }
        return info.hasSubTenantRole(tenantId, Security.TENANT_ADMIN);
    }

    @Util
    public static String currentAdminTenantForVcenter() {
        String sessionTenant = session.get(TENANT_ID);
        if (sessionTenant != null && canSelectTenantForVcenters(sessionTenant)) {
            return validateSessionTenantForVcenter(sessionTenant);
        } else {
            session.remove(TENANT_ID);
            UserInfo info = Security.getUserInfo();
            if (Security.isTenantAdmin() && !Security.isHomeTenantAdmin()) {
                for (URI tenant : info.getSubTenants()) {
                    String tenantId = tenant.toString();
                    if (info.hasSubTenantRole(tenantId, Security.TENANT_ADMIN)) {
                        return tenantId;
                    }
                }
            }
            // fallback to the home tenant if nothing else matches
            return info.getTenant();
        }
    }

    private static String validateSessionTenantForVcenter(String sessionTenant) {
        try{
            if (!(TenantUtils.getNoTenantSelector().equalsIgnoreCase(sessionTenant) ||
                    TenantUtils.getTenantSelectorForUnassigned().equalsIgnoreCase(sessionTenant)) &&
                    getViprClient().tenants().get(uri(sessionTenant)).getInactive()) {
                Models.resetAdminTenantId();
                sessionTenant = Models.currentAdminTenantForVcenter();
            }
        } catch (ServiceErrorException tenantNotFound) {
            Models.resetAdminTenantId();
            sessionTenant = Models.currentAdminTenantForVcenter();
        }
        return sessionTenant;
    }

    private static String validateSessionTenant(String sessionTenant) {
        try {
            if (TenantUtils.getNoTenantSelector().equalsIgnoreCase(sessionTenant) ||
                    TenantUtils.getTenantSelectorForUnassigned().equalsIgnoreCase(sessionTenant) ||
                    getViprClient().tenants().get(uri(sessionTenant)).getInactive()) {
                Models.resetAdminTenantId();
                sessionTenant = Models.currentAdminTenantForVcenter();
            }
        } catch (ServiceErrorException tenantNotFound) {
            Models.resetAdminTenantId();
            sessionTenant = Models.currentAdminTenantForVcenter();
        }
        return sessionTenant;
    }

    @Util
    public static void setVcenterAdminTenantId(String tenantId) {
        if (Models.canSelectTenantForVcenters(tenantId)) {
            session.put(TENANT_ID, tenantId);
        } else {
            Logger.error("ACCESS-DENIED: User %s attempt to switch to tenant %s", Security.getUserInfo()
                    .getCommonName(), tenantId);
        }
    }
}
