/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.SystemsMapper.map;

import java.net.URI;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ObjectNamespace;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.object.ObjectNamespaceList;
import com.emc.storageos.model.object.ObjectNamespaceRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

/**
 * Object storage namespace implementation
 */
@Path("/vdc/object-namespaces")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ObjectNamespaceService extends TaggedResource {

    private int _retry_attempts;
    
    /**
     * Get IDs of all object storage namespaces
     * 
     * @return object namespace list
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ObjectNamespaceList getObjectNamespaces() {

        ObjectNamespaceList objNamespaceList = new ObjectNamespaceList();
        List<URI> ids = _dbClient.queryByType(ObjectNamespace.class, true);
        for (URI id : ids) {
            ObjectNamespace objNamespace = _dbClient.queryObject(ObjectNamespace.class, id);
            if (objNamespace != null) {
                objNamespaceList.getNamespaces().add(toNamedRelatedResource(objNamespace, objNamespace.getNativeGuid()));
            }
        }

        return objNamespaceList;
    }

    /**
     * Get details of the object storage namespace specified
     * 
     * @param id object storage namespace id
     * @return details of the namespace
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ObjectNamespaceRestRep getObjectNamespace(@PathParam("id") URI id) {

        ArgValidator.checkFieldUriType(id, ObjectNamespace.class, "id");
        ArgValidator.checkUri(id);
        ObjectNamespace objNamespace = _dbClient.queryObject(ObjectNamespace.class, id);
        ArgValidator.checkEntity(objNamespace, id, isIdEmbeddedInURL(id));

        ObjectNamespaceRestRep restRep = toObjectNamespaceRestRep(objNamespace, _dbClient, _coordinator);
        return restRep;
    }

    private ObjectNamespaceRestRep toObjectNamespaceRestRep(ObjectNamespace objNamespace, DbClient dbClient,
            CoordinatorClient coordinator) {

        return map(objNamespace);
    }

      
    @Override
    protected DataObject queryResource(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.OBJECT_NAMESPACE;
    }
    
    public void setRetryAttempts(int retries) {
        _retry_attempts = retries;
    }


}
