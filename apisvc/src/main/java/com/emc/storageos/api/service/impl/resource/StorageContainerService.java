package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.VasaObjectMapper.toStorageContainer;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageContainer;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.vasa.StorageContainerCreateResponse;
import com.emc.storageos.model.vasa.StorageContainerRequestParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/vasa/storagecontainer")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class StorageContainerService extends AbstractStorageContainerService {

    private static final Logger _log = LoggerFactory.getLogger(StorageContainerService.class);

    /**
     * Creates Storage Container
     * 
     * @param param
     * @return
     * @throws DatabaseException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StorageContainerCreateResponse createStorageContainer(
            StorageContainerRequestParam param) throws DatabaseException {
        ArgValidator.checkFieldNotEmpty(param.getName(), NAME);
        checkForDuplicateName(param.getName(), VirtualPool.class);
        ArgValidator.checkFieldNotEmpty(param.getDescription(), DESCRIPTION);
        StorageContainer storageContainer = prepareStorageContainer(param);
        
        return toStorageContainer(storageContainer);

    }

    private StorageContainer prepareStorageContainer(StorageContainerRequestParam param) {
        StorageContainer storageContainer = new StorageContainer();
        storageContainer.setType(StorageContainer.Type.geo.name());
        // set common VirtualPool parameters.
        populateCommonStorageContainerCreateParams(storageContainer, param);
        _dbClient.createObject(storageContainer);
        return storageContainer;
    }
    
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response getResponseStatus(){
        _log.info("*******enter in get response***********");
        List<URI> storageContainerList = new ArrayList<URI>();
        storageContainerList = _dbClient.queryByType(StorageContainer.class, true);
        _log.info("@@@@@@@@" + "StorageContainerObject : " + storageContainerList.toString()  + "@@@@@@");
        return Response.status(200).build();
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

}
