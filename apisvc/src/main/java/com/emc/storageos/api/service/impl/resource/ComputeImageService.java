/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.ComputeMapper;
import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImage.ComputeImageStatus;
import com.emc.storageos.db.client.model.ComputeImageJob;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.compute.ComputeImageBulkRep;
import com.emc.storageos.model.compute.ComputeImageCreate;
import com.emc.storageos.model.compute.ComputeImageList;
import com.emc.storageos.model.compute.ComputeImageRestRep;
import com.emc.storageos.model.compute.ComputeImageUpdate;
import com.emc.storageos.imageservercontroller.ImageServerController;
import com.emc.storageos.imageservercontroller.impl.ImageServerControllerImpl;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Compute image service handles create, update, and remove of compute images.
 */
@Path("/compute/images")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, readAcls = { ACL.USE }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })
public class ComputeImageService extends TaskResourceService {

    private static final Logger log = LoggerFactory.getLogger(ComputeImageService.class);

    private static final String EVENT_SERVICE_TYPE = "ComputeImage";

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
    public ComputeImageRestRep getComputeImage(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, ComputeImage.class, "id");
        ComputeImage ci = queryResource(id);
        List<ComputeImageServer> successfulServers = new ArrayList<ComputeImageServer>();
        List<ComputeImageServer> failedServers = new ArrayList<ComputeImageServer>();
        getImageImportStatus(ci, successfulServers, failedServers);
        return ComputeMapper.map(ci, successfulServers, failedServers);
    }

    /**
     * Returns a list of all compute images.
     * 
     * @brief Show compute images
     * @return List of all compute images.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ComputeImageList getComputeImages(@QueryParam("imageType") String imageType) {
        log.info("getComputeImages, imageType: {}", imageType);

        // validate query param
        if (imageType != null) {
            ArgValidator.checkFieldValueFromEnum(imageType, "imageType",
                    ComputeImage.ImageType.class);
        }

        List<URI> ids = _dbClient.queryByType(ComputeImage.class, true);
        ComputeImageList list = new ComputeImageList();
        Iterator<ComputeImage> iter = _dbClient.queryIterativeObjects(ComputeImage.class, ids);
        while (iter.hasNext()) {
            ComputeImage img = iter.next();
            if (imageType == null || imageType.equals(img.getImageType())) {
                list.getComputeImages().add(DbObjectMapper.toNamedRelatedResource(img));
            }
        }
        return list;
    }

    public void getImageImportStatus(ComputeImage image, List<ComputeImageServer> successfulServers,
            List<ComputeImageServer> failedServers) {

        List<URI> ids = _dbClient.queryByType(ComputeImageServer.class,
                true);
        for (URI imageServerId : ids) {
            ComputeImageServer imageServer = _dbClient.queryObject(
                    ComputeImageServer.class, imageServerId);
            if (imageServer.getComputeImages() != null
                    && imageServer.getComputeImages().contains(
                            image.getId().toString())) {
                successfulServers.add(imageServer);
            } else {
                failedServers.add(imageServer);
            }
        }
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
    public TaskResourceRep createComputeImage(ComputeImageCreate param) {
        log.info("createComputeImage");
        // unique name required
        ArgValidator.checkFieldNotEmpty(param.getName(), "name");
        checkDuplicateLabel(ComputeImage.class, param.getName(), param.getName());

        ArgValidator.checkFieldNotEmpty(param.getImageUrl(), "image_url");
        ArgValidator.checkUrl(param.getImageUrl(), "image_url");
        if (!checkForImageServers()) {
            throw APIException.badRequests.cannotAddImageWithoutImageServer();
        }
        ComputeImage ci = new ComputeImage();
        ci.setId(URIUtil.createId(ComputeImage.class));

        // IN_PROGRESS until successfully loaded by image server controller
        ci.setComputeImageStatus(ComputeImageStatus.IN_PROGRESS.name());

        ci.setLabel(param.getName());
        ci.setImageUrl(encryptImageURLPassword(param.getImageUrl(), false));

        _dbClient.createObject(ci);

        auditOp(OperationTypeEnum.CREATE_COMPUTE_IMAGE, true, AuditLogManager.AUDITOP_BEGIN, ci.getId().toString(),
                ci.getImageUrl(), ci.getComputeImageStatus());
        try {

            return doImportImage(ci);
        } catch (Exception e) {
            ci.setComputeImageStatus(ComputeImageStatus.NOT_AVAILABLE.name());
            _dbClient.updateObject(ci);
            throw e;
        }
    }

    /*
     * Returns task in ready state.
     */
    private TaskResourceRep getReadyOp(ComputeImage ci, ResourceOperationTypeEnum opType) {
        log.info("doImportImageDone");
        String taskId = UUID.randomUUID().toString();
        AsyncTask task = new AsyncTask(ComputeImage.class, ci.getId(), taskId);
        Operation readyOp = new Operation();
        readyOp.ready();
        readyOp.setResourceType(opType);
        _dbClient.createTaskOpStatus(ComputeImage.class, ci.getId(), task._opId, readyOp);

        return TaskMapper.toTask(ci, task._opId, readyOp);
    }

    /*
     * Schedules the import task.
     */
    private TaskResourceRep doImportImage(ComputeImage ci) {
        log.info("doImportImage");
        ImageServerController controller = getController(ImageServerController.class, null);
        AsyncTask task = new AsyncTask(ComputeImage.class, ci.getId(), UUID.randomUUID().toString());

        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.IMPORT_IMAGE);
        _dbClient.createTaskOpStatus(ComputeImage.class, ci.getId(), task._opId, op);
        controller.importImageToServers(task);
        return TaskMapper.toTask(ci, task._opId, op);
    }

    /**
     * Updates an already present compute image.
     * 
     * @param id
     *            compute image URN.
     * @param param
     *            The ComputeImageUpdate object with attributes to be updated.
     * @brief Update compute image details
     * @return Update task REST representation.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateComputeImage(@PathParam("id") URI id, ComputeImageUpdate param) {
        log.info("updateComputeImage: {}, new name: {}", id, param.getName());
        ArgValidator.checkFieldUriType(id, ComputeImage.class, "id");
        ArgValidator.checkFieldNotEmpty(param.getName(), "name");

        ComputeImage ci = _dbClient.queryObject(ComputeImage.class, id);
        ArgValidator.checkEntity(ci, id, isIdEmbeddedInURL(id));

        if (!ci.getLabel().equals(param.getName())) {
            checkDuplicateLabel(ComputeImage.class, param.getName(), param.getName());
            ci.setLabel(param.getName());
        }

        boolean reImport = false;

        // see if image URL needs updating
        if (!StringUtils.isBlank(param.getImageUrl()) && !param.getImageUrl().equals(ci.getImageUrl())) {
            ArgValidator.checkUrl(param.getImageUrl(), "image_url");

            // URL can only be update if image not successfully loaded
            if (ci.getComputeImageStatus().equals(
                    ComputeImageStatus.NOT_AVAILABLE.name())) {
                String prevImageUrl = ci.getImageUrl();
                boolean isEncrypted = false;
                String oldPassword = ImageServerControllerImpl
                        .extractPasswordFromImageUrl(prevImageUrl);
                String newPassword = ImageServerControllerImpl
                        .extractPasswordFromImageUrl(param.getImageUrl());

                if (StringUtils.isNotBlank(oldPassword)
                        && StringUtils.isNotBlank(newPassword)
                        && oldPassword.equals(newPassword)) {
                    isEncrypted = true;
                }
                ci.setImageUrl(encryptImageURLPassword(param.getImageUrl(),
                        isEncrypted));

                ci.setComputeImageStatus(ComputeImageStatus.IN_PROGRESS.name());
                reImport = true;
            } else {
                throw APIException.badRequests.invalidParameterCannotUpdateComputeImageUrl();
            }
        }

        _dbClient.updateObject(ci);

        auditOp(OperationTypeEnum.UPDATE_COMPUTE_IMAGE, true, null,
                ci.getId().toString(), ci.getImageUrl());

        return createUpdateTasks(ci, reImport);

    }

    /**
     * Delete existing compute image.
     * 
     * @param id
     *            compute image URN.
     * @brief Delete compute image
     * @return Async task remove the image from multiple image serevers returned in response body
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep deleteComputeImage(@PathParam("id") URI id,
            @QueryParam("force") String force) {
        log.info("deleteComputeImage: {}", id);

        ComputeImage ci = queryObject(ComputeImage.class, id, true);
        ArgValidator.checkEntity(ci, id, isIdEmbeddedInURL(id));

        if (ComputeImage.ComputeImageStatus.AVAILABLE.name().equals(
                ci.getComputeImageStatus())) {

            if (force == null || !force.equals("true")) {
                // make sure there are no active jobs associated with this image
                URIQueryResultList ceUriList = new URIQueryResultList();
                _dbClient.queryByConstraint(
                        ContainmentConstraint.Factory
                                .getComputeImageJobsByComputeImageConstraint(ci
                                        .getId()),
                        ceUriList);
                Iterator<URI> iterator = ceUriList.iterator();
                while (iterator.hasNext()) {
                    ComputeImageJob job = _dbClient.queryObject(
                            ComputeImageJob.class, iterator.next());
                    if (job.getJobStatus().equals(
                            ComputeImageJob.JobStatus.CREATED.name())) {
                        throw APIException.badRequests
                                .cannotDeleteComputeWhileInUse();
                    }
                }
            }

            auditOp(OperationTypeEnum.DELETE_COMPUTE_IMAGE, true,
                    AuditLogManager.AUDITOP_BEGIN, ci.getId().toString(),
                    ci.getImageUrl());

            return doRemoveImage(ci);

        } else if (ComputeImage.ComputeImageStatus.IN_PROGRESS.name().equals(
                ci.getComputeImageStatus())) {
            if (force == null || !force.equals("true")) {
                throw APIException.badRequests.resourceCannotBeDeleted(ci
                        .getLabel());
            } else { // delete is forced
                deleteImageFromImageServers(ci);
                _dbClient.markForDeletion(ci);
                auditOp(OperationTypeEnum.DELETE_COMPUTE_IMAGE, true, null, ci
                        .getId().toString(), ci.getImageUrl());
                return getReadyOp(ci, ResourceOperationTypeEnum.REMOVE_IMAGE);
            }
        } else { // NOT_AVAILABLE
            deleteImageFromImageServers(ci);
            _dbClient.markForDeletion(ci);
            auditOp(OperationTypeEnum.DELETE_COMPUTE_IMAGE, true, null, ci
                    .getId().toString(), ci.getImageUrl());
            return getReadyOp(ci, ResourceOperationTypeEnum.REMOVE_IMAGE);
        }

    }

    /*
     * Schedules the remove task.
     */
    private TaskResourceRep doRemoveImage(ComputeImage ci) {
        log.info("doRemoveImage");
        ImageServerController controller = getController(
                ImageServerController.class, null);

        AsyncTask task = new AsyncTask(ComputeImage.class, ci.getId(), UUID
                .randomUUID().toString());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.REMOVE_IMAGE);

        _dbClient.createTaskOpStatus(ComputeImage.class, ci.getId(),
                task._opId, op);
        controller.deleteImage(task);

        log.info("Removing image " + ci.getImageName());

        return TaskMapper.toTask(ci, task._opId, op);
    }

    /**
     * List data of compute images based on input ids.
     * 
     * @param param
     *            POST data containing the id list.
     * @prereq none
     * @brief List data of compute images
     * @return List of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ComputeImageBulkRep getBulkResources(BulkIdParam param) {
        return (ComputeImageBulkRep) super.getBulkResources(param);
    }

    @Override
    public ComputeImageBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<ComputeImage> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new ComputeImageBulkRep(BulkList.wrapping(_dbIterator, COMPUTE_IMAGE_MAPPER));
    }

    private final ComputeImageMapper COMPUTE_IMAGE_MAPPER = new ComputeImageMapper();

    private class ComputeImageMapper implements Function<ComputeImage, ComputeImageRestRep> {
        @Override
        public ComputeImageRestRep apply(final ComputeImage ci) {
            List<ComputeImageServer> successfulServers = new ArrayList<ComputeImageServer>();
            List<ComputeImageServer> failedServers = new ArrayList<ComputeImageServer>();
            getImageImportStatus(ci, successfulServers, failedServers);
            return ComputeMapper.map(ci, successfulServers, failedServers);
        }
    }

    @Override
    protected ComputeImageBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        return queryBulkResourceReps(ids);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.COMPUTE_IMAGE;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ComputeImage> getResourceClass() {
        return ComputeImage.class;
    }

    @Override
    protected ComputeImage queryResource(URI id) {
        return queryObject(ComputeImage.class, id, false);
    }

    /**
     * Method to create and initiate task to controller.
     * 
     * @param ci
     *            {@link ComputeImage} instance
     * @param reImport
     *            boolean to let identify if a reimport of images is required
     * @return {@link TaskResourceRep}
     */
    private TaskResourceRep createUpdateTasks(ComputeImage ci, boolean reImport) {
        boolean hasImportTask = false;
        try {

            List<URI> ids = _dbClient.queryByType(ComputeImageServer.class,
                    true);
            for (URI imageServerId : ids) {
                ComputeImageServer imageServer = _dbClient.queryObject(
                        ComputeImageServer.class, imageServerId);

                if (reImport
                        || imageServer.getComputeImages() == null
                        || !imageServer.getComputeImages().contains(
                                ci.getId().toString())) {
                    hasImportTask = true;
                    break;
                }
            }
            if (hasImportTask) {
                return doImportImage(ci);
            } else {
                return getReadyOp(ci, ResourceOperationTypeEnum.UPDATE_IMAGE);
            }
        } catch (Exception e) {
            ci.setComputeImageStatus(ComputeImageStatus.NOT_AVAILABLE.name());
            _dbClient.updateObject(ci);
            throw e;
        }
    }

    /**
     * Delete any image references or associations from all existing ImageServers.
     * @param ci {@link ComputeImage}
     */
    private void deleteImageFromImageServers(ComputeImage ci) {
        List<URI> ids = _dbClient.queryByType(ComputeImageServer.class, true);
        for (URI imageServerId : ids) {
            ComputeImageServer imageServer = _dbClient.queryObject(
                    ComputeImageServer.class, imageServerId);

            if (imageServer.getFailedComputeImages() != null
                    && imageServer.getFailedComputeImages().contains(
                            ci.getId().toString())) {
                imageServer.getFailedComputeImages().remove(
                        ci.getId().toString());
            } else if (imageServer.getComputeImages() != null
                    && imageServer.getComputeImages().contains(
                            ci.getId().toString())) {
                imageServer.getComputeImages().remove(
                        ci.getId().toString());
            }
            _dbClient.updateObject(imageServer);
        }
    }

    /**
     * Check if there are image Servers in the system
     */
    private boolean checkForImageServers() {
        boolean imageServerExists = true;
        List<URI> imageServerURIList = _dbClient.queryByType(
                ComputeImageServer.class, true);
        ArrayList<URI> tempList = Lists.newArrayList(imageServerURIList
                .iterator());

        if (tempList.isEmpty()) {
            imageServerExists = false;
        }
        return imageServerExists;
    }

    /**
     * Method to mask/encrypt password of the ImageUrl
     * @param imageUrl {@link String} compute image URL string
     * @param isEncrypted boolean indicating if password is already encrypted.
     * @return
     */
    private String encryptImageURLPassword(String imageUrl, boolean isEncrypted) {
        String password = ImageServerControllerImpl
                .extractPasswordFromImageUrl(imageUrl);
        String encryptedPassword = password;
        if (!isEncrypted && StringUtils.isNotBlank(password)) {
            EncryptionProviderImpl encryptionProviderImpl = new EncryptionProviderImpl();
            encryptionProviderImpl.setCoordinator(_coordinator);
            encryptionProviderImpl.start();
            EncryptionProvider encryptionProvider = encryptionProviderImpl;
            encryptedPassword = encryptionProvider.getEncryptedString(password);
            imageUrl = StringUtils.replace(imageUrl, ":" + password + "@", ":"
                    + encryptedPassword + "@");
        }
        return imageUrl;
    }

}
