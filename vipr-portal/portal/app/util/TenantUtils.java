/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.model.object.ObjectNamespaceList;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.tenant.TenantCreateParam;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.tenant.TenantUpdateParam;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

import controllers.security.Security;

import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

public class TenantUtils {
    //To represent the two additional options in the
    //tenant selector.
    private static final String NO_TENANT_SELECTOR = "[No-Filter]";
    private static final String TENANT_SELECTOR_FOR_UNASSIGNED = "[Not-Assigned]";
    private static final String API_NO_TENANT_SELECTOR = "No-Filter";
    private static final String API_TENANT_SELECTOR_FOR_UNASSIGNED = "Not-Assigned";

    public static boolean canReadAllTenants() {
        return Security.hasAnyRole(Security.ROOT_TENANT_ADMIN, Security.SECURITY_ADMIN, Security.SYSTEM_MONITOR);
    }

    public static boolean canReadAllTenantsForVcenters() {
        return Security.hasAnyRole(Security.ROOT_TENANT_ADMIN, Security.SYSTEM_MONITOR, Security.SYSTEM_ADMIN);
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
    
    /*public static List<StringOption> getAllNamespace() {
        ObjectNamespaceList objNamespaceList = getViprClient().names().getNamespaces();
        
    }*/

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
        options.add(createTenantOption(NO_TENANT_SELECTOR, NO_TENANT_SELECTOR));
        options.add(createTenantOption(TENANT_SELECTOR_FOR_UNASSIGNED, TENANT_SELECTOR_FOR_UNASSIGNED));

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

    /**
     * Converts the portal tenant filter to match the API tenant
     * filter. In portal the tenant filter are
     * "[No Filter]" - to list all the vCenters in the system.
     * "[Not Assigned]" - to list all the vCenters with no tenants assigned.
     * But, in API, they are slightly different.
     * "ALL" - to list all the vCenters in the system.
     * "NONE" - to list all the vCenters with no tenants assigned.
     *
     * @param tenantId to be converted to the api level filter.
     *
     * @return returns the corresponding api level filter to the tenantId.
     */
    public static URI getTenantFilter(String tenantId) {
        URI tenantFilter;
        if (StringUtils.isNotBlank(tenantId) &&
                tenantId.equalsIgnoreCase(NO_TENANT_SELECTOR)) {
            tenantFilter = uri(API_NO_TENANT_SELECTOR);
        } else if (StringUtils.isNotBlank(tenantId) &&
                tenantId.equalsIgnoreCase(TENANT_SELECTOR_FOR_UNASSIGNED)){
            tenantFilter = uri(API_TENANT_SELECTOR_FOR_UNASSIGNED);
        } else {
            tenantFilter = uri(tenantId);
        }

        return tenantFilter;
    }

    public static String getNoTenantSelector() {
        return NO_TENANT_SELECTOR;
    }

    public static String getTenantSelectorForUnassigned() {
        return TENANT_SELECTOR_FOR_UNASSIGNED;
    }
}
