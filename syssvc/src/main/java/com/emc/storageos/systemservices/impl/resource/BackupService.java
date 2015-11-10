/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.io.PipedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.net.URI;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.core.*;

import com.emc.storageos.management.backup.BackupFile;
import com.emc.storageos.management.backup.BackupFileSet;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.jobs.backupscheduler.BackupScheduler;
import com.emc.storageos.systemservices.impl.jobs.common.JobProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.management.backup.BackupOps;
import com.emc.storageos.management.backup.BackupSetInfo;
import com.emc.storageos.management.backup.exceptions.BackupException;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.systemservices.impl.resource.util.ClusterNodesUtil;
import com.emc.storageos.systemservices.impl.resource.util.NodeInfo;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.model.sys.backup.BackupSets;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;
import static com.emc.vipr.model.sys.backup.BackupUploadStatus.Status;

/**
 * Defines the API for making requests to the backup service.
 */
@Path("/backupset/")
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private BackupOps backupOps;
    private BackupScheduler backupScheduler;
    private JobProducer jobProducer;
    private NamedThreadPoolExecutor backupDownloader = new NamedThreadPoolExecutor("BackupDownloader", 10);

    /**
     * Sets backup client
     * 
     * @param backupOps the backup client instance
     */
    public void setBackupOps(BackupOps backupOps) {
        this.backupOps = backupOps;
    }

    /**
     * Sets backup scheduler client
     *
     * @param backupScheduler the backup scheduler client instance
     */
    public void setBackupScheduler(BackupScheduler backupScheduler) {
        this.backupScheduler = backupScheduler;
    }

    /**
     * Sets backup upload job producer
     *
     * @param jobProducer the backup upload job producer
     */
    public void setJobProducer(JobProducer jobProducer) {
        this.jobProducer = jobProducer;
    }

    /**
     * Default constructor.
     */
    public BackupService() {
    }

    /**
     * List the info of backupsets that have zk backup file and
     * quorum db and geodb backup files
     * 
     * @brief List current backup info
     * @prereq none
     * @return A list of backup info
     */
    @GET
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.RESTRICTED_SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public BackupSets listBackup() {
        List<BackupSetInfo> backupList = new ArrayList<BackupSetInfo>();

        log.info("Received list backup request");
        try {
            backupList = backupOps.listBackup();
        } catch (BackupException e) {
            log.error("Failed to list backup sets, e=", e);
            throw APIException.internalServerErrors.getObjectError("Backup info", e);
        }

        return toBackupSets(backupList);
    }

    private BackupSets toBackupSets(List<BackupSetInfo> backupList) {
        BackupSets backupSets = new BackupSets();
        for (BackupSetInfo backupInfo : backupList) {
            BackupUploadStatus uploadStatus = getBackupUploadStatus(backupInfo.getName());
            backupSets.getBackupSets().add(new BackupSets.BackupSet(
                    backupInfo.getName(),
                    backupInfo.getSize(),
                    backupInfo.getCreateTime(),
                    uploadStatus));
        }
        return backupSets;
    }

    /**
     * List the info of backupsets that have zk backup file and
     * quorum db and geodb backup files
     *
     * @brief List current backup info
     * @prereq none
     * @return A list of backup info
     */
    @GET
    @Path("backup/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.RESTRICTED_SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public BackupSets.BackupSet queryBackup(@QueryParam("tag") String backupTag) {
        List<BackupSetInfo> backupList;

        log.info("Received query backup request, tag={}", backupTag);
        try {
            backupList = backupOps.listBackup();
        } catch (BackupException e) {
            log.error("Failed to list backup sets", e);
            throw APIException.internalServerErrors.getObjectError("Backup info", e);
        }
        for (BackupSetInfo backupInfo : backupList) {
            if (backupInfo.getName().equals(backupTag)) {
                BackupUploadStatus uploadStatus = getBackupUploadStatus(backupInfo.getName());
                BackupSets.BackupSet backupSet = new BackupSets.BackupSet(backupInfo.getName(), backupInfo.getSize(),
                        backupInfo.getCreateTime(), uploadStatus);
                log.info("BackupSet={}", backupSet.toString());
                return backupSet;
            }
        }
        return new BackupSets.BackupSet();
    }

    /**
     * Create a near Point-In-Time copy of DB & ZK data files on all controller nodes.
     * 
     * @brief Create a backup set
     * 
     *        <p>
     *        Limitations of the argument: 1. Maximum length is 200 characters 2. Underscore "_" is not supported 3. Any character that is
     *        not supported by Linux file name is not allowed
     * 
     * @param backupTag The name of backup. This parameter is optional,
     *            default is timestamp(for example 20140531193000).
     * @param forceCreate If true, will ignore the errors during the operation
     *            and force create backup, and return success if zk backup file
     *            and quorum db and geodb backup files create succeed,
     *            or else return failures and roolback. A probable use senario
     *            of this paramter is single node crash.
     * @prereq none
     * @return server response indicating if the operation succeeds.
     */
    @POST
    @Path("backup/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response createBackup(@QueryParam("tag") String backupTag,
            @QueryParam("force") @DefaultValue("false") boolean forceCreate) {
        log.info("Received create backup request, backup tag={}", backupTag);
        try {
            backupOps.createBackup(backupTag, forceCreate);
        } catch (BackupException e) {
            log.error("Failed to create backup(tag={}), e=", backupTag, e);
            throw APIException.internalServerErrors.createObjectError("Backup files", e);
        }
        return Response.ok().build();
    }

    /**
     * Delete the specific backup files on each controller node of cluster
     * 
     * @brief Delete a backup
     * @param backupTag The name of backup
     * @prereq This backup sets should have been created
     * @return server response indicating if the operation succeeds.
     */
    @DELETE
    @Path("backup/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteBackup(@QueryParam("tag") String backupTag) {
        log.info("Received delete backup request, backup tag={}", backupTag);
        if (backupTag == null) {
            throw APIException.badRequests.parameterIsNotValid(backupTag);
        }
        try {
            backupOps.deleteBackup(backupTag);
        } catch (BackupException e) {
            log.error("Failed to delete backup(tag= {}), e=", backupTag, e);
            throw APIException.internalServerErrors.updateObjectError("Backup files", e);
        }
        return Response.ok().build();
    }

    @POST
    @Path("backup/upload/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response uploadBackup(@QueryParam("tag") final String backupTag) {
        log.info("Received upload backup request, backup tag={}", backupTag);

        BackupUploadStatus job = new BackupUploadStatus();
        job.setBackupName(backupTag);
        job.setStatus(Status.NOT_STARTED);
        jobProducer.enqueue(job);

        return Response.ok().build();
    }

    @GET
    @Path("backup/upload/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.RESTRICTED_SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public BackupUploadStatus getBackupUploadStatus(@QueryParam("tag") String backupTag) {
        log.info("Received get upload status request, backup tag={}", backupTag);
        try {
            BackupUploadStatus uploadStatus = backupScheduler.getUploadExecutor().getUploadStatus(backupTag);
            log.info("Current upload status is: {}", uploadStatus);
            return uploadStatus;
        } catch (Exception e) {
            log.error("Failed to get upload status", e);
            throw APIException.internalServerErrors.getObjectError("Upload status", e);
        }
    }

    /**
     * Download the zip archive that composed of DB & ZK backup files on all controller nodes
     * It's suggest to download backupset to external media timely after the creation
     * and then delete it to release the storage space
     * 
     * @brief Download a specific backupset
     * @param backupTag The name of backup
     * @prereq This backup sets should have been created
     * @return Zip file stream if the operation succeeds,
     */
    @GET
    @Path("download/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response downloadBackup(@QueryParam("tag") final String backupTag) {
        log.info("Received download backup request, backup tag={}", backupTag);
        try {
            final BackupFileSet files = getDownloadList(backupTag);
            if (files.isEmpty()) {
                throw APIException.internalServerErrors.createObjectError(
                        String.format("can not find target backup set '%s'.", backupTag),
                        null);
            }
            if (!files.isValid()) {
                throw APIException.internalServerErrors.noNodeAvailableError("download backup files");
            }

            InputStream pipeIn = getDownloadStream(files);

            return Response.ok(pipeIn).type(MediaType.APPLICATION_OCTET_STREAM).build();
        } catch (Exception e) {
            log.error("create backup final file failed. e=", e);
            throw APIException.internalServerErrors.createObjectError("Download backup files", e);
        }
    }

    /**
     * *Internal API, used only between nodes*
     * <p>
     * Get backup file name
     * 
     * @param fileName
     * @return the name and content info of backup files
     */
    @POST
    @Path("internal/node-backups/download")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response downloadFileFromNode(String fileName) {
        log.info("getBackup({})", fileName);
        try {
            File file = new File(this.backupOps.getBackupDir(), fileName);
            if (!file.exists()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            InputStream input = new FileInputStream(file);
            return Response.ok(input).type(MediaType.APPLICATION_OCTET_STREAM).build();
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError(
                    "backup file input stream", "local", e);
        }
    }

    /**
     * This method returns a list of files on each node to be downloaded for specified tag
     * 
     * @param backupTag
     * @return backupFileSet,
     *         if its size() is 0, means can not find the backup set of specified tag;
     *         if it is not isValid(), means can not get enough files for specified tag.
     */
    public BackupFileSet getDownloadList(String backupTag) {
        BackupFileSet files = this.backupOps.listRawBackup(true);

        BackupFileSet filesForTag = files.subsetOf(backupTag, null, null);
        return filesForTag;
    }

    private InputStream getDownloadStream(final BackupFileSet files) throws IOException {
        final PipedOutputStream pipeOut = new PipedOutputStream();
        PipedInputStream pipeIn = new PipedInputStream(pipeOut);

        Runnable runnable = new Runnable() {
            public void run() {
                try {
                    collectData(files, pipeOut);
                } catch (Exception ex) {
                    log.error("Exception when compressing", ex);
                    try {
                        pipeOut.close();
                    } catch (Exception ex2) {
                        log.error("Exception when terminating output", ex);
                    }
                }
            }
        };
        this.backupDownloader.submit(runnable);

        return pipeIn;
    }

    public void collectData(BackupFileSet files, OutputStream outStream) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(outStream);
        String backupTag = files.first().tag;

        Set<String> uniqueNodes = files.uniqueNodes();

        List<NodeInfo> nodes = ClusterNodesUtil.getClusterNodeInfo(new ArrayList<>(Arrays.asList(uniqueNodes.toArray(new String[uniqueNodes
                .size()]))));
        if (nodes.size() < uniqueNodes.size()) {
            log.info("Only {}/{} nodes available for the backup, cannot download.", uniqueNodes.size(), nodes.size());
            return;
        }

        Collections.sort(nodes, new Comparator<NodeInfo>() {
            @Override
            public int compare(NodeInfo o1, NodeInfo o2) {
                return o1.getId().compareTo(o2.getId());
            }
        });

        URI postUri = SysClientFactory.URI_NODE_BACKUPS_DOWNLOAD;
        boolean propertiesFileFound = false;
        int collectFileCount = 0;
        int totalFileCount = files.size() * 2;
        for (final NodeInfo node : nodes) {
            String baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT,
                    node.getIpAddress(), node.getPort());
            log.debug("processing node: {}", baseNodeURL);
            SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(
                    URI.create(baseNodeURL));
            for (String fileName : getFileNameList(files.subsetOf(null, null, node.getId()))) {
                int progress = collectFileCount / totalFileCount * 100;
                backupScheduler.getUploadExecutor().setUploadStatus(null, Status.IN_PROGRESS, progress, null);

                String fullFileName = backupTag + File.separator + fileName;
                InputStream in = sysClient.post(postUri, InputStream.class, fullFileName);
                newZipEntry(zos, in, fileName);
                collectFileCount++;
            }

            try {
                String fileName = backupTag + BackupConstants.BACKUP_INFO_SUFFIX;
                String fullFileName = backupTag + File.separator + fileName;
                InputStream in = sysClient.post(postUri, InputStream.class, fullFileName);
                newZipEntry(zos, in, fileName);
                propertiesFileFound = true;
            } catch (SysClientException ex) {
                log.info("info.properties file is not found on node {}, exception {}", node.getId(), ex.getMessage());
            }
        }

        if (!propertiesFileFound) {
            throw new FileNotFoundException(String.format("No live node contains %s%s", backupTag, BackupConstants.BACKUP_INFO_SUFFIX));
        }

        // We only close ZIP stream when everything is OK, or the package will be extractable but missing files.
        zos.close();

        log.info("Successfully generated ZIP package");
    }

    private List<String> getFileNameList(BackupFileSet files) {
        List<String> nameList = new ArrayList<>();

        for (BackupFile file : files) {
            nameList.add(file.info.getName());
            nameList.add(file.info.getName() + ".md5");
        }

        Collections.sort(nameList, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });

        return nameList;
    }

    private void newZipEntry(ZipOutputStream zos, InputStream in, String name) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        log.info("zip entry: {}", name);
        int length = 0;
        byte[] buffer = new byte[102400];
        while ((length = in.read(buffer)) != -1) {
            zos.write(buffer, 0, length);
        }
        in.close();
        zos.closeEntry();
    }
}
