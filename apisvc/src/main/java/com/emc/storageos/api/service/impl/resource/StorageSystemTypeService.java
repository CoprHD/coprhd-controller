package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.Consumes;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.StorageSystemTypeServiceUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.google.common.collect.Lists;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.emc.storageos.api.mapper.SystemsMapper.map;

/**
 * StorageSystemTypes resource implementation
 */
@Path("/storagesystemtype")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN,
		Role.RESTRICTED_SYSTEM_ADMIN })

public class StorageSystemTypeService extends TaskResourceService {

	private static final Logger log = LoggerFactory.getLogger(StorageSystemTypeService.class);
	private static final String EVENT_SERVICE_TYPE = "StorageSystemTypeService";
	private static final String UPLOAD_DEVICE_DRIVER = "/tmp/drivers/";
	private static final String ALL_TYPE = "all";

	/**
	 * Show Storage System Type detail for given URI
	 *
	 * @param id the URN of compute image
	 * @brief Show StorageSystemType
	 * @return Storage System Type details
	 */
	@GET
	@Path("/{id}")
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public StorageSystemTypeRestRep getStorageSystemType(@PathParam("id") URI id) {
		log.info("GET getStorageSystemType on Uri: " + id);
		if (!checkForStorageSystemType()) {
			StorageSystemTypeServiceUtils.InitializeStorageSystemTypes(_dbClient);
		}
		ArgValidator.checkFieldUriType(id, StorageSystemType.class, "id");
		StorageSystemType storageType = queryResource(id);
		ArgValidator.checkEntity(storageType, id, isIdEmbeddedInURL(id));
		StorageSystemTypeRestRep storageTypeRest = new StorageSystemTypeRestRep();
		return map(storageType, storageTypeRest);
	}

	/**
	 * Returns a list of all Storage System Types requested for like block, file, object or all.
	 * Valid input parameters are block, file, object and all
	 * @brief Show list of storage system types base of type or all
	 * @return List of all storage system types.
	 */
	@GET
	@Path("/type/{type}")
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
	public StorageSystemTypeList getStorageSystemTypeType(@PathParam("type") String type) {
		log.info("GET getStorageSystemType on type: " + type);
		if (!checkForStorageSystemType()) {
			StorageSystemTypeServiceUtils.InitializeStorageSystemTypes(_dbClient);
		}

		if (type != null) {
			ArgValidator.checkFieldValueFromEnum(type, "storageTypeType", StorageSystemType.storageSupportedType.class);
		}

		List<URI> ids = _dbClient.queryByType(StorageSystemType.class, true);

		StorageSystemTypeList list = new StorageSystemTypeList();

		Iterator<StorageSystemType> iter = _dbClient.queryIterativeObjects(StorageSystemType.class, ids);
		while (iter.hasNext()) {
			StorageSystemType ssType = iter.next();
			if (ssType.getStorageTypeId() == null) {
				ssType.setStorageTypeId(ssType.getId().toString());
			}
			if (StringUtils.equals(ALL_TYPE, type) || StringUtils.equals(type, ssType.getStorageTypeType())) {
				list.getStorageSystemTypes().add(map(ssType));
			}
		}
		return list;
	}

	/**
	 * Create a new storage system type that CoprHD is not natively supported.
	 *
	 * @param param
	 *            The StorageSystemTypeAddParam object contains all the parameters for
	 *            creation.
	 * @brief Create storage system type
	 * @return StorageSystemTypeRestRep object.
	 */
	@POST
	@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
	public StorageSystemTypeRestRep addStorageSystemType(StorageSystemTypeAddParam addparam) {
		log.info("addStorageSystemType");
		if (!checkForStorageSystemType()) {
			StorageSystemTypeServiceUtils.InitializeStorageSystemTypes(_dbClient);
		}

		ArgValidator.checkFieldNotEmpty(addparam.getStorageTypeName(), "storageTypeName");
		checkDuplicateLabel(StorageSystemType.class, addparam.getStorageTypeName());

		ArgValidator.checkFieldNotEmpty(addparam.getStorageTypeType(), "storageTypeType");

		ArgValidator.checkFieldNotEmpty(addparam.getDriverClassName(), "driverClassName");

		if (addparam.getIsDefaultSsl()) {
			ArgValidator.checkFieldNotEmpty(addparam.getSslPort(), "sslPort");
		} else {
			ArgValidator.checkFieldNotEmpty(addparam.getNonSslPort(), "nonSslPort");
		}

		StorageSystemType ssType = new StorageSystemType();
		URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
		ssType.setId(ssTyeUri);
		ssType.setStorageTypeId(ssTyeUri.toString());

		ssType.setStorageTypeName(addparam.getStorageTypeName());
		ssType.setStorageTypeType(addparam.getStorageTypeType());
		ssType.setDriverClassName(addparam.getDriverClassName());

		if (addparam.getStorageTypeDispName() != null) {
			ssType.setStorageTypeDispName(addparam.getStorageTypeDispName());
		}

		if (addparam.getNonSslPort() != null) {
			ssType.setNonSslPort(addparam.getNonSslPort());
		}

		if (addparam.getSslPort() != null) {
			ssType.setSslPort(addparam.getSslPort());
		}

		ssType.setIsSmiProvider(addparam.getIsSmiProvider());
		ssType.setIsDefaultSsl(addparam.getIsDefaultSsl());
		ssType.setIsDefaultMDM(addparam.getIsDefaultMDM());
		ssType.setIsOnlyMDM(addparam.getIsOnlyMDM());
		ssType.setIsElementMgr(addparam.getIsElementMgr());

		_dbClient.createObject(ssType);

		auditOp(OperationTypeEnum.ADD_STORAGE_SYSTEM_TYPE, true, AuditLogManager.AUDITOP_BEGIN,
				ssType.getId().toString(), ssType.getStorageTypeName(), ssType.getStorageTypeType());
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
		// Name of Array and its Display Name mapping, cannot delete native drivers
		HashMap<String, String> nativeDriverNameMap = new HashMap<String, String>();
		StorageSystemTypeServiceUtils.initializeDisplayName(nativeDriverNameMap);
		
		StorageSystemType sstype = queryObject(StorageSystemType.class, id, true);
		ArgValidator.checkEntity(sstype, id, isIdEmbeddedInURL(id));
		if(nativeDriverNameMap.get(sstype.getStorageTypeName()) == null ) {
			_dbClient.markForDeletion(sstype);

			auditOp(OperationTypeEnum.REMOVE_STORAGE_SYSTEM_TYPE, true, AuditLogManager.AUDITOP_BEGIN,
					sstype.getId().toString(), sstype.getStorageTypeName(), sstype.getStorageTypeType());
			return Response.ok().build();
		}
		else {
			return Response.serverError().build();
		}

	}

	/**
	 * Upload the device driver file. Consumes MediaType.MULTIPART_FORM_DATA.
	 * This is an asynchronous operation.
	 * 
	 * @brief Upload the specified device driver file
	 * @return Response information.
	 */

	@POST
	@Path("/upload")
	@CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
	@Consumes({ MediaType.APPLICATION_OCTET_STREAM, MediaType.MULTIPART_FORM_DATA })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public Response uploadFile (
			@FormDataParam("deviceDriver") InputStream fileInputStream,
			@FormDataParam("deviceDriver") FormDataContentDisposition contentDispositionHeader) {
		log.info("Upload of device driver file started, time: " + System.currentTimeMillis());

		String filePath = UPLOAD_DEVICE_DRIVER + contentDispositionHeader.getFileName();
		// save the file to the server
		saveFile(fileInputStream, filePath);
		//String output = "File saved to server location : " + filePath;
		log.info("Device driver file uploaded at " + filePath);
		Response myhttpresponse = Response.status(Response.Status.OK).build();
		return myhttpresponse;
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

	// save uploaded file to a defined location on the server
	private void saveFile(InputStream uploadedInputStream, String serverLocation) {
		try {
			OutputStream outpuStream = new FileOutputStream(new File(serverLocation));
			int read = 0;
			byte[] bytes = new byte[1024];
			//outpuStream = new FileOutputStream(new File(serverLocation));
			while ((read = uploadedInputStream.read(bytes)) != -1) {
				outpuStream.write(bytes, 0, read);
			}
			outpuStream.flush();
			outpuStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
