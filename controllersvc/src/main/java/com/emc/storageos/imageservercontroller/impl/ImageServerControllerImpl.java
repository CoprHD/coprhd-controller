/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller.impl;

import static com.emc.storageos.imageservercontroller.ImageServerConstants.DEFAULT_FILE;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_FAILURE_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_FIRSTBOOT_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_KICKSTART_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.HTTP_SUCCESS_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.PXELINUX_0_FILE;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.PXELINUX_CFG_DIR;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.SERVER_PY_FILE;
import static com.emc.storageos.imageservercontroller.ImageServerConstants.WGET_FILE;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.computecontroller.impl.ComputeDeviceController;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImageJob;
import com.emc.storageos.db.client.model.ComputeImageJob.JobStatus;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.imageservercontroller.ComputeImageCompleter;
import com.emc.storageos.imageservercontroller.ComputeImageServerCompleter;
import com.emc.storageos.imageservercontroller.ImageServerController;
import com.emc.storageos.imageservercontroller.OsInstallCompleter;
import com.emc.storageos.imageservercontroller.exceptions.ImageServerControllerException;
import com.emc.storageos.imageservercontroller.impl.OsInstallStatusPoller.OsInstallStatus;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.networkcontroller.SSHSession;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class ImageServerControllerImpl implements ImageServerController {

    private static final String EVENT_SERVICE_TYPE = "IMAGE_SERVER_CONTROLLER";

    private final static String IMPORT_IMAGE_WF = "IMPORT_IMAGE_WORKFLOW";
    private final static String IMPORT_IMAGE_STEP = "IMPORT_IMAGE_STEP";

    private final static String DELETE_IMAGE_WF = "DELETE_IMAGE_WORKFLOW";
    private final static String DELETE_IMAGE_STEP = "DELETE_IMAGE_STEP";

    private final static String OS_INSTALL_WF = "OS_INSTALL_WORKFLOW";
    private final static String OS_INSTALL_IMAGE_SERVER_CHECK_STEP = "OS_INSTALL_IMAGE_SERVER_CHECK_STEP";
    private final static String OS_INSTALL_PREPARE_PXE_STEP = "OS_INSTALL_PREPARE_PXE_STEP";
    private final static String OS_INSTALL_WAIT_FOR_FINISH_STEP = "OS_INSTALL_WAIT_FOR_FINISH_STEP";

    private static final String ROLLBACK_NOTHING_METHOD = "rollbackNothingMethod";

    private static final String TMP = "/tmp";

    private static final Logger log = LoggerFactory.getLogger(ImageServerControllerImpl.class);

    private DbClient dbClient;

    private WorkflowService workflowService;

    private ComputeDeviceController computeDeviceController;

    private PxeIntegrationService pxeIntegrationService;

    private OsInstallStatusPoller osInstallStatusPoller;

    private String imageServerErrorMsg = null;
    private final static int IMAGE_SERVER_VERSION = 100;

    @Autowired
    private AuditLogManager _auditMgr;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setOsInstallStatusPoller(OsInstallStatusPoller osInstallStatusPoller) {
        this.osInstallStatusPoller = osInstallStatusPoller;
    }

    public void setPxeIntegrationService(PxeIntegrationService pxeIntegrationService) {
        this.pxeIntegrationService = pxeIntegrationService;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public void setComputeDeviceController(ComputeDeviceController computeDeviceController) {
        this.computeDeviceController = computeDeviceController;
    }




    private boolean isImageServerValid(ComputeImageServer imageServer){
    	boolean valid = false;
        // make sure all required fields are set
        if (!StringUtils.isBlank(imageServer.getImageServerIp()) && !StringUtils.isBlank(imageServer.getImageServerUser())
                && !StringUtils.isBlank(imageServer.getTftpbootDir()) && !StringUtils.isBlank(imageServer.getImageServerSecondIp())
                && !StringUtils.isBlank(imageServer.getImageServerHttpPort()) && !StringUtils.isBlank(imageServer.getImageServerPassword())) {
            log.info("ImageServer appears valid");
            valid = true;
        }
        return valid;
    }

    /**
     * The following is expected to exist on the image server:
     * TFTPBOOT directory
     * pxelinux.0 binary
     * python
     * Everything else if doesn't exist, will be pushed.
     */
    private boolean verifyImageServer(ComputeImageServer imageServer) {
        log.info("verifyImageServer: {}", imageServer.getImageServerIp());
        boolean imageServerVerified = false;

        if (!isImageServerValid(imageServer)) {
            imageServerErrorMsg = "Image server settings are not valid, can't verify the server";
            log.warn(imageServerErrorMsg);
            return imageServerVerified;
        }

        ImageServerDialog d = null;
        try {
            SSHSession session = new SSHSession();
            session.connect(imageServer.getImageServerIp(), imageServer.getSshPort(),
                    imageServer.getImageServerUser(), imageServer.getImageServerPassword());
            d = new ImageServerDialog(session, imageServer.getSshTimeoutMs());
            d.init();

            if (!d.directoryExists(imageServer.getTftpbootDir())) {
                throw ImageServerControllerException.exceptions.imageServerNotSetup("tftpboot directory does not exist");
            }

            if (!d.fileExists(imageServer.getTftpbootDir() + PXELINUX_0_FILE)) {
                throw ImageServerControllerException.exceptions.imageServerNotSetup("pxelinux.0 binary does not exist");
            }

            if (!d.fileExists("/usr/bin/python")) {
                throw ImageServerControllerException.exceptions.imageServerNotSetup("python not found");
            }

            // check is.properties file if upgrade is needed
            // perform upgrade if file is not there, or version property is not
            // found or not valid or less then IMAGE_SERVER_VERSION
            boolean upgradeRequired = false;
            if (!d.fileExists(imageServer.getTftpbootDir() + "is.properties")) {
                upgradeRequired = true;
            } else {
                String s = d.readFile(imageServer.getTftpbootDir() + "is.properties");
                Properties p = ImageServerUtils.stringToProperties(s);
                if (p.getProperty("version") == null) {
                    upgradeRequired = true;
                } else {
                    try {
                        int version = Integer.parseInt(p.getProperty("version"));
                        if (version < IMAGE_SERVER_VERSION) {
                            upgradeRequired = true;
                        }
                    } catch (NumberFormatException e) {
                        upgradeRequired = true;
                    }
                }
            }

            log.info("image server upgrade required: {}", upgradeRequired);

            if (!d.directoryExists(imageServer.getTftpbootDir() + PXELINUX_CFG_DIR)) {
                log.info("pxelinux.cfg does not exist, will create it");
                d.mkdir(imageServer.getTftpbootDir() + PXELINUX_CFG_DIR);
            }

            if (!StringUtils.isBlank(imageServer.getImageDir())) {
                if (!d.directoryExists(imageServer.getTftpbootDir() + imageServer.getImageDir())) {
                    log.info("image directory does not exist, will create it");
                    d.mkdir(imageServer.getTftpbootDir() + imageServer.getImageDir());
                }
            }

            if (upgradeRequired || !d.fileExists(imageServer.getTftpbootDir() + PXELINUX_CFG_DIR + DEFAULT_FILE)) {
                log.info("creating pxelinux.cfg/default");
                String content = ImageServerUtils.getResourceAsString("imageserver/default");
                d.writeFile(imageServer.getTftpbootDir() + PXELINUX_CFG_DIR + DEFAULT_FILE, content);
            }

            if (!d.directoryExists(imageServer.getTftpbootDir() + HTTP_DIR)) {
                log.info("http does not exist, will create it");
                d.mkdir(imageServer.getTftpbootDir() + HTTP_DIR);
            }

            if (upgradeRequired || !d.fileExists(imageServer.getTftpbootDir() + HTTP_DIR + SERVER_PY_FILE)) {
                log.info("creating server.py");
                String content = ImageServerUtils.getResourceAsString("imageserver/server.py");
                StringBuilder script = new StringBuilder(content);
                ImageServerUtils.replaceAll(script, "{http.port}", imageServer.getImageServerHttpPort());
                d.writeFile(imageServer.getTftpbootDir() + HTTP_DIR + SERVER_PY_FILE, script.toString());
                d.chmodFile("744", imageServer.getTftpbootDir() + HTTP_DIR + SERVER_PY_FILE);
            }

            String pid = d.getServerPid(imageServer.getImageServerHttpPort());
            if (upgradeRequired && pid != null) {
                // if update required and server is running, kill it
                log.info("{} is running as pid: {}, kill it", SERVER_PY_FILE, pid);
                d.kill(pid);
                pid = null;
            }
            if (pid == null) {
                log.info("{} is not running, will attempt to start it", SERVER_PY_FILE);
                d.cd(imageServer.getTftpbootDir() + HTTP_DIR);
                d.nohup(String.format("python %s", SERVER_PY_FILE));
            }

            if (upgradeRequired || !d.fileExists(imageServer.getTftpbootDir() + HTTP_DIR + WGET_FILE)) {
                log.info("creating wget wrapper script");
                String content = ImageServerUtils.getResourceAsString("imageserver/wget");
                d.writeFile(imageServer.getTftpbootDir() + HTTP_DIR + WGET_FILE, content);
            }

            if (!d.directoryExists(imageServer.getTftpbootDir() + HTTP_KICKSTART_DIR)) {
                log.info("http/ks does not exist, will create it");
                d.mkdir(imageServer.getTftpbootDir() + HTTP_KICKSTART_DIR);
            }

            if (!d.directoryExists(imageServer.getTftpbootDir() + HTTP_FIRSTBOOT_DIR)) {
                log.info("http/fb does not exist, will create it");
                d.mkdir(imageServer.getTftpbootDir() + HTTP_FIRSTBOOT_DIR);
            }

            if (!d.directoryExists(imageServer.getTftpbootDir() + HTTP_SUCCESS_DIR)) {
                log.info("http/success does not exist, will create it");
                d.mkdir(imageServer.getTftpbootDir() + HTTP_SUCCESS_DIR);
            }

            if (!d.directoryExists(imageServer.getTftpbootDir() + HTTP_FAILURE_DIR)) {
                log.info("http/failure does not exist, will create it");
                d.mkdir(imageServer.getTftpbootDir() + HTTP_FAILURE_DIR);
            }

            // save is.properties
            if (upgradeRequired) {
                log.info("saving is.properties");
                d.writeFile(imageServer.getTftpbootDir() + "is.properties", "version=" + IMAGE_SERVER_VERSION
                        + "\nhttp_port=" + imageServer.getImageServerHttpPort());
            }

            log.info("image server setup was successfully verified");
            imageServerVerified = true;
            imageServerErrorMsg = null;
        } catch (Exception e) {
            log.error("Unexpected exception during image server verification: " + e.getMessage(), e);
            imageServerErrorMsg = e.getMessage();
        } finally {
            if (d != null && d.isConnected()) {
                d.close();
            }
        }
        return imageServerVerified;
    }

    @Override
    public void importImage(AsyncTask task,URI imageServerId) throws InternalException {
        log.info("importImage");

        URI ciId = task._id;
        ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, imageServerId);
        TaskCompleter completer = null;
        try {
            completer = new ComputeImageCompleter(ciId, task._opId, OperationTypeEnum.CREATE_COMPUTE_IMAGE, EVENT_SERVICE_TYPE);
            boolean imageServerVerified = verifyImageServer(imageServer);
            if (!imageServerVerified) {

                throw ImageServerControllerException.exceptions.imageServerNotSetup("Can't perform image import: "
                            + imageServerErrorMsg);
            }

            Workflow workflow = workflowService.getNewWorkflow(this, IMPORT_IMAGE_WF, true, task._opId);
            workflow.createStep(IMPORT_IMAGE_STEP,
                    String.format("importing image %s", ciId), null,
                    ciId, ciId.toString(),
                    this.getClass(),
                    new Workflow.Method("importImageMethod", ciId, imageServer.getId()),
                    null,
                    null);
            workflow.executePlan(completer, "Success");
        } catch (Exception e) {
            log.error("importImage caught an exception.", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(dbClient, serviceError);
        }
    }

    private void sanitizeUrl(String url) {
        try {
            new URL(url); // NOSONAR ("We are using this line to verify valid URL")
        } catch (MalformedURLException e) {
            throw ImageServerControllerException.exceptions.urlSanitationFailure(url, e);
        }
    }

    /**
     * Extract "update02" from file path.
     * Example:
     * /tmp/ESX-4.1.0-update02-502767.iso
     * /tmp/VMware-VMvisor-Installer-4.1.0.update02-502767.x86_64.iso
     *
     * @param isoPath
     * @return
     */
    private String extractUpdateFromFilePath(String isoPath) {
        String fileName = getFileNameFromPath(isoPath);
        int idx = fileName.indexOf("update");
        if (idx != -1) {
            String result = fileName.substring(idx).split("-")[0];
            return shortenUpdateLabel(result);
        } else {
            return "";
        }
    }

    /**
     * Get file name from file path.
     * Example: /tmp/dir1/my_file => my_file
     *
     * @param pathToFile
     * @return
     */
    private String getFileNameFromPath(String pathToFile) {
        String[] tokens = pathToFile.split("/");
        String fileName = tokens[tokens.length - 1]; // get last element
        return fileName;
    }

    /**
     * Translate update01 to u1, update2 to u2.
     *
     * @param update
     * @return
     */
    private String shortenUpdateLabel(String update) {
        return "u" + Integer.parseInt(update.substring(6));
    }

    private ComputeImage getOsMetadata(ImageServerDialog d, String isoPath, String isoMountDir) throws InternalException {
        ComputeImage metadata = new ComputeImage();

        // is it ESXi 5x
        if (d.fileExists(isoMountDir + "upgrade/metadata.xml") && d.fileExists(isoMountDir + "upgrade/profile.xml")) {
            metadata = new ComputeImage();

            d.cd(isoMountDir);
            String cmd = String.format(ImageServerDialogProperties.getString("cmd.grepXmlValue"), "esxVersion",
                    "esxVersion", "upgrade/metadata.xml");
            String esxVersion = d.execCommand(cmd);

            cmd = String.format(ImageServerDialogProperties.getString("cmd.grepXmlValue"), "build",
                    "build", "upgrade/metadata.xml");
            String build = d.execCommand(cmd);

            metadata.setOsVersion(esxVersion);
            metadata.setOsBuild(build);
            metadata.setOsName("esxi");
            metadata.setOsArchitecture("x86_64");
            metadata.setOsUpdate(extractUpdateFromFilePath(isoPath));
            metadata.setImageType(ComputeImage.ImageType.esx.name());

            // figure out custom
            cmd = String.format(ImageServerDialogProperties.getString("cmd.grepXmlValue"), "name",
                    "name", "upgrade/profile.xml");
            String profileName = d.execCommand(cmd);
            if (!profileName.endsWith("-standard")) {
                metadata.setCustomName(profileName.replaceAll(" ", "_"));
            }
            d.cd(TMP);
        } else {
            throw ImageServerControllerException.exceptions.unknownOperatingSystem();
        }
        return metadata;
    }

    private void isSupportedImage(ComputeImage os) throws ImageServerControllerException {
        if ("esxi".equals(os.getOsName())
                && os.getOsVersion() != null && os.getOsVersion().startsWith("5.")
                && os.getOsBuild() != null
                && os.getOsArchitecture() != null && os.getOsArchitecture().equals("x86_64")) {
            log.info(String.format("metadata: %s %s %s %s %s %s",
                    os.getOsName(), os.getOsVersion(), os.getOsUpdate(),
                    os.getOsBuild(), os.getOsArchitecture(), os.getCustomName()));
        }
        else {
            throw ImageServerControllerException.exceptions.unsupportedImageVersion(String.format("metadata: %s %s %s %s %s %s",
                    os.getOsName(), os.getOsVersion(), os.getOsUpdate(),
                    os.getOsBuild(), os.getOsArchitecture(), os.getCustomName()));
        }
    }

    private void cleanupTemp(ImageServerDialog d, String dir, String iso) {
        try {
            d.umount(dir);
            d.rm(dir);
            d.rm(iso);
        } catch (Exception ignore) {
            log.warn(ignore.getMessage(), ignore);
        }
    }

    public void importImageMethod(URI ciId, URI imageServerId, String stepId) {
        log.info("importImageMethod {}", ciId);
        ImageServerDialog d = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class,imageServerId);

            ComputeImage ci = dbClient.queryObject(ComputeImage.class, ciId);
            importImage(imageServer, ci, d);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception importing image: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception importing image: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.IMPORT_IMAGE.getName();
            WorkflowStepCompleter.stepFailed(stepId, ImageServerControllerException.exceptions.unexpectedException(opName, e));
        } finally {
            try {
                if (d != null && d.isConnected()) {
                    d.close();
                }
            } catch (Exception e) {
                log.error("failed to close image server dialog", e);
            }
        }
    }

    /**
     * Utility method to import an image to the given computeimage server
     * @param imageServer {@link ComputeImageServer} instance.
     * @param ci {@link ComputeImage} instance
     * @param imageserverDialog {@link ImageServerDialog} instance
     * @throws Exception
     */
    private void importImage(ComputeImageServer imageServer, ComputeImage ci, ImageServerDialog imageserverDialog) throws Exception
    {
        sanitizeUrl(ci.getImageUrl());

        String ts = String.valueOf(System.currentTimeMillis());
        String[] tokens = ci.getImageUrl().split("/");
        String imageName = tokens[tokens.length - 1];
        String imagePath = TMP + "/" + imageName;
        String tempDir = TMP + "/os" + ts + "/";

        SSHSession session = new SSHSession();
        session.connect(imageServer.getImageServerIp(), imageServer.getSshPort(),
                imageServer.getImageServerUser(), imageServer.getImageServerPassword());
        imageserverDialog = new ImageServerDialog(session, imageServer.getSshTimeoutMs());
        imageserverDialog.init();
        log.info("connected to image server");

        log.info("cd to {}", TMP);
        imageserverDialog.cd(TMP);

        log.info("download image");
        // CTRL-12030: special characters in URL's password cause issues on Image Server. Adding quotes.
        boolean res = imageserverDialog.wget("'" + ci.getImageUrl() + "'", imageName, imageServer.getImageImportTimeoutMs());

        if (res) {
            log.info("downloaded image successfully");
        } else {
            throw ImageServerControllerException.exceptions.fileDownloadFailed(ci.getImageUrl());
        }

        log.info("create temp dir {}", tempDir);
        imageserverDialog.mkdir(tempDir);

        log.info("mount image onto temp dir");
        imageserverDialog.mount(imageName, tempDir);

        log.info("Analyze metadata");
        ComputeImage osMetadata = getOsMetadata(imageserverDialog, imagePath, tempDir);

        isSupportedImage(osMetadata);

        // make sure it is not already loaded
        List<URI> ids = dbClient.queryByType(ComputeImage.class, true);
        Iterator<ComputeImage> iter = dbClient.queryIterativeObjects(ComputeImage.class, ids);
        while (iter.hasNext()) {
            ComputeImage existingImage = iter.next();
            if (osMetadata.fullName().equals(existingImage.getImageName())) {
                log.error("This image is already imported, id: {}", existingImage.getId());
                cleanupTemp(imageserverDialog, tempDir, imagePath);
                throw ImageServerControllerException.exceptions.duplicateImage(osMetadata.fullName());
            }
        }
        log.info("Compute image '" + osMetadata.fullName() + "' will be loaded.");

        // copy OS into TFTP boot directory
        String targetDir = imageServer.getTftpbootDir() + imageServer.getImageDir() + osMetadata.fullName();

        imageserverDialog.rm(targetDir);

        log.info("Saving image into target directory " + targetDir);
        imageserverDialog.cpDir(tempDir, targetDir);
        log.info("Saved");

        log.info("Change target directory permissions to 755");
        imageserverDialog.chmodDir("755", targetDir);

        // save in DB
        ci.setOsName(osMetadata.getOsName());
        ci.setOsVersion(osMetadata.getOsVersion());
        ci.setOsUpdate(osMetadata.getOsUpdate());
        ci.setOsBuild(osMetadata.getOsBuild());
        ci.setOsArchitecture(osMetadata.getOsArchitecture());
        ci.setCustomName(osMetadata.getCustomName());
        ci.setPathToDirectory(imageServer.getImageDir() + osMetadata.fullName() + "/");
        ci.setImageName(osMetadata.fullName());
        ci.setImageType(osMetadata.getImageType());

        dbClient.persistObject(ci);
        //update the imageServer with the successfully updated image.
        StringSet imagesSet = imageServer.getComputeImage();
        if(imagesSet == null){
            imageServer.setComputeImage(new StringSet());
        }
        imagesSet.add(ci.getId().toString());
        imageServer.setComputeImage(imagesSet);
        dbClient.persistObject(imageServer);
        // clean up
        cleanupTemp(imageserverDialog, tempDir, imagePath);
    }

    @Override
    public void deleteImage(AsyncTask task,URI imageServerId) throws InternalException {
        log.info("deleteImage " + task._id);

        URI ciId = task._id;
        ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, imageServerId);
        TaskCompleter completer = null;
        try {
            completer = new ComputeImageCompleter(ciId, task._opId, OperationTypeEnum.DELETE_COMPUTE_IMAGE, EVENT_SERVICE_TYPE);
            boolean imageServerVerified = verifyImageServer(imageServer);

            if (!imageServerVerified) {
            	throw ImageServerControllerException.exceptions.imageServerNotSetup("Can't delete image: " + imageServerErrorMsg);
            }

            Workflow workflow = workflowService.getNewWorkflow(this, DELETE_IMAGE_WF, true, task._opId);
            workflow.createStep(DELETE_IMAGE_STEP,
                    String.format("removing image %s", ciId), null,
                    ciId, ciId.toString(),
                    this.getClass(),
                    new Workflow.Method("deleteImageMethod", ciId,imageServer.getId()),
                    null,
                    null);
            workflow.executePlan(completer, "Success");
        } catch (Exception e) {
            log.error("deleteImage caught an exception.", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(dbClient, serviceError);
        }
    }

    public void deleteImageMethod(URI ciId, URI imageServerId, String stepId) {
        log.info("deleteImageMethod {}", ciId);
        ImageServerDialog d = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, imageServerId);

            ComputeImage ci = dbClient.queryObject(ComputeImage.class, ciId);

            SSHSession session = new SSHSession();
            session.connect(imageServer.getImageServerIp(), imageServer.getSshPort(),
                    imageServer.getImageServerUser(), imageServer.getImageServerPassword());
            d = new ImageServerDialog(session, imageServer.getSshTimeoutMs());
            d.init();
            log.info("connected to image server");

            log.info("calling image server to delete image");
            d.rm(imageServer.getTftpbootDir() + ci.getPathToDirectory());
            log.info("delete done");

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception deleting image: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception deleting image: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.REMOVE_IMAGE.getName();
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions.unexpectedException(opName, e));
        } finally {
            try {
                if (d != null && d.isConnected()) {
                    d.close();
                }
            } catch (Exception e) {
                log.error("failed to close image server dialog", e);
            }
        }
    }

    @Override
    public void installOperatingSystem(AsyncTask task, URI computeImageJob)
            throws InternalException {
        log.info("installOperatingSystem");

        Host host = dbClient.queryObject(Host.class, task._id);
        ComputeElement ce = dbClient.queryObject(ComputeElement.class, host.getComputeElement());
        ComputeSystem cs = dbClient.queryObject(ComputeSystem.class, ce.getComputeSystem());

        ComputeImageJob job = dbClient.queryObject(ComputeImageJob.class, computeImageJob);
        ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class,job.getComputeImageServerId());
        ComputeImage img = dbClient.queryObject(ComputeImage.class, job.getComputeImageId());

        TaskCompleter completer = null;
        try {
            completer = new OsInstallCompleter(host.getId(), task._opId, job.getId(), EVENT_SERVICE_TYPE);
            boolean imageServerVerified = verifyImageServer(imageServer);
            if (!imageServerVerified) {
                throw ImageServerControllerException.exceptions.imageServerNotSetup("Can't install operating system: "
                            + imageServerErrorMsg);
            }

            Workflow workflow = workflowService.getNewWorkflow(this, OS_INSTALL_WF, true, task._opId);

            String waitFor = null;
            waitFor = workflow.createStep(OS_INSTALL_IMAGE_SERVER_CHECK_STEP,
                    "image server check pre os install", waitFor,
                    img.getId(), img.getImageType(),
                    this.getClass(), new Workflow.Method("preOsInstallImageServerCheck", job.getId()),
                    new Workflow.Method(ROLLBACK_NOTHING_METHOD), null);

            waitFor = workflow.createStep(OS_INSTALL_PREPARE_PXE_STEP,
                    "prepare pxe boot", waitFor,
                    img.getId(), img.getImageType(),
                    this.getClass(), new Workflow.Method("preparePxeBootMethod", job.getId()),
                    new Workflow.Method(ROLLBACK_NOTHING_METHOD), null);

            String prepStepId = workflow.createStepId();

            waitFor = computeDeviceController.addStepsPreOsInstall(workflow, waitFor, cs.getId(), host.getId(), prepStepId);

            waitFor = workflow.createStep(OS_INSTALL_WAIT_FOR_FINISH_STEP,
                    "wait for os install to finish", waitFor,
                    img.getId(), img.getImageType(),
                    this.getClass(), new Workflow.Method("waitForFinishMethod", job.getId()),
                    new Workflow.Method(ROLLBACK_NOTHING_METHOD), null);

            waitFor = computeDeviceController.addStepsPostOsInstall(workflow, waitFor, cs.getId(), ce.getId(),
                    host.getId(), prepStepId, job.getVolumeId());

            workflow.executePlan(completer, "Success");
        } catch (Exception e) {
            log.error("installOperatingSystem caught an exception.", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            completer.error(dbClient, serviceError);
        }
    }

    public void preOsInstallImageServerCheck(URI jobId, String stepId) {
        log.info("preOsInstallImageServerCheck {} ", jobId);
        ImageServerDialog d = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            ComputeImageJob job = dbClient.queryObject(ComputeImageJob.class, jobId);
            ComputeImage img = dbClient.queryObject(ComputeImage.class, job.getComputeImageId());
            ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, job.getComputeImageServerId());

            SSHSession session = new SSHSession();
            session.connect(imageServer.getImageServerIp(), imageServer.getSshPort(),
                    imageServer.getImageServerUser(), imageServer.getImageServerPassword());
            d = new ImageServerDialog(session, imageServer.getSshTimeoutMs());
            d.init();
            log.info("connected to image server");

            log.info("verify the image is still there");
            if (!d.directoryExists(imageServer.getTftpbootDir() + img.getPathToDirectory())) {
                log.error("the image is missing");
                throw ImageServerControllerException.exceptions.computeImageIsMissing(img.getPathToDirectory());
            }

            String pid = d.getServerPid("67");
            if (pid == null) {
                // dhcp down
                throw ImageServerControllerException.exceptions.dhcpServerNotRunning();
            }

            pid = d.getServerPid("69");
            if (pid == null) {
                // tftp down
                throw ImageServerControllerException.exceptions.tftpServerNotRunning();
            }

            log.info("make sure the python server is running");
            pid = d.getServerPid(imageServer.getImageServerHttpPort());
            if (pid == null) {
                log.warn("python server is not running, attempt to start it");
                d.cd(imageServer.getTftpbootDir() + HTTP_DIR);
                d.nohup(String.format("python %s", SERVER_PY_FILE));
                pid = d.getServerPid(imageServer.getImageServerHttpPort());
                if (pid == null) {
                    throw ImageServerControllerException.exceptions.httpPythonServerNotRunning();
                }
            }

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception during image server check pre os install: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception during image server check pre os install: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions.unexpectedException(opName, e));
        } finally {
            try {
                if (d != null && d.isConnected()) {
                    d.close();
                }
            } catch (Exception e) {
                log.error("failed to close image server dialog", e);
            }
        }
    }

    public void preparePxeBootMethod(URI jobId, String stepId) {
        log.info("preparePxeBootMethod {} ", jobId);
        ImageServerDialog d = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            ComputeImageJob job = dbClient.queryObject(ComputeImageJob.class, jobId);
            ComputeImage img = dbClient.queryObject(ComputeImage.class, job.getComputeImageId());
            ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, job.getComputeImageServerId());

            SSHSession session = new SSHSession();
            session.connect(imageServer.getImageServerIp(), imageServer.getSshPort(),
                    imageServer.getImageServerUser(), imageServer.getImageServerPassword());
            d = new ImageServerDialog(session, imageServer.getSshTimeoutMs());
            d.init();
            log.info("connected to image server");

            log.info("putting pxe conf file");
            pxeIntegrationService.createSession(d, job, img, imageServer);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception preparing pxe boot: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception preparing pxe boot: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions.unexpectedException(opName, e));
        } finally {
            try {
                if (d != null && d.isConnected()) {
                    d.close();
                }
            } catch (Exception e) {
                log.error("failed to close image server dialog", e);
            }
        }
    }

    public void waitForFinishMethod(URI jobId, String stepId) {
        log.info("waitForFinishMethod {} ", jobId);

        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            ComputeImageJob job = dbClient.queryObject(ComputeImageJob.class, jobId);
            ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, job.getComputeImageServerId());

            if (job.getJobStartTime() == null) {
                log.info("starting the job");
                job.setJobStartTime(System.currentTimeMillis());
                dbClient.persistObject(job);
            }
            else {
                log.info("resuming the job");
            }

            OsInstallStatus status = null;

            while (System.currentTimeMillis() - job.getJobStartTime() < imageServer.getOsInstallTimeoutMs()
                    && status == null) {
                try {
                    log.info("sleep for {} ms", imageServer.getJobPollingIntervalMs());
                    Thread.sleep(imageServer.getJobPollingIntervalMs());
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }

                log.info("check status for {}, after {} sec", job.getPxeBootIdentifier(),
                        (System.currentTimeMillis() - job.getJobStartTime()) / 1000);
                status = osInstallStatusPoller.getOsInstallStatus(job.getPxeBootIdentifier());
            }

            if (status != null) { // it is success or failure - do clean up
                ImageServerDialog d = null;
                try {
                    SSHSession session = new SSHSession();
                    session.connect(imageServer.getImageServerIp(), imageServer.getSshPort(),
                            imageServer.getImageServerUser(), imageServer.getImageServerPassword());
                    d = new ImageServerDialog(session, imageServer.getSshTimeoutMs());
                    d.init();
                    d.rm(imageServer.getTftpbootDir() + HTTP_SUCCESS_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpbootDir() + HTTP_FAILURE_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpbootDir() + HTTP_KICKSTART_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpbootDir() + HTTP_FIRSTBOOT_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpbootDir() + PXELINUX_CFG_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpbootDir() + PXELINUX_CFG_DIR + job.getPxeBootIdentifier() + ".boot.cfg");
                } catch (Exception e) {
                    log.error("exception when trying to poll for status", e);
                } finally {
                    try {
                        if (d != null && d.isConnected()) {
                            d.close();
                        }
                    } catch (Exception e) {
                        log.error("failed to close image server dialog", e);
                    }
                }
            }

            log.info("job status: {}", status);

            if (status == OsInstallStatus.SUCCESS) {
                log.info("session {} - marking job as SUCCESS", job.getPxeBootIdentifier());
                job.setJobStatus(JobStatus.SUCCESS.name());
                dbClient.persistObject(job);
                WorkflowStepCompleter.stepSucceded(stepId);
            } else if (status == OsInstallStatus.FAILURE) {
                log.info("session {} - marking job as FAILED", job.getPxeBootIdentifier());
                job.setJobStatus(JobStatus.FAILED.name());
                dbClient.persistObject(job);
                WorkflowStepCompleter.stepFailed(stepId,
                        ImageServerControllerException.exceptions.osInstallationFailed("failure in the post-install"));
            } else { // timed out
                log.info("session {} - marking job as TIMEDOUT", job.getPxeBootIdentifier());
                job.setJobStatus(JobStatus.TIMEDOUT.name());
                dbClient.persistObject(job);
                WorkflowStepCompleter.stepFailed(stepId,
                        ImageServerControllerException.exceptions.osInstallationTimedOut(imageServer.getOsInstallTimeoutMs() / 1000));
            }

        } catch (InternalException e) {
            log.error("Exception waiting for finish: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception waiting for finish: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM.getName();
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions.unexpectedException(opName, e));
        }
    }

    /**
     * This is needed if any of the workflow steps have a real rollback method.
     *
     * @param stepId
     */
    public void rollbackNothingMethod(String stepId) {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    /**
     * Method to verify and impport images on the imageserver
     *
     * @param task {@link AsyncTask} instance
     *
     */
    @Override
    public void verifyImageServerAndImportImages(AsyncTask task) {
        TaskCompleter completer = null;
        try{
        URI computeImageServerID = task._id;
        completer = new ComputeImageServerCompleter(computeImageServerID, task._opId, OperationTypeEnum.CREATE_COMPUTE_IMAGESERVER, EVENT_SERVICE_TYPE);

        Workflow workflow = workflowService.getNewWorkflow(this, "CreateImageServer_WF", true, task._opId);
        workflow.createStep("CreateImageServer_Step",
                String.format("Verfiying ImageServer %s", computeImageServerID), null,
                computeImageServerID, computeImageServerID.toString(),
                this.getClass(),
                new Workflow.Method("verifyComputeImageServer", computeImageServerID),
                null,
                null);
        workflow.createStep("ImportImagesToServer_Step",
                String.format("Importing images for %s", computeImageServerID), "CreateImageServer_Step",
                computeImageServerID, computeImageServerID.toString(),
                this.getClass(),
                new Workflow.Method("importImagesToServer", computeImageServerID),
                null,
                null);
        workflow.executePlan(completer, "Success");
        }catch(Exception ex)
        {
            log.error("Unexpected exception waiting for finish: " + ex.getMessage(), ex);
            //TODO: Need to decide if we need to throw a ServiceError.
        }
    }

    /**
     * This method imports all exisiting images to the given Image server
     * @param imageServerID {@link URI} imageServer URI
     * @param stepId workflow step id
     */
    public void importImagesToServer(URI imageServerID, String stepId) {
        WorkflowStepCompleter.stepExecuting(stepId);
        log.info("Executing method importImagesToServer");
        ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, imageServerID);

        List<URI> imageURIList = dbClient.queryByType(ComputeImage.class, true);
        if (imageURIList == null || !imageURIList.iterator().hasNext()) {
            log.info("There are no images to be imported.");
        }else {
            List<ComputeImage> imageList = dbClient.queryObject(ComputeImage.class, imageURIList);
            if (CollectionUtils.isEmpty(imageList)) {
                log.error("Could not find the ComputeImage's for the Ids {}", imageList.toString());
            } else {
                String taskId = UUID.randomUUID().toString();
                Operation op = new Operation();
                op.setResourceType(ResourceOperationTypeEnum.IMPORT_IMAGE);
                dbClient.createTaskOpStatus(ComputeImageServer.class, imageServerID, taskId, op);
                AsyncTask subTask = new AsyncTask(ComputeImageServer.class, imageServerID, taskId);
                TaskCompleter completer = new ComputeImageServerCompleter(imageServerID, subTask._opId, OperationTypeEnum.CREATE_COMPUTE_IMAGE, EVENT_SERVICE_TYPE);
                Workflow workflow = workflowService.getNewWorkflow(this, "ImportImages_WF", true, subTask._opId);
                for (ComputeImage computeImage : imageList) {
                    if(computeImage.getComputeImageStatus().equals("AVAILABLE")) {
                        StringBuilder msg = new StringBuilder("Importing image ");
                        msg.append(computeImage.getLabel()).append(" on to imageServer - ");
                        msg.append(imageServer.getImageServerIp()).append(".");
                        workflow.createStep("ImportImage_Step", msg.toString(), null,
                                imageServerID, imageServerID.toString(), this
                                        .getClass(), new Workflow.Method(
                                        "performImportImage", imageServer,
                                        computeImage), null, null);
                    }
                }
                workflow.executePlan(completer, "Success");
            }
        }
        WorkflowStepCompleter.stepSucceded(stepId);
        log.info("exiting method importImagesToServer");
    }

    /**
     * Method to perform an image import to the specified imagegserver
     * @param imageServer {@link ComputeImageServer} instance
     * @param image {@link ComputeImage} instance
     * @param stepId workflow stepid being executed.
     */
    public void performImportImage(ComputeImageServer imageServer, ComputeImage image, String stepId)
    {
        WorkflowStepCompleter.stepExecuting(stepId);
        log.info("Executing method performImportImage");
        ImageServerDialog d = null;
        try {
            importImage(imageServer, image, d);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception importing image: " + e.getMessage(), e);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error("Unexpected exception importing image: " + e.getMessage(), e);
            String opName = ResourceOperationTypeEnum.IMPORT_IMAGE.getName();
            WorkflowStepCompleter.stepFailed(stepId, ImageServerControllerException.exceptions.unexpectedException(opName, e));
        } finally {
            try {
                if (d != null && d.isConnected()) {
                    d.close();
                }
            } catch (Exception e) {
                log.error("failed to close image server dialog", e);
            }
        }
    }

    /**
     * This method verifies if the given image Server is a valid imageServer.
     * @param imageServerId {@link URI} of ComputeImageServer
     * @param stepId workflow stepid being executed.
     */
    public void verifyComputeImageServer(URI imageServerId, String stepId) {
        log.info("entering method verifyComputeImageServer");
        WorkflowStepCompleter.stepExecuting(stepId);

        ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, imageServerId);

        if(verifyImageServer(imageServer))
        {
            WorkflowStepCompleter.stepSucceded(stepId);
        }else
        {
            log.error("Unable to verify imageserver");
            WorkflowStepCompleter
                    .stepFailed(
                            stepId,
                            ImageServerControllerException.exceptions
                                    .unexpectedException(
                                            OperationTypeEnum.CREATE_COMPUTE_IMAGESERVER.name(),
                                            new Exception(
                                                    "Unable to verify imageserver")));
        }
    }
}
