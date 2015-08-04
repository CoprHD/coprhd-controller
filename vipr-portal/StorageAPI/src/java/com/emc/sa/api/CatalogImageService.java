/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.CatalogImageMapper.createNewObject;
import static com.emc.sa.api.mapper.CatalogImageMapper.map;
import static com.emc.sa.api.mapper.CatalogImageMapper.updateObject;
import static com.emc.storageos.db.client.URIUtil.uri;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.CatalogImageFilter;
import com.emc.sa.api.mapper.CatalogImageMapper;
import com.emc.sa.catalog.CatalogImageManager;
import com.emc.storageos.db.client.model.uimodels.CatalogImage;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.model.catalog.CatalogImageBulkRep;
import com.emc.vipr.model.catalog.CatalogImageCommonParam;
import com.emc.vipr.model.catalog.CatalogImageCreateParam;
import com.emc.vipr.model.catalog.CatalogImageList;
import com.emc.vipr.model.catalog.CatalogImageRestRep;
import com.emc.vipr.model.catalog.CatalogImageUpdateParam;

@DefaultPermissions(
        read_roles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        write_roles = { Role.TENANT_ADMIN },
        read_acls = { ACL.ANY })
@Path("/catalog/images")
public class CatalogImageService extends CatalogTaggedResourceService {

    private static final String EVENT_SERVICE_TYPE = "catalog-image";

    @Autowired
    private CatalogImageManager catalogImageManager;

    @Autowired
    private RecordableEventManager eventManager;

    @Override
    protected CatalogImage queryResource(URI id) {
        return getCatalogImageById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        CatalogImage catalogImage = queryResource(id);
        return uri(catalogImage.getTenant());
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.CATALOG_IMAGE;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get info for catalog image
     * 
     * @param id the URN of a Catalog Image
     * @prereq none
     * @brief Show catalog image
     * @return Catalog Image details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public CatalogImageRestRep getCatalogImage(@PathParam("id") URI id) {
        CatalogImage catalogImage = queryResource(id);
        return map(catalogImage);
    }

    /**
     * Creates a new catalog image
     * 
     * @param createParam
     *            the parameter to create a new catalog image
     * @prereq none
     * @brief Create Catalog Image
     * @return none
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    @Path("")
    public CatalogImageRestRep createCatalogImage(CatalogImageCreateParam createParam) {

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(createParam.getTenant(), user);

        validateParam(createParam.getTenant(), createParam, null);

        CatalogImage catalogImage = createNewObject(createParam.getTenant(), createParam);

        catalogImageManager.createCatalogImage(catalogImage);

        auditOpSuccess(OperationTypeEnum.CREATE_CATALOG_IMAGE, catalogImage.auditParameters());

        return map(catalogImage);
    }

    /**
     * Update catalog image
     * 
     * @param param Catalog Image update parameters
     * @param id the URN the catalog image
     * @prereq none
     * @brief Update Catalog Image
     * @return No data returned in response body
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public CatalogImageRestRep updateCatalogImage(@PathParam("id") URI id, CatalogImageUpdateParam param) {
        CatalogImage catalogImage = getCatalogImageById(id, true);

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(catalogImage.getTenant()), user);

        validateParam(uri(catalogImage.getTenant()), param, catalogImage);

        updateObject(catalogImage, param);

        catalogImageManager.updateCatalogImage(catalogImage);

        auditOpSuccess(OperationTypeEnum.UPDATE_CATALOG_IMAGE, catalogImage.auditParameters());

        catalogImage = catalogImageManager.getCatalogImageById(catalogImage.getId());

        return map(catalogImage);
    }

    /**
     * Deactivates the catalog image
     * 
     * @param id the URN of an catalog image to be deactivated
     * @brief Deactivate Catalog Image
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public Response deactivateCatalogImage(@PathParam("id") URI id) throws DatabaseException {
        CatalogImage catalogImage = queryResource(id);
        ArgValidator.checkEntity(catalogImage, id, true);

        catalogImageManager.deleteCatalogImage(catalogImage);

        auditOpSuccess(OperationTypeEnum.DELETE_CATALOG_IMAGE, catalogImage.auditParameters());

        return Response.ok().build();
    }

    /**
     * Gets the list of catalog images
     * 
     * @param tenantId the URN of a tenant
     * @brief List Catalog Images
     * @return a list of catalog images
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CatalogImageList getCatalogImages(@DefaultValue("") @QueryParam(SearchConstants.TENANT_ID_PARAM) String tenantId)
            throws DatabaseException {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = user.getTenantId();
        }

        verifyAuthorizedInTenantOrg(uri(tenantId), getUserFromContext());

        List<CatalogImage> catalogImages = catalogImageManager.getCatalogImages(uri(tenantId));

        CatalogImageList list = new CatalogImageList();
        for (CatalogImage catalogImage : catalogImages) {
            NamedRelatedResourceRep resourceRep = toNamedRelatedResource(ResourceTypeEnum.CATALOG_IMAGE,
                    catalogImage.getId(), catalogImage.getLabel());
            list.getCatalogImages().add(resourceRep);
        }

        return list;
    }

    private CatalogImage getCatalogImageById(URI id, boolean checkInactive) {
        CatalogImage catalogImage = catalogImageManager.getCatalogImageById(id);
        ArgValidator.checkEntity(catalogImage, id, isIdEmbeddedInURL(id), checkInactive);
        return catalogImage;
    }

    private void validateParam(URI tenantId, CatalogImageCommonParam input, CatalogImage existing) {

    }

    /**
     * List data for the specified catalog images.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of specified images
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public CatalogImageBulkRep getBulkResources(BulkIdParam param) {
        return (CatalogImageBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<CatalogImage> getResourceClass() {
        return CatalogImage.class;
    }

    @Override
    public CatalogImageBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<CatalogImage> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new CatalogImageBulkRep(BulkList.wrapping(_dbIterator, CatalogImageMapper.getInstance()));
    }

    @Override
    public CatalogImageBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        Iterator<CatalogImage> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new CatalogImageFilter(getUserFromContext(), _permissionsHelper);
        return new CatalogImageBulkRep(BulkList.wrapping(_dbIterator, CatalogImageMapper.getInstance(), filter));
    }

}
