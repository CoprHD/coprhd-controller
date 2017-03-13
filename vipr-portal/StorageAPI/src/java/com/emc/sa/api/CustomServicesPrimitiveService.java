/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.api;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.sa.catalog.primitives.CustomServicesResourceDAO;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAOs;
import com.emc.storageos.db.client.model.uimodels.CustomServicesAnsibleInventoryResource;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.CustomServicesPrimitiveManager;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAO;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveMapper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBPrimitive;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveBulkRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.NotFoundException;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;

@Path("/primitives")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = {
        ACL.OWN, ACL.ALL })
public class CustomServicesPrimitiveService extends CatalogTaggedResourceService {
    @Autowired
    private CustomServicesPrimitiveManager primitiveManager;
    @Autowired
    private CustomServicesPrimitiveDAOs daos;
    @Autowired
    private CustomServicesResourceDAOs resourceDAOs;
    
    private static final Logger _log = LoggerFactory
            .getLogger(CustomServicesPrimitiveManager.class);

    /**
     * Get the list of primitives that can be used for creating 
     * custom services workflows
     *
     * @prereq none
     *
     *
     * @brief Get list of primitives
     *
     * @return PrimitiveList
     *
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesPrimitiveList getPrimitives(@QueryParam("type") final String type) {

        final Set<String> types = (type == null) ? daos.getTypes() : Collections.singleton(type);

        final List<URI> list = new ArrayList<URI>();
        for( final String primitiveType : types ) {
            final CustomServicesPrimitiveDAO<?> dao = getDAO(primitiveType, false);
            final List<URI> ids = dao.list();
            for(final URI id : ids) {
                list.add(id);
            }
        }
        
        return new CustomServicesPrimitiveList(list);
    }

    /**
     * Create a new primitive
     * 
     * @prereq none
     * 
     * @brief Create a new user defined primitive
     * 
     * @param param a primitive creation parameter
     * 
     * @return PrimitiveRestRep 
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesPrimitiveRestRep makePrimitive(CustomServicesPrimitiveCreateParam param) {
        final CustomServicesPrimitiveDAO<?> dao = getDAO(param.getType(), false);
        return CustomServicesPrimitiveMapper.map(dao.create(param));
    }

    /**
     * Get a primitive that can be used in custom services workflows
     *
     * @prereq none
     *
     *
     * @brief Get a primitive by name
     *
     * @param id
     * @return PrimitiveRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public CustomServicesPrimitiveRestRep getPrimitive(@PathParam("id") final URI id) {
        return CustomServicesPrimitiveMapper.map(getPrimitiveType(id));
    }

    /**
     * Get a list of resources of a given type
     * 
     * @brief Get a list of Custom Services resources
     * 
     * @param type The type of the custom services resource to list
     * 
     * @return a list of resources of the given type
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/resource/{type}")
    public CustomServicesPrimitiveResourceList getResources(@PathParam("type") final String type) {
        
        final CustomServicesResourceDAO<?> dao = resourceDAOs.get(type);
        final List<NamedElement> resources = dao.listResources();
        
        return CustomServicesPrimitiveMapper.toCustomServicesPrimitiveResourceList(type, resources);
    }
    
    /**
     * Upload a resource
     * @param request HttpServletRequest containing the file octet stream
     * @param type The type of the primitive file resource
     * @param name The user defined name of the resource
     * @return A rest response containing details of the resource that was created
     */
    @POST
    @Consumes({ MediaType.APPLICATION_OCTET_STREAM })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/resource/{type}")
    public CustomServicesPrimitiveResourceRestRep upload(
            @Context HttpServletRequest request,
            @PathParam("type") String type, @QueryParam("name") String name) {

        ArgValidator.checkFieldNotNull(name, "name");
        CustomServicesResourceDAO<?> dao = getResourceDAO(type, true);

        final byte[] stream = read(request);
        CustomServicesPrimitiveResourceType resource = dao.createResource(name, stream);
        return CustomServicesPrimitiveMapper.map(resource);
    }

    /**
     * Upload a resource
     * @param request HttpServletRequest containing the file octet stream
     * @param id The type of the primitive file resource
     * @param name The user defined name of the resource
     * @return A rest response containing details of the resource that was created
     */
    @POST
    @Consumes({ MediaType.APPLICATION_OCTET_STREAM })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/resource/{id}/inventory")
    public CustomServicesPrimitiveResourceRestRep uploadInventoryFile(
            @Context HttpServletRequest request,
            @PathParam("id") final URI id, @QueryParam("name") String name) {

        ArgValidator.checkFieldNotNull(name, "name");
        String type = "ANSIBLE";
        CustomServicesPrimitiveDAO<?> dao = getDAO(type, true);

        final byte[] stream = read(request);
//        CustomServicesPrimitiveResourceType resource = dao.createResource(name, stream);

        final CustomServicesAnsibleInventoryResource inventoryResource = new CustomServicesAnsibleInventoryResource();
        inventoryResource.setId(URIUtil.createId(CustomServicesAnsibleInventoryResource.class));
        inventoryResource.setAnsiblePackage(id);
        inventoryResource.setLabel(name);
        inventoryResource.setResource(Base64.encodeBase64(stream));
        primitiveManager.save(inventoryResource);


        return CustomServicesPrimitiveMapper.map(inventoryResource);
    }

    /**
     * Update a primitive
     * @param id the ID of the primitivie to be updated
     * @param param Primitive update request entity
     * @return
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public CustomServicesPrimitiveRestRep updatePrimitive(@PathParam("id") final URI id,
            final CustomServicesPrimitiveUpdateParam param) {
        final CustomServicesPrimitiveDAO<?> dao = daos.getByModel(URIUtil.getTypeName(id));
        if(null == dao) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        return CustomServicesPrimitiveMapper.map(dao.update(id, param));
    }

    /**
     * Download a primitive file resource
     * @param type The type of the primitive resource
     * @param id The ID of the resource to download
     * @param response HttpServletResponse the servlet response to update with the file octet stream
     * @return Response containing the octet stream of the primitive file resource
     */
    @GET
    @Path("resource/{type}/{id}")
    public Response download(@PathParam("type") final String type,
            @PathParam("id") final URI id,
            @Context final HttpServletResponse response) {

        CustomServicesResourceDAO<?> dao = getResourceDAO(type, true);

        CustomServicesPrimitiveResourceType resource = getResourceNullSafe(id, dao);
        final ModelObject model = toModelObject(resource);
        ArgValidator.checkEntity(model, id, true);

   
        
        final byte[] bytes = Base64.decodeBase64(resource.resource());
        response.setContentLength(bytes.length);
        
        
        response.setHeader("Content-Disposition", "attachment; filename="
                + resource.name() + resource.suffix());
        return Response.ok(bytes).build();
    }
    
    @POST
    @Path("/bulk")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesPrimitiveBulkRestRep bulkGetPrimitives(final BulkIdParam ids) {
        return (CustomServicesPrimitiveBulkRestRep) super.getBulkResources(ids);
    }
    
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response deactivatePrimitive(@PathParam("id") final URI id) {
        CustomServicesPrimitiveDAO<?> dao = getDAOFromID(id);
        if(null == dao ) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        dao.deactivate(id);
        return Response.ok().build();
    }

    private byte[] read(final HttpServletRequest request) {
        try {
            final ByteArrayOutputStream file = new ByteArrayOutputStream();
            final byte buffer[] = new byte[2048];
            final InputStream in = request.getInputStream();
            int nRead = 0;
            while ((nRead = in.read(buffer)) > 0) {
                file.write(buffer, 0, nRead);
            }
            return file.toByteArray();
        } catch (IOException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("failed to read octet stream", e);
        }
    }

    @Override
    protected DataObject queryResource(URI id) {
        final CustomServicesPrimitiveDAO<?> dao = getDAOFromID(id);
        return dao == null ? null : asModelObjectNullSafe(dao.get(id));
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.CUSTOM_SERVICES_PRIMTIVES;
        
    }
    
    @Override
    public Class<CustomServicesDBPrimitive> getResourceClass() {
        return CustomServicesDBPrimitive.class;
    }
    
    @Override
    public CustomServicesPrimitiveBulkRestRep queryBulkResourceReps(final List<URI> ids) {
        return new CustomServicesPrimitiveBulkRestRep(getPrimitiveRestReps(ids));
    }
    
    @Override
    public CustomServicesPrimitiveBulkRestRep queryFilteredBulkResourceReps(final List<URI> ids) { 
        //TODO do we need any filtering?
        return new CustomServicesPrimitiveBulkRestRep(getPrimitiveRestReps(ids));
    }
    
    private List<CustomServicesPrimitiveRestRep> getPrimitiveRestReps(final List<URI> ids) {
        final ImmutableListMultimap<CustomServicesPrimitiveDAO<?>, URI> idGroups = Multimaps.index(ids, new Function<URI, CustomServicesPrimitiveDAO<?>>() {
            @Override
            public CustomServicesPrimitiveDAO<?> apply(final URI id) {
                CustomServicesPrimitiveDAO<?> dao = getDAOFromID(id);
                if( null == dao) {
                    throw BadRequestException.badRequests.invalidParameter("id", id.toString());
                }
                return dao;
            }
        });
        
        final ImmutableList.Builder<CustomServicesPrimitiveRestRep> builder = ImmutableList.<CustomServicesPrimitiveRestRep>builder();
        for( final Entry<CustomServicesPrimitiveDAO<?>, Collection<URI>> entry : idGroups.asMap().entrySet()) {
            builder.addAll(entry.getKey().bulk(entry.getValue()));
        }
        return builder.build();
    }
    
    private CustomServicesPrimitiveDAO<?> getDAO(final String type, final boolean embedded) {
        final CustomServicesPrimitiveDAO<?> dao = daos.get(type);
        
        if( null == dao) {
            if(embedded) {
                throw NotFoundException.notFound.unableToFindEntityInURL(URIUtil.uri(type));
            } else {
                throw BadRequestException.badRequests.unableToFindEntity(URIUtil.uri(type));
            }
        }
        
        return dao;
    }

    private CustomServicesResourceDAO<?> getResourceDAO(final String type, final boolean embedded) {
        final CustomServicesResourceDAO<?> dao = resourceDAOs.get(type);

        if( null == dao) {
            if(embedded) {
                throw NotFoundException.notFound.unableToFindEntityInURL(URIUtil.uri(type));
            } else {
                throw BadRequestException.badRequests.unableToFindEntity(URIUtil.uri(type));
            }
        }

        return dao;
    }


    
    private CustomServicesPrimitiveType getPrimitiveType(final URI id) {
        final CustomServicesPrimitiveDAO<?> dao = getDAOFromID(id);
        final CustomServicesPrimitiveType primitive = (null == dao) ? null : dao.get(id);
        ArgValidator.checkEntityNotNull(asModelObjectNullSafe(primitive), id, true);
        return primitive;
    }
    

    private CustomServicesPrimitiveDAO<?> getDAOFromID(final URI id) {
        return  daos.getByModel(URIUtil.getTypeName(id));
    }
    
    private ModelObject toModelObject(
            CustomServicesPrimitiveResourceType resource) {
        return (resource == null) ? null : resource.asModelObject();
    }
    
    private CustomServicesPrimitiveType getNullSafe(final URI id,
            final CustomServicesPrimitiveDAO<?> dao) {
        return dao == null ? null : dao.get(id);
    }
    
    private CustomServicesPrimitiveResourceType getResourceNullSafe(final URI id,
            final CustomServicesResourceDAO<?> dao) {
        return dao == null ? null : dao.getResource(id);
    }
    
    private DataObject asModelObjectNullSafe(
            final CustomServicesPrimitiveType primitive) {
        return primitive == null ? null : primitive.asModelObject();
    }
}
