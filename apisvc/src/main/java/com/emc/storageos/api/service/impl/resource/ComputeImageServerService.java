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
import java.util.concurrent.TimeUnit;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.mapper.ComputeMapper;
import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImage.ComputeImageStatus;
import com.emc.storageos.db.client.model.ComputeImageJob;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.imageservercontroller.ImageServerController;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.compute.ComputeImageServerBulkRep;
import com.emc.storageos.model.compute.ComputeImageServerCreate;
import com.emc.storageos.model.compute.ComputeImageServerList;
import com.emc.storageos.model.compute.ComputeImageServerRestRep;
import com.emc.storageos.model.compute.ComputeImageServerUpdate;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Service class responsible for serving rest requests of ComputeImageServer
 * 
 *
 */
@Path("/compute/imageservers")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = {
        Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ComputeImageServerService extends TaskResourceService {

    private static final Logger log = LoggerFactory
            .getLogger(ComputeImageServerService.class);

    private static final String EVENT_SERVICE_TYPE = "ComputeImageServer";
    private static final String IMAGESERVER_IP = "imageServerIp";
    private static final String TFTPBOOTDIR = "tftpBootDir";
    private static final String IMAGESERVER_SECONDARY_IP = "imageServerSecondIp";
    private static final String IMAGESERVER_PASSWORD = "imageServerPassword";
    private static final String IMAGESERVER_USER = "imageServerUser";
    private static final String OS_INSTALL_TIMEOUT_MS = "osInstallTimeoutMs";
    private static final String IMAGE_SERVER_IMAGEDIR = "image_server_image_directory";

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
        // Validate the imageServer
        log.info("Delete computeImageServer id {} ", id);
        ArgValidator.checkFieldUriType(id, ComputeImageServer.class, "id");
        ComputeImageServer imageServer = _dbClient.queryObject(
                ComputeImageServer.class, id);
        ArgValidator.checkEntityNotNull(imageServer, id, isIdEmbeddedInURL(id));

        // make sure there are no active jobs associated with this imageserver
        checkActiveJobsForImageServer(id);

        // Remove the association with the ComputeSystem and then delete the
        // imageServer
        List<URI> imageServerURIList = _dbClient.queryByType(
                ComputeImageServer.class, true);
        ArrayList<URI> tempList = Lists.newArrayList(imageServerURIList
                .iterator());

        if (tempList.size() > 1) {
            removeImageServerFromComputeSystem(id);
        } else if (tempList.size() == 1) {

            // If the imageServer being deleted is the last one,
            // then check if there are any valid AVAILABLE images, if so
            // throw exception because user cannot delete all imageServers when
            // there are valid images available.
            boolean hasValidImages = false;
            List<URI> imageURIList = _dbClient.queryByType(ComputeImage.class,
                    true);
            Iterator<ComputeImage> imageItr = _dbClient.queryIterativeObjects(
                    ComputeImage.class, imageURIList);

            while (imageItr.hasNext()) {
                ComputeImage computeImage = (ComputeImage) imageItr.next();
                if (ComputeImageStatus.AVAILABLE.name().equals(
                        computeImage.getComputeImageStatus())) {
                    hasValidImages = true;
                    break;
                }
            }

            if (hasValidImages) {
                throw APIException.badRequests.cannotDeleteImageServer();
            } else {
                removeImageServerFromComputeSystem(id);
            }
        }

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
        log.info("Create computeImageServer");
        String imageServerName = createParams.getName();
        String imageServerAddress = createParams.getImageServerIp();
        ArgValidator.checkFieldNotEmpty(imageServerName, "imageServerName");
        ArgValidator.checkFieldNotEmpty(imageServerAddress, IMAGESERVER_IP);
        checkDuplicateImageServer(null, imageServerAddress, imageServerName);

        String bootDir = createParams.getTftpBootDir();
        String osInstallAddress = createParams.getImageServerSecondIp();
        String username = createParams.getImageServerUser();
        String password = createParams.getImageServerPassword();
        Integer installTimeout = createParams.getOsInstallTimeout();
        Integer sshTimeout = createParams.getSshTimeout();
        Integer imageImportTimeout = createParams.getImageImportTimeout();

        ArgValidator.checkFieldNotEmpty(bootDir, TFTPBOOTDIR);
        ArgValidator.checkFieldNotEmpty(osInstallAddress,
                IMAGESERVER_SECONDARY_IP);
        ArgValidator.checkFieldNotEmpty(username, IMAGESERVER_USER);
        ArgValidator.checkFieldNotEmpty(password, IMAGESERVER_PASSWORD);
        ArgValidator.checkFieldNotNull(installTimeout, OS_INSTALL_TIMEOUT_MS);
        ArgValidator.checkFieldRange(installTimeout, 0, 2147483, "seconds", "osInstallTimeout");
        ArgValidator.checkFieldNotNull(sshTimeout, OS_INSTALL_TIMEOUT_MS);
        ArgValidator.checkFieldRange(sshTimeout, 0, 2147483, "seconds", "sshTimeout");
        ArgValidator.checkFieldNotNull(imageImportTimeout, OS_INSTALL_TIMEOUT_MS);
        ArgValidator.checkFieldRange(installTimeout, 0, 2147483, "seconds", "imageImportTimeout");

        ComputeImageServer imageServer = new ComputeImageServer();
        imageServer.setId(URIUtil.createId(ComputeImageServer.class));
        imageServer.setLabel(imageServerName);
        imageServer.setImageServerIp(imageServerAddress);
        imageServer.setTftpBootDir(bootDir);
        imageServer.setImageServerUser(username);
        imageServer.setImageServerPassword(password);
        imageServer.setOsInstallTimeoutMs(new Long(
                TimeUnit.SECONDS.toMillis(installTimeout)).intValue());
        imageServer.setImageServerSecondIp(osInstallAddress);
        imageServer.setImageDir(_coordinator.getPropertyInfo().getProperty(IMAGE_SERVER_IMAGEDIR));
        imageServer.setSshTimeoutMs(new Long(
                TimeUnit.SECONDS.toMillis(sshTimeout)).intValue());
        imageServer.setImageImportTimeoutMs(new Long(
                TimeUnit.SECONDS.toMillis(imageImportTimeout)).intValue());

        auditOp(OperationTypeEnum.IMAGESERVER_VERIFY_IMPORT_IMAGES, true, null,
                imageServer.getId().toString(), imageServer.getImageServerIp());

        _dbClient.createObject(imageServer);

        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        AsyncTask task = new AsyncTask(ComputeImageServer.class,
                imageServer.getId(), taskId);
        tasks.add(task);
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_VERIFY_COMPUTE_IMAGE_SERVER);
        _dbClient.createTaskOpStatus(ComputeImageServer.class,
                imageServer.getId(), taskId, op);

        ImageServerController controller = getController(
                ImageServerController.class, null);
        controller.verifyImageServerAndImportExistingImages(task, op.getName());
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
        return map(_dbClient, imageServer);
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

        ComputeImageServerList imageServerList = new ComputeImageServerList();
        Iterator<ComputeImageServer> iter = _dbClient.queryIterativeObjects(
                ComputeImageServer.class, ids);
        while (iter.hasNext()) {
            ComputeImageServer imageServer = iter.next();
            imageServerList.getComputeImageServers().add(
                    DbObjectMapper.toNamedRelatedResource(imageServer));
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
        log.info("Update computeImageServer id {} ",id);
        ComputeImageServer imageServer = _dbClient.queryObject(
                ComputeImageServer.class, id);
        if (null == imageServer || imageServer.getInactive()) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        } else {
            StringSet availImages = imageServer.getComputeImages();
            // make sure there are no active jobs associated with this imageserver
            checkActiveJobsForImageServer(id);
            String imageServerName = param.getName();
            String imageServerAddress = param.getImageServerIp();
            String bootDir = param.getTftpBootDir();
            String osInstallAddress = param.getImageServerSecondIp();
            String username = param.getImageServerUser();
            String password = param.getImageServerPassword();
            Integer installTimeout = param.getOsInstallTimeout();
            Integer sshTimeout = param.getSshTimeout();
            Integer imageImportTimeout = param.getImageImportTimeout();
            if (StringUtils.isNotBlank(imageServerName)
                    && !imageServerName
                            .equalsIgnoreCase(imageServer.getLabel())) {
                checkDuplicateLabel(ComputeImageServer.class, imageServerName);
                imageServer.setLabel(param.getName());
            }
            if (StringUtils.isNotBlank(imageServerAddress)
                    && !imageServerAddress.equalsIgnoreCase(imageServer
                            .getImageServerIp())) {
                checkDuplicateImageServer(id, imageServerAddress, null);
                disassociateComputeImages(imageServer);
                imageServer.setImageServerIp(imageServerAddress);
            }
            if(StringUtils.isNotBlank(osInstallAddress)){
                imageServer.setImageServerSecondIp(osInstallAddress);
            }
            if(StringUtils.isNotBlank(username)){
                imageServer.setImageServerUser(username);
            }
            if(null != installTimeout){
                ArgValidator.checkFieldRange(installTimeout, 0, 2147483, "seconds", "osInstallTimeout");
                imageServer.setOsInstallTimeoutMs(new Long(
                        TimeUnit.SECONDS.toMillis(installTimeout)).intValue());
            }
            if(null != sshTimeout){
                ArgValidator.checkFieldRange(sshTimeout, 0, 2147483, "seconds", "sshTimeout");
                imageServer.setSshTimeoutMs(new Long(
                        TimeUnit.SECONDS.toMillis(sshTimeout)).intValue());
            }
            if(null != imageImportTimeout){
                ArgValidator.checkFieldRange(imageImportTimeout, 0, 2147483, "seconds", "imageImportTimeout");
                imageServer.setImageImportTimeoutMs(new Long(
                        TimeUnit.SECONDS.toMillis(imageImportTimeout)).intValue());
            }
            if (StringUtils.isNotBlank(bootDir)) {
                if (!CollectionUtils.isEmpty(availImages)
                        && !imageServer.getTftpBootDir().equals(bootDir)) {
                    log.info("Cannot update TFTPBOOT directory, while "
                            + "an image server has associated successful import images.");
                    throw APIException.badRequests
                            .cannotUpdateTFTPBOOTDirectory();
                } else {
                    imageServer.setTftpBootDir(bootDir);
                }
            }
            if(StringUtils.isNotBlank(password)){
                imageServer.setImageServerPassword(password);
            }
            auditOp(OperationTypeEnum.IMAGESERVER_VERIFY_IMPORT_IMAGES, true,
                    null, imageServer.getId().toString(),
                    imageServer.getImageServerIp());

            _dbClient.updateObject(imageServer);

            ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
            String taskId = UUID.randomUUID().toString();
            AsyncTask task = new AsyncTask(ComputeImageServer.class,
                    imageServer.getId(), taskId);
            tasks.add(task);
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.UPDATE_VERIFY_COMPUTE_IMAGE_SERVER);
            _dbClient.createTaskOpStatus(ComputeImageServer.class,
                    imageServer.getId(), taskId, op);

            ImageServerController controller = getController(
                    ImageServerController.class, null);
            controller.verifyImageServerAndImportExistingImages(task,
                    op.getName());
        }
        return map(_dbClient, imageServer);
    }

    /**
     * List data of compute image servers based on input ids.
     *
     * @param param
     *            POST data containing the id list.
     * @prereq none
     * @brief List data of compute image servers
     * @return List of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ComputeImageServerBulkRep getBulkResources(BulkIdParam param) {
        return (ComputeImageServerBulkRep) super.getBulkResources(param);
    }

    @Override
    public ComputeImageServerBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<ComputeImageServer> _dbIterator = _dbClient
                .queryIterativeObjects(getResourceClass(), ids);
        return new ComputeImageServerBulkRep(BulkList.wrapping(_dbIterator,
                COMPUTE_IMAGESERVER_MAPPER));
    }

    private final ComputeImageServerMapper COMPUTE_IMAGESERVER_MAPPER = new ComputeImageServerMapper();

    private class ComputeImageServerMapper implements
            Function<ComputeImageServer, ComputeImageServerRestRep> {
        @Override
        public ComputeImageServerRestRep apply(
                final ComputeImageServer imageserver) {
            return ComputeMapper.map(_dbClient, imageserver);
        }
    }

    @Override
    protected ComputeImageServerBulkRep queryFilteredBulkResourceReps(
            List<URI> ids) {
        return queryBulkResourceReps(ids);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ComputeImageServer> getResourceClass() {
        return ComputeImageServer.class;
    }

    /**
     * Check if the given imageServer has any active computeImageJob
     * if so throws an appropriate exception
     * @param imageServerURI
     */
    private void checkActiveJobsForImageServer(URI imageServerURI) {
        log.info(
                "Check if any active ComputeImageJobs are present for imageServer id {} ",
                imageServerURI);
        // make sure there are no active jobs associated with this imageserver
        URIQueryResultList computeImageJobsUriList = new URIQueryResultList();
        _dbClient
                .queryByConstraint(
                        ContainmentConstraint.Factory
                                .getComputeImageJobsByComputeImageServerConstraint(imageServerURI),
                        computeImageJobsUriList);
        Iterator<URI> iterator = computeImageJobsUriList.iterator();
        while (iterator.hasNext()) {
            ComputeImageJob job = _dbClient.queryObject(ComputeImageJob.class,
                    iterator.next());
            if (job.getJobStatus().equals(
                    ComputeImageJob.JobStatus.CREATED.name())) {
                throw APIException.badRequests
                        .cannotDeleteOrUpdateImageServerWhileInUse();
            }
        }
    }

    /**
     * Removes the given imageServerId from each ComputeSystem present,
     * if the computeSystem has the given imageServerId as it association or relation.
     * Disassociate's  the imageServer from the computeSystem.
     * @param imageServerID {@link URI} computeImageServer id
     */
    private void removeImageServerFromComputeSystem(URI imageServerID) {
        // Remove the association with the ComputeSystem and then delete
        // the imageServer
        List<URI> computeSystemURIList = _dbClient.queryByType(
                ComputeSystem.class, true);
        if (computeSystemURIList != null
                && computeSystemURIList.iterator().hasNext()) {
            List<ComputeSystem> computeSystems = _dbClient.queryObject(
                    ComputeSystem.class, computeSystemURIList);
            if (!CollectionUtils.isEmpty(computeSystems)) {
                for (ComputeSystem computeSystem : computeSystems) {
                    if (computeSystem.getComputeImageServer() != null
                            && computeSystem.getComputeImageServer()
                                    .equals(imageServerID)) {
                        computeSystem
                                .setComputeImageServer(NullColumnValueGetter
                                        .getNullURI());
                        _dbClient.updateObject(computeSystem);
                        log.info(
                                "Disassociating imageServer {} from ComputeSystem id {} ",
                                imageServerID, computeSystem.getId());
                    }
                }
            }
        }
    }

    /**
     * Check if the imageServer already exists, this method checks by both
     * name/label and IP.
     * @param id {@link URI} imageServer URI
     * @param imageServerAddress {@link String} imageServer IP/FQDN
     * @param imageServerName {@link String} label/user given name for imageServer
     */
    private void checkDuplicateImageServer(URI id, String imageServerAddress,
            String imageServerName) {
        if (StringUtils.isNotBlank(imageServerName)) {
            checkDuplicateLabel(ComputeImageServer.class, imageServerName);
        }
        List<URI> existingImageServers = _dbClient.queryByType(
                ComputeImageServer.class, false);
        for (URI uri : existingImageServers) {
            ComputeImageServer existing = _dbClient.queryObject(
                    ComputeImageServer.class, uri);
            if (existing == null || existing.getInactive()
                    || existing.getId().equals(id)) {
                continue;
            }
            if (existing.getImageServerIp() != null
                    && imageServerAddress != null
                    && existing.getImageServerIp().equalsIgnoreCase(
                            imageServerAddress)) {
                throw APIException.badRequests
                        .resourceExistsWithSameName(imageServerAddress);
            }
        }
    }

    /**
     * Remove computeImage associations (both success image and failed images
     * associations)for a given imageServer
     *
     * @param imageServer {@link ComputeImageServer} instance
     */
    private void disassociateComputeImages(ComputeImageServer imageServer) {
        StringSet successImages = imageServer.getComputeImages();
        if (!CollectionUtils.isEmpty(successImages)) {
            Iterator<String> itr = successImages.iterator();
            while (itr.hasNext()) {
                itr.next();
                itr.remove();
            }
        }
        StringSet failedImages = imageServer.getFailedComputeImages();
        if (!CollectionUtils.isEmpty(failedImages)) {
            Iterator<String> itr = failedImages.iterator();
            while (itr.hasNext()) {
                itr.next();
                itr.remove();
            }
        }
    }
}
