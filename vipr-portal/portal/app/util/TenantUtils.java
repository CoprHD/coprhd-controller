/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.tenant.TenantUpdateParam;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

import controllers.security.Security;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

public class TenantUtils {
    //To represent the two additional options in the
    //tenant selector.
    public static String ALL_TENANT_RESOURCES = "ALL";
    public static String TENANT_RESOURCES_WITH_NO_TENANTS = "NONE";

    public static boolean canReadAllTenants() {
        return Security.hasAnyRole(Security.ROOT_TENANT_ADMIN, Security.SECURITY_ADMIN, Security.SYSTEM_MONITOR);
    }

    public static boolean canReadAllTenantsForVcenters() {
        return Security.hasAnyRole(Security.ROOT_TENANT_ADMIN, Security.SECURITY_ADMIN,
                Security.SYSTEM_MONITOR, Security.SYSTEM_ADMIN);
    }

    public static List<TenantOrgRestRep> getAllTenants() {
        List<TenantOrgRestRep> tenants = Lists.newArrayList();
        TenantOrgRestRep rootTenant = findRootTenant();
        tenants.add(rootTenant);
        tenants.addAll(getSubTenants(id(rootTenant)));
        return tenants;
    }

    public static List<TenantOrgRestRep> getSubTenants(String parentTenantId) {
        return getSubTenants(uri(parentTenantId));
    }

    public static List<TenantOrgRestRep> getSubTenants(URI parentTenantId) {
        return getViprClient().tenants().getAllSubtenants(parentTenantId);
    }

    public static TenantOrgRestRep findRootTenant() {
        URI userTenantId = getViprClient().getUserTenantId();

        TenantOrgRestRep currentTenant = getViprClient().tenants().get(userTenantId);
        while (currentTenant.getParentTenant() != null) {
            currentTenant = getViprClient().tenants().get(currentTenant.getParentTenant());
        }

        return currentTenant;
    }

    public static TenantOrgRestRep getTenant(String tenantId) {
        try {
            return getViprClient().tenants().get(uri(tenantId));
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static void update(String tenantId, TenantUpdateParam tenantUpdateParam) {
        getViprClient().tenants().update(uri(tenantId), tenantUpdateParam);
    }

    public static TenantOrgRestRep create(TenantCreateParam tenantCreateParam) {
        return getViprClient().tenants().create(tenantCreateParam);
    }

    public static boolean isRootTenant(URI tenantId) {
        return isRootTenant(getViprClient().tenants().get(tenantId));
    }

    public static boolean isRootTenant(TenantOrgRestRep tenant) {
        return tenant != null && tenant.getParentTenant() == null;
    }

    public static QuotaInfo getQuota(URI id) {
        return getViprClient().tenants().getQuota(id);
    }

    public static QuotaInfo getQuota(String id) {
        return getQuota(uri(id));
    }

    public static QuotaInfo updateQuota(String id, boolean enable, Long sizeInGB) {
        if (enable) {
            return enableQuota(id, sizeInGB);
        }
        else {
            return disableQuota(id);
        }
    }

    public static QuotaInfo enableQuota(String id, Long sizeInGB) {
        return getViprClient().tenants().updateQuota(uri(id), new QuotaUpdateParam(true, sizeInGB));
    }

    public static QuotaInfo disableQuota(String id) {
        return getViprClient().tenants().updateQuota(uri(id), new QuotaUpdateParam(false, null));
    }

    public static boolean deactivate(URI tenantId) {
        if (tenantId != null) {
            if (!isRootTenant(tenantId)) {
                getViprClient().tenants().deactivate(tenantId);
                return true;
            }
        }
        return false;
    }

    public static List<StringOption> getSubTenantOptions() {
        List<StringOption> options = Lists.newArrayList();

        TenantOrgRestRep userTenant = getViprClient().tenants().get(uri(Security.getUserInfo().getTenant()));
        options.add(createTenantOption(userTenant));

        for (TenantOrgRestRep tenant : getViprClient().tenants().getAllSubtenants(uri(Security.getUserInfo().getTenant()))) {
            options.add(createTenantOption(tenant));
        }
        Collections.sort(options);

        return options;
    }

    public static TenantOrgRestRep getUserTenant() {
        return getViprClient().tenants().get(uri(Security.getUserInfo().getTenant()));
    }

    public static List<StringOption> getUserSubTenantOptions() {
        List<StringOption> options = Lists.newArrayList();

        if (Security.hasAnyRole(Security.SECURITY_ADMIN, Security.RESTRICTED_SECURITY_ADMIN, Security.HOME_TENANT_ADMIN)) {
            TenantOrgRestRep userTenant = getViprClient().tenants().get(uri(Security.getUserInfo().getTenant()));
            options.add(createTenantOption(userTenant));
        }

        for (TenantOrgRestRep tenant : getViprClient().tenants().getByIds(Security.getUserInfo().getSubTenants())) {
            options.add(createTenantOption(tenant));
        }

        return options;
    }

    private static StringOption createTenantOption(TenantOrgRestRep tenant) {
        return new StringOption(tenant.getId().toString(), tenant.getName());
    }

    private static StringOption createTenantOption(String tenantId, String name) {
        return new StringOption(tenantId, name);
    }

    /**
     * Creates a list tenant selector options. But these options are
     * not the actual tenants. They are just added to filter the
     * vCenters, Clusters and Hosts in the UI.
     *
     * @return list of additional tenant selector options.
     */
    public static List<StringOption> getAdditionalTenantOptions() {
        List<StringOption> options = Lists.newArrayList();
        options.add(createTenantOption(ALL_TENANT_RESOURCES, ALL_TENANT_RESOURCES));
        options.add(createTenantOption(TENANT_RESOURCES_WITH_NO_TENANTS, TENANT_RESOURCES_WITH_NO_TENANTS));

        Collections.sort(options);

        return options;
    }

    /**
     * Creates a list tenant selector options. This includes both
     * the actual tenants an the additional tenant options.
     *
     * @return list of tenant with additional tenant selector options.
     */
    public static List<StringOption> getSubTenantOptionsWithAdditionalTenants() {
        List<StringOption> options = getAdditionalTenantOptions();
        options.addAll(getSubTenantOptions());

        return options;
    }
}
