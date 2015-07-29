/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.CatalogPreferencesMapper.map;
import static com.emc.sa.api.mapper.CatalogPreferencesMapper.updateObject;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import javax.annotation.PostConstruct;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.utils.ValidationUtils;
import com.emc.sa.catalog.CatalogPreferenceManager;
import com.emc.storageos.db.client.model.uimodels.TenantPreferences;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.model.catalog.CatalogPreferencesRestRep;
import com.emc.vipr.model.catalog.CatalogPreferencesUpdateParam;

@Path("/catalog/preferences")
public class CatalogPreferenceService extends CatalogResourceService {

    private static final Logger log = Logger.getLogger(CatalogPreferenceService.class);

    private static final String EVENT_SERVICE_TYPE = "catalog-preferences";

    @Autowired
    private CatalogPreferenceManager catalogPreferenceManager;

    @PostConstruct
    public void init() {
        log.info("Initializing CatalogPreferenceService");
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("")
    public CatalogPreferencesRestRep get(@DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenantId) {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }
        verifyAuthorizedInTenantOrg(uri(tenantId), user);

        TenantPreferences catalogPreferences = catalogPreferenceManager.getPreferencesByTenant(tenantId);

        return map(catalogPreferences);
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("")
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public CatalogPreferencesRestRep update(CatalogPreferencesUpdateParam param) {

        String tenantId = param.getTenantId();
        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }
        verifyAuthorizedInTenantOrg(uri(tenantId), user);

        TenantPreferences tenantPreferences = catalogPreferenceManager.getPreferencesByTenant(tenantId);

        validateParam(uri(tenantId), param, tenantPreferences);

        updateObject(tenantPreferences, param);

        catalogPreferenceManager.updatePreferences(tenantPreferences);

        auditOpSuccess(OperationTypeEnum.UPDATE_CATALOG_PREFERENCES, tenantPreferences.auditParameters());

        tenantPreferences = catalogPreferenceManager.getPreferences(tenantPreferences.getId());

        return map(tenantPreferences);
    }

    private void validateParam(URI tenantId, CatalogPreferencesUpdateParam input, TenantPreferences existing) {
        if (StringUtils.isNotBlank(input.getApproverEmail())) {
            for (String email : StringUtils.split(input.getApproverEmail(), ",")) {
                email = StringUtils.trim(email);
                if (ValidationUtils.isValidEmail(email) == false) {
                    throw APIException.badRequests.propertyValueTypeIsInvalid("approver_email", "email");
                }
            }
        }
        if (StringUtils.isNotBlank(input.getApprovalUrl())) {
            try {
                URL url = new URL(input.getApprovalUrl());
                if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) {
                    throw APIException.badRequests.propertyValueTypeIsInvalid("approval_url", "url");
                }
                else if (!ValidationUtils.isValidHostNameOrIp(url.getHost())) {
                    throw APIException.badRequests.propertyValueTypeIsInvalid("approval_url", "url");
                }
            } catch (MalformedURLException e) {
                throw APIException.badRequests.propertyValueTypeIsInvalid("approval_url", "url");
            }
        }
    }

}
