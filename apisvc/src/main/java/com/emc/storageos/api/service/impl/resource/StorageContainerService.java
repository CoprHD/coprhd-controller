package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.VirtualPoolMapper.toStorageContainer;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.vpool.StorageContainerCreateResponse;
import com.emc.storageos.model.vpool.StorageContainerRequestParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

@Path("/vdc/storagecontainer")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class StorageContainerService extends VirtualPoolService {

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
        ArgValidator.checkFieldNotEmpty(param.getName(), VPOOL_NAME);
        checkForDuplicateName(param.getName(), VirtualPool.class);
        ArgValidator.checkFieldNotEmpty(param.getDescription(), VPOOL_DESCRIPTION);
        VirtualPoolUtil.validateStorageContainerCreateParams(param, _dbClient);
        VirtualPool vPool = prepareVirtualPool(param);
        
        return toStorageContainer(vPool);

    }

    private VirtualPool prepareVirtualPool(StorageContainerRequestParam param) {
        VirtualPool vPool = new VirtualPool();
        vPool.setType(VirtualPool.Type.storageContainer.name());
        // set common VirtualPool parameters.
        populateCommonVirtualPoolCreateParams(vPool, param);
        StringSetMap arrayInfo = new StringSetMap();
        if (null != param.getSystemType()) {
            arrayInfo.put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, param.getSystemType());
            vPool.addArrayInfoDetails(arrayInfo);
        }
        // update the implicit pools matching with this VirtualPool.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(vPool, _dbClient, _coordinator);
        _dbClient.createObject(vPool);
        recordOperation(OperationTypeEnum.CREATE_VPOOL, VPOOL_CREATED_DESCRIPTION, vPool);
        return vPool;
    }
    
    @GET
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/container_id")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response getResponseStatus(){
        return Response.status(200).build();
    }

    @Override
    protected Type getVirtualPoolType() {
        return Type.storageContainer;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

}
