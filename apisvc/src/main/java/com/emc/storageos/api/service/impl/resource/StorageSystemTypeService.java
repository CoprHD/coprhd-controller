package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAdd;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.google.common.collect.Lists;

import static java.util.Arrays.asList;
import static com.emc.storageos.api.mapper.SystemsMapper.map;

/**
 * StorageSystemTypes resource implementation
 */
@Path("/storagesystemtypes")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })

public class StorageSystemTypeService extends TaskResourceService {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypeService.class);

    private static final String EVENT_SERVICE_TYPE = "StorageSystemTypeService";

    /**
     * Show compute image attribute.
     *
     * @param id
     *            the URN of compute image
     * @brief Show compute image
     * @return Compute image details
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public StorageSystemTypeRestRep getStorageSystemType(@PathParam("id") URI id) {
        if (!checkForStorageSystemType()) {
        	addDefaultStorageSystemTypes();
        }
    	ArgValidator.checkFieldUriType(id, StorageSystemType.class, "id");
    	StorageSystemType storageType = queryResource(id);
    	ArgValidator.checkEntity(storageType, id, isIdEmbeddedInURL(id));
    	StorageSystemTypeRestRep storageTypeRest = new StorageSystemTypeRestRep(); 
        return map(storageType, storageTypeRest);
    }

    /**
     * Returns a list of all compute images.
     *
     * @brief Show compute images
     * @return List of all compute images.
     */
    @GET
    @Path("/type/{type_name}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageSystemTypeList getStorageSystemTypes(String storageType) {
        log.info("getStorageSystemTypes");
        
        storageType = "block";
        
        if (!checkForStorageSystemType()) {
        	addDefaultStorageSystemTypes();
        }
        // validate query param
        if (storageType != null) {
            ArgValidator.checkFieldValueFromEnum(storageType, "storageType",
            		StorageSystemType.StorageType.class);
        }

        List<URI> ids = _dbClient.queryByType(StorageSystemType.class, true);
        StorageSystemTypeList list = new StorageSystemTypeList();
        
        Iterator<StorageSystemType> iter = _dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (iter.hasNext()) {
        	StorageSystemType ssType = iter.next();
            if (storageType == null || storageType.equals(ssType.getStorageType())) {
                list.getStorageSystemTypes().add(map(ssType)); //DbObjectMapper.toNamedRelatedResource
            }
        }
        return list;
    }

    /**
     * Create compute image from image URL or existing installable image URN.
     *
     * @param param
     *            The ComputeImageCreate object contains all the parameters for
     *            creation.
     * @brief Create compute image
     * @return Creation task REST representation.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StorageSystemTypeRestRep addStorageSystemType(StorageSystemTypeAdd param) {
        log.info("addStorageSystemType");
        if (!checkForStorageSystemType()) {
        	addDefaultStorageSystemTypes();
        }
        // unique name required
        ArgValidator.checkFieldNotEmpty(param.getName(), "name");
        checkDuplicateLabel(StorageSystemType.class, param.getName());

        ArgValidator.checkFieldNotEmpty(param.getId(), "id");
        ArgValidator.checkUrl(param.getId(), "id");
        
        StorageSystemType ssType = new StorageSystemType();
        ssType.setId(URIUtil.createId(StorageSystemType.class));

        ssType.setStorageTypeName(param.getName());
        ssType.setStorageType(param.getStorageType());
        ssType.setIsSmiProvider(param.getIsProvider());

        _dbClient.createObject(ssType);

        auditOp(OperationTypeEnum.ADD_STORAGE_SYSTEM_TYPE, true, AuditLogManager.AUDITOP_BEGIN, ssType.getId().toString(),
        		ssType.getStorageTypeName(), ssType.getStorageType());
        return map(ssType);
    }


    /**
     * Delete existing Storage System Type.
     *
     * @param id
     *            
     * @brief Delete Storage System Type.
     * @return No data returned in response body
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteStorageSystemType(@PathParam("id") URI id) {
        log.info("deleteStorageSystemType: {}", id);

        StorageSystemType sstype = queryObject(StorageSystemType.class, id, true);
        ArgValidator.checkEntity(sstype, id, isIdEmbeddedInURL(id));

        _dbClient.markForDeletion(sstype);

        auditOp(OperationTypeEnum.REMOVE_STORAGE_SYSTEM_TYPE, true, AuditLogManager.AUDITOP_BEGIN, sstype.getId().toString(),
        		sstype.getStorageTypeName(), sstype.getStorageType());
        return Response.ok().build();

    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }


    @Override
    protected StorageSystemType queryResource(URI id) {
        ArgValidator.checkUri(id);
        StorageSystemType storageType = _dbClient.queryObject(StorageSystemType.class, id);
        ArgValidator.checkEntity(storageType, id, isIdEmbeddedInURL(id));
        return storageType;
    }


    /**
     * Check if there are Storage System Type, if not we should initialized
     */
    private boolean checkForStorageSystemType() {
        boolean storageTypeExist = true;
        List<URI> storageTypes = _dbClient.queryByType(StorageSystemType.class, true);
        
        ArrayList<URI> tempList = Lists.newArrayList(storageTypes.iterator());

        if (tempList.isEmpty()) {
        	storageTypeExist = false;
        }
        return storageTypeExist;
    }

    
    
	@Override
	protected ResourceTypeEnum getResourceType() {
		// TODO Auto-generated method stub
		return ResourceTypeEnum.STORAGE_SYSTEM_TYPE;
	}

	@Override
	protected URI getTenantOwner(URI id) {
		// TODO Auto-generated method stub
		return null;
	}

	private void addDefaultStorageSystemTypes() {
		// Default File arrays
		List<String> storageArrayFile = asList("EMC VNX File", "EMC Isilon", "NetApp 7-mode", "NetApp Cluster-mode");
		
		//Default Provider for File
		List<String> storageProviderFile = asList("ScaleIO Gateway");

		//Default block arrays
		List<String> storageArrayBlock = asList("EMC VNX Block", "EMC VNXe");
		
		//Default Storage provider for Block
		List<String> storageProviderBlock = asList("Storage Provider for EMC VMAX, VNX Block", "Storage Provider for Hitachi storage systems", 
				"Storage Provider for EMC VPLEX", "Storage Provider for Third-party block storage systems", "Block Storage Powered by ScaleIO",
				"Storage Provider for Data Domain Management Center", "Storage Provider for IBM XIV",
				"Storage Provider for EMC XtremIO");
		
		//Default object arrays
		List<String> storageArrayObject = asList("EMC Elastic Cloud Storage");
		
		for(String file:storageArrayFile) {
			StorageSystemType ssType = new StorageSystemType();
			ssType.setId(URIUtil.createId(StorageSystemType.class));
			ssType.setStorageTypeName(file);
			ssType.setStorageType("file");
			ssType.setIsSmiProvider(false);
			 _dbClient.createObject(ssType);
		}
		
		for(String file:storageProviderFile) {
			StorageSystemType ssType = new StorageSystemType();
			ssType.setId(URIUtil.createId(StorageSystemType.class));
			ssType.setStorageTypeName(file);
			ssType.setStorageType("file");
			ssType.setIsSmiProvider(true);
			 _dbClient.createObject(ssType);
		}
		
		for(String block:storageArrayBlock) {
			StorageSystemType ssType = new StorageSystemType();
			ssType.setId(URIUtil.createId(StorageSystemType.class));
			ssType.setStorageTypeName(block);
			ssType.setStorageType("block");
			ssType.setIsSmiProvider(false);
			 _dbClient.createObject(ssType);
		}
		
		for(String block:storageProviderBlock) {
			StorageSystemType ssType = new StorageSystemType();
			ssType.setId(URIUtil.createId(StorageSystemType.class));
			ssType.setStorageTypeName(block);
			ssType.setStorageType("block");
			ssType.setIsSmiProvider(true);
			 _dbClient.createObject(ssType);
		}
		
		for(String object:storageArrayObject) {
			StorageSystemType ssType = new StorageSystemType();
			ssType.setId(URIUtil.createId(StorageSystemType.class));
			ssType.setStorageTypeName(object);
			ssType.setStorageType("object");
			ssType.setIsSmiProvider(false);
			 _dbClient.createObject(ssType);
		}
	}
}




