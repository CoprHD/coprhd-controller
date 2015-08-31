/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.ComputeMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.imageservercontroller.ImageServerController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
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
import com.emc.storageos.volumecontroller.AsyncTask;

@Path("/compute/compute-imageservers")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = {
        Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ComputeImageServerService extends TaskResourceService {

    private static final Logger log = LoggerFactory.getLogger(ComputeImageServerService.class);

    private static final String EVENT_SERVICE_TYPE = "ComputeImageServer";

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

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
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
                imageServer.getId().toString(), imageServer.getImageServerIp(),
                imageServer.getImageServerUser());

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

        String imageServerAddress = createParams.getImageServerIp();
        ArgValidator.checkFieldNotEmpty(imageServerAddress,
                "imageServerAddress");
        checkDuplicateLabel(ComputeImageServer.class,
                createParams.getImageServerIp(), "imageServerIp");

        String bootDir = createParams.getTftpbootDir();
        String osInstallAddress = createParams.getImageServerSecondIp();
        String username = createParams.getImageServerUser();
        String password = createParams.getImageServerPassword();
        Integer installTimeout = createParams.getOsInstallTimeoutMs();

        ArgValidator.checkFieldNotEmpty(bootDir, "tftpbootDir");
        ArgValidator
                .checkFieldNotEmpty(osInstallAddress, "imageServerSecondIp");
        ArgValidator.checkFieldNotEmpty(username, "imageServerPassword");
        ArgValidator.checkFieldNotEmpty(password, "password");
        ArgValidator.checkFieldNotNull(installTimeout, "osInstallTimeoutMs");


        ComputeImageServer imageServer = new ComputeImageServer();
        imageServer.setId(URIUtil.createId(ComputeImageServer.class));
        imageServer.setLabel(imageServerAddress);
        imageServer.setImageServerIp(imageServerAddress);
        imageServer.setTftpbootDir(bootDir);
        imageServer.setImageServerUser(username);
        imageServer.setImageServerPassword(password);
        imageServer.setOsInstallTimeoutMs((int) installTimeout);
        imageServer.setImageServerSecondIp(osInstallAddress);

        auditOp(OperationTypeEnum.CREATE_COMPUTE_IMAGESERVER, true,
                null, imageServer.getId().toString(),
                imageServer.getImageServerIp());

        _dbClient.createObject(imageServer);

        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        AsyncTask task = new AsyncTask(ComputeImageServer.class, imageServer.getId(), taskId);
        tasks.add(task);
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_COMPUTE_IMAGE_SERVER);
        _dbClient.createTaskOpStatus(ComputeImageServer.class, imageServer.getId(), taskId, op);

        ImageServerController controller = getController(ImageServerController.class, null);
        controller.verifyImageServerAndImportImages(task);
        return toTask(imageServer, taskId, op);
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

        ArgValidator.checkFieldUriType(id, ComputeImageServer.class, "id");
        ComputeImageServer imageServer = queryResource(id);
        return map(imageServer);
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

        List<URI> ids = _dbClient.queryByType(ComputeImageServer.class, true);
        /*List<ComputeImageServer> computeImageServers = _dbClient.queryObject(ComputeImageServer.class, ids);
        if (computeImageServers == null) {
            //TODO: Need to throw appropriate exception...
            throw APIException.badRequests.unableToFindStorageProvidersForIds(ids);
        }*/
        ComputeImageServerList imageServerList = new ComputeImageServerList();
        Iterator<ComputeImageServer> iter = _dbClient.queryIterativeObjects(ComputeImageServer.class, ids);
        while (iter.hasNext()) {
            ComputeImageServer imageServer = iter.next();
                imageServerList.getComputeImageServers().add(DbObjectMapper.toNamedRelatedResource(imageServer));
        }

        return imageServerList;
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
