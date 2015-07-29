/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.services.util.SecurityUtils;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.emc.vipr.model.catalog.AssetDependencyRequest;
import com.emc.vipr.model.catalog.AssetDependencyResponse;
import com.emc.vipr.model.catalog.AssetOption;
import com.emc.vipr.model.catalog.AssetOptionsRequest;
import com.emc.vipr.model.catalog.AssetOptionsResponse;

@Path("/catalog/asset-options")
public class AssetOptionService extends CatalogResourceService {

    private static final Logger log = Logger.getLogger(AssetOptionService.class);

    @Autowired
    private AssetOptionsManager assetOptionsManager;

    @PostConstruct
    public void init() {
        log.info("Initializing AssetOptions");
    }

    private AssetOptionsContext createAssetOptionsContext(AssetOptionsRequest request) {
        StorageOSUser user = getUserFromContext();
        AssetOptionsContext context = assetOptionsManager.createDefaultContext(user);

        // Override the tenant if specified in the request
        URI tenantId = request.getTenantId();
        if (tenantId != null) {
            verifyAuthorizedInTenantOrg(tenantId, user);
            context.setTenant(tenantId);
        }
        return context;
    }

    @POST
    @Path("/{assetType}")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public AssetOptionsResponse getAssetOptions(@PathParam("assetType") String assetType, AssetOptionsRequest request) {
        final Map<String, String> availableAssets = request.getAvailableAssets();
        final AssetOptionsContext context = createAssetOptionsContext(request);
        final Map<String, String> sanitizedAvailableAssets = SecurityUtils.stripMapXSS(availableAssets);
        final String sanitizedAssetType = SecurityUtils.stripXSS(assetType);

        log.info("Retrieving asset options for " + sanitizedAssetType + " with available assets : "
                + StringUtils.join(sanitizedAvailableAssets.keySet(), ", "));

        try {
            List<AssetOption> options = assetOptionsManager.getOptions(context, sanitizedAssetType, sanitizedAvailableAssets);

            AssetOptionsResponse response = new AssetOptionsResponse();
            response.setAssetType(sanitizedAssetType);
            response.setAvailableAssets(sanitizedAvailableAssets);
            response.setOptions(options);

            return response;
        } catch (IllegalStateException e) {
            Response response = Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
            throw new WebApplicationException(response);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                Response response = Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
                throw new WebApplicationException(response);
            }
            throw new WebApplicationException(e);
        }
    }

    @POST
    @Path("/{assetType}/dependencies")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public AssetDependencyResponse getAssetDependencies(@PathParam("assetType") String assetType,
            AssetDependencyRequest request) {

        final Set<String> availableAssetTypes = request.getAvailableAssetTypes();
        final String sanitizedAssetType = SecurityUtils.stripXSS(assetType);

        log.info("Retrieving asset dependencies for " + sanitizedAssetType + " with available assets : "
                + StringUtils.join(availableAssetTypes, ", "));

        List<String> dependencies = assetOptionsManager.getAssetDependencies(sanitizedAssetType, availableAssetTypes);

        AssetDependencyResponse response = new AssetDependencyResponse();
        response.setAssetType(sanitizedAssetType);
        response.setAssetDependencies(dependencies);
        return response;
    }
}
