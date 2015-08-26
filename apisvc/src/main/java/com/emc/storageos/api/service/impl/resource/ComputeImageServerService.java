/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.compute.ComputeImageServerCreate;
import com.emc.storageos.model.compute.ComputeImageServerList;
import com.emc.storageos.model.compute.ComputeImageServerRestRep;
import com.emc.storageos.model.compute.ComputeImageServerUpdate;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;

@Path("/compute/compute-imageservers")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = {
		Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ComputeImageServerService extends TaskResourceService {

	@Override
	protected ComputeImageServer queryResource(URI id) {
		return queryObject(ComputeImageServer.class, id, false);
	}

	@Override
	protected URI getTenantOwner(URI id) {
		return null;
	}

	@Override
	protected ResourceTypeEnum getResourceType() {
		return ResourceTypeEnum.COMPUTE_IMAGESERVER;
	}

	/**
	 * Delete the Compute image server
	 * 
	 * @param id
	 *            the URN of compute image server
	 *
	 * @return {@link Response} instance
	 */
	@POST
	@Path("/{id}/deactivate")
	@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
	public Response deleteComputeImageServer(@PathParam("id") URI id) {
		// Validate the provider
		ArgValidator.checkFieldUriType(id, ComputeImageServer.class, "id");
		ComputeImageServer imageServer = _dbClient.queryObject(
				ComputeImageServer.class, id);
		ArgValidator.checkEntityNotNull(imageServer, id, isIdEmbeddedInURL(id));

		// Set to inactive.
		_dbClient.markForDeletion(imageServer);

		auditOp(OperationTypeEnum.DELETE_COMPUTE_IMAGESERVER, true, null,
				imageServer.getId().toString(),
				imageServer.getImageServerIp(), imageServer.getImageServerUser());

		return Response.ok().build();
	}

	/**
	 * Create the Compute image server
	 * 
	 * @param createParams
	 *            {@link ComputeImageServerCreate} containing the details
	 * 
	 * @return {@link TaskResourceRep} instance
	 */
	@POST
	@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
	public TaskResourceRep createComputeImageServer(
			ComputeImageServerCreate createParams) {
		// TODO:
		return null;
	}

	/**
	 * Show compute image server attributes.
	 * 
	 * @param id
	 *            the URN of compute image server
	 * @brief Show compute image server
	 * @return Compute image server details
	 */
	@GET
	@Path("/{id}")
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public ComputeImageServerRestRep getComputeImageServer(
			@PathParam("id") URI id) {
		return null;
	}

	/**
	 * Returns a list of all compute image servers.
	 * 
	 * @brief Show compute image servers
	 * @return List of all compute image servers.
	 */
	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
	public ComputeImageServerList getComputeImageServers() {
		return null;
	}

	/**
	 * Update the Compute image server details
	 * 
	 * @param id
	 *            the URN of a ViPR compute image server
	 * 
	 * @return Updated compute image server information.
	 */
	@PUT
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("/{id}")
	@CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
	public ComputeImageServerRestRep updateComputeImageServer(
			@PathParam("id") URI id, ComputeImageServerUpdate param) {
		return null;
	}

}
