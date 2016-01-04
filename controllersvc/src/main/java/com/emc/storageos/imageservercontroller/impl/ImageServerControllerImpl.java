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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.computecontroller.impl.ComputeDeviceController;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImage.ComputeImageStatus;
import com.emc.storageos.db.client.model.ComputeImageJob;
import com.emc.storageos.db.client.model.ComputeImageJob.JobStatus;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.Host;
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

    private static final String IMPORT_IMAGE_WF = "IMPORT_IMAGE_WORKFLOW";
    private static final String IMPORT_IMAGE_TO_SERVER_STEP = "IMPORT_IMAGE_TO_SERVER_STEP";

    private static final String DELETE_IMAGE_WF = "DELETE_IMAGE_WORKFLOW";
    private static final String DELETE_IMAGE_STEP = "DELETE_IMAGE_STEP";

    private static final String OS_INSTALL_WF = "OS_INSTALL_WORKFLOW";
    private static final String OS_INSTALL_IMAGE_SERVER_CHECK_STEP = "OS_INSTALL_IMAGE_SERVER_CHECK_STEP";
    private static final String OS_INSTALL_PREPARE_PXE_STEP = "OS_INSTALL_PREPARE_PXE_STEP";
    private static final String OS_INSTALL_WAIT_FOR_FINISH_STEP = "OS_INSTALL_WAIT_FOR_FINISH_STEP";

    private static final String ROLLBACK_NOTHING_METHOD = "rollbackNothingMethod";

    private static final String TMP = "/tmp";

    private static final String IMAGESERVER_VERIFY_IMPORT_IMAGE_WF = "IMAGESERVER_VERIFY_IMPORT_IMAGE_WF";

    private static final String IMAGESERVER_VERIFICATION_STEP = "IMAGESERVER_VERIFICATION_STEP";

    private static final String IMAGESERVER_IMPORT_IMAGES_STEP = "IMAGESERVER_IMPORT_IMAGES_STEP";

    private static final Logger log = LoggerFactory.getLogger(ImageServerControllerImpl.class);

    private static final String FAILED_TO_CLOSE_STR = "failed to close image server dialog";

    private static final String SUCCESS = "Success";

    private DbClient dbClient;

    private WorkflowService workflowService;

    private ComputeDeviceController computeDeviceController;

    private PxeIntegrationService pxeIntegrationService;

    private OsInstallStatusPoller osInstallStatusPoller;

    private String imageServerErrorMsg = null;
    private static final int IMAGE_SERVER_VERSION = 100;

    @Autowired
    private AuditLogManager _auditMgr;

    private CoordinatorClient _coordinator;

    private static final String IMAGEURL_PASSWORD_SPLIT_REGEX = "(.*?:){2}((?<=\\:).*(?=\\@))";

    private static final String IMAGEURL_HOST_REGEX = "^*(?<=@)([^/@]++)/.*+$";
    public static final String MASKED_PASSWORD = "*********";


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

    /**
     * setter for coordinator client
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Check if the given {@link ComputeImageServer} instance has valid details
     *
     * @param imageServer {@link ComputeImageServer} instance
     * @return true if valid else false
     */
    private boolean isImageServerValid(ComputeImageServer imageServer) {
        boolean valid = false;
        // make sure all required fields are set
        if (!StringUtils.isBlank(imageServer.getImageServerIp()) && !StringUtils.isBlank(imageServer.getImageServerUser())
                && !StringUtils.isBlank(imageServer.getTftpBootDir()) && !StringUtils.isBlank(imageServer.getImageServerSecondIp())
                && !StringUtils.isBlank(imageServer.getImageServerHttpPort())
                && !StringUtils.isBlank(imageServer.getImageServerPassword())) {
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

            if (!d.directoryExists(imageServer.getTftpBootDir())) {
                throw ImageServerControllerException.exceptions.imageServerNotSetup("tftpboot directory does not exist");
            }

            if (!d.fileExists(imageServer.getTftpBootDir() + PXELINUX_0_FILE)) {
                throw ImageServerControllerException.exceptions.imageServerNotSetup("pxelinux.0 binary does not exist");
            }

            if (!d.fileExists("/usr/bin/python")) {
                throw ImageServerControllerException.exceptions.imageServerNotSetup("python not found");
            }

            // check is.properties file if upgrade is needed
            // perform upgrade if file is not there, or version property is not
            // found or not valid or less then IMAGE_SERVER_VERSION
            boolean upgradeRequired = false;
            if (!d.fileExists(imageServer.getTftpBootDir() + "is.properties")) {
                upgradeRequired = true;
            } else {
                String s = d.readFile(imageServer.getTftpBootDir() + "is.properties");
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

            if (!d.directoryExists(imageServer.getTftpBootDir() + PXELINUX_CFG_DIR)) {
                log.info("pxelinux.cfg does not exist, will create it");
                d.mkdir(imageServer.getTftpBootDir() + PXELINUX_CFG_DIR);
            }

            if (!StringUtils.isBlank(imageServer.getImageDir())
                    && !d.directoryExists(imageServer.getTftpBootDir()
                            + imageServer.getImageDir())) {
                log.info("image directory does not exist, will create it");
                d.mkdir(imageServer.getTftpBootDir()
                        + imageServer.getImageDir());
            }

            if (upgradeRequired || !d.fileExists(imageServer.getTftpBootDir() + PXELINUX_CFG_DIR + DEFAULT_FILE)) {
                log.info("creating pxelinux.cfg/default");
                String content = ImageServerUtils.getResourceAsString("imageserver/default");
                d.writeFile(imageServer.getTftpBootDir() + PXELINUX_CFG_DIR + DEFAULT_FILE, content);
            }

            if (!d.directoryExists(imageServer.getTftpBootDir() + HTTP_DIR)) {
                log.info("http does not exist, will create it");
                d.mkdir(imageServer.getTftpBootDir() + HTTP_DIR);
            }

            if (upgradeRequired || !d.fileExists(imageServer.getTftpBootDir() + HTTP_DIR + SERVER_PY_FILE)) {
                log.info("creating server.py");
                String content = ImageServerUtils.getResourceAsString("imageserver/server.py");
                StringBuilder script = new StringBuilder(content);
                ImageServerUtils.replaceAll(script, "{http.port}", imageServer.getImageServerHttpPort());
                d.writeFile(imageServer.getTftpBootDir() + HTTP_DIR + SERVER_PY_FILE, script.toString());
                d.chmodFile("744", imageServer.getTftpBootDir() + HTTP_DIR + SERVER_PY_FILE);
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
                d.cd(imageServer.getTftpBootDir() + HTTP_DIR);
                d.nohup(String.format("python %s", SERVER_PY_FILE));
            }

            if (upgradeRequired || !d.fileExists(imageServer.getTftpBootDir() + HTTP_DIR + WGET_FILE)) {
                log.info("creating wget wrapper script");
                String content = ImageServerUtils.getResourceAsString("imageserver/wget");
                d.writeFile(imageServer.getTftpBootDir() + HTTP_DIR + WGET_FILE, content);
            }

            if (!d.directoryExists(imageServer.getTftpBootDir() + HTTP_KICKSTART_DIR)) {
                log.info("http/ks does not exist, will create it");
                d.mkdir(imageServer.getTftpBootDir() + HTTP_KICKSTART_DIR);
            }

            if (!d.directoryExists(imageServer.getTftpBootDir() + HTTP_FIRSTBOOT_DIR)) {
                log.info("http/fb does not exist, will create it");
                d.mkdir(imageServer.getTftpBootDir() + HTTP_FIRSTBOOT_DIR);
            }

            if (!d.directoryExists(imageServer.getTftpBootDir() + HTTP_SUCCESS_DIR)) {
                log.info("http/success does not exist, will create it");
                d.mkdir(imageServer.getTftpBootDir() + HTTP_SUCCESS_DIR);
            }

            if (!d.directoryExists(imageServer.getTftpBootDir() + HTTP_FAILURE_DIR)) {
                log.info("http/failure does not exist, will create it");
                d.mkdir(imageServer.getTftpBootDir() + HTTP_FAILURE_DIR);
            }

            // save is.properties
            if (upgradeRequired) {
                log.info("saving is.properties");
                d.writeFile(imageServer.getTftpBootDir() + "is.properties", "version=" + IMAGE_SERVER_VERSION
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

    /**
     * Import image to all availabe imageServer
     *
     * @param task {@link AsyncTask} instance
     */
    @Override
    public void importImageToServers(AsyncTask task) throws InternalException {
        log.info("importImage");
        URI ciId = task._id;
        boolean wfHasSteps = false;
        Workflow workflow = workflowService.getNewWorkflow(this,
                IMPORT_IMAGE_WF, true, task._opId);
        TaskCompleter completer = new ComputeImageCompleter(ciId, task._opId,
                OperationTypeEnum.CREATE_COMPUTE_IMAGE, EVENT_SERVICE_TYPE);
        try {
            List<URI> ids = dbClient
                    .queryByType(ComputeImageServer.class, true);
            for (URI imageServerId : ids) {
                log.info("import to server:" + imageServerId.toString());
                ComputeImageServer imageServer = dbClient.queryObject(
                        ComputeImageServer.class, imageServerId);
                if (imageServer.getComputeImages() == null
                        || !imageServer.getComputeImages().contains(
                                ciId.toString())) {
                    log.info("verify Image Server");
                    String verifyServerStepId = workflow.createStep(IMAGESERVER_VERIFICATION_STEP, String.format(
                            "Verifying ImageServer %s", imageServerId), null,
                            imageServerId, imageServerId.toString(), this
                                    .getClass(),
                            new Workflow.Method(
                                    "verifyComputeImageServer", imageServerId),
                            null, null);

                    workflow.createStep(IMPORT_IMAGE_TO_SERVER_STEP, String
                            .format("Importing image for %s", imageServerId),
                            verifyServerStepId, imageServerId, imageServerId.toString(), this
                                    .getClass(),
                            new Workflow.Method(
                                    "importImageMethod", ciId, imageServer, null),
                            null, null);
                    wfHasSteps = true;
                }
            }
            if (wfHasSteps) {
                workflow.executePlan(completer, SUCCESS);
            }
        } catch (Exception e) {
            log.error("importImage caught an exception.", e);
            ServiceError serviceError = DeviceControllerException.errors
                    .jobFailed(e);
            completer.error(dbClient, serviceError);
        }
    }

    private String sanitizeUrl(String url) {
        try {
            URL imageUrl = new URL(url); // NOSONAR
                                         // ("We are using this line to verify valid URL")
            String filepart = imageUrl.getFile();
            String[] tempArr = StringUtils.split(filepart, "/");
            StringBuilder strBuilder = new StringBuilder();
            for (String string : tempArr) {
                strBuilder.append("/").append(
                        URLEncoder.encode(string, "UTF-8"));
            }

            String newURL = StringUtils.replace(url, filepart,
                    strBuilder.toString());
            new URL(newURL);
            return newURL;
        } catch (MalformedURLException e) {
            throw ImageServerControllerException.exceptions
                    .urlSanitationFailure(url, e);
        } catch (UnsupportedEncodingException e) {
            throw ImageServerControllerException.exceptions
                    .urlSanitationFailure(url, e);
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

    private void isSupportedImage(ComputeImage os)
            throws ImageServerControllerException {
        if (checkOSVersion(os) && checkOSBuildArchitecture(os)) {
            log.info(String.format("metadata: %s %s %s %s %s %s",
                    os.getOsName(), os.getOsVersion(), os.getOsUpdate(),
                    os.getOsBuild(), os.getOsArchitecture(), os.getCustomName()));
        } else {
            throw ImageServerControllerException.exceptions
                    .unsupportedImageVersion(String.format(
                            "metadata: %s %s %s %s %s %s", os.getOsName(),
                            os.getOsVersion(), os.getOsUpdate(),
                            os.getOsBuild(), os.getOsArchitecture(),
                            os.getCustomName()));
        }
    }

    /**
     * check OS version
     *
     * @param os {@link ComputeIamge} instance
     * @return
     */
    private boolean checkOSVersion(ComputeImage os) {
        return "esxi".equals(os.getOsName()) && os.getOsVersion() != null
                && (os.getOsVersion().startsWith("5.") || os.getOsVersion().startsWith("6."));
    }

    /**
     * check OS build and architecture type
     *
     * @param os {@link ComputeIamge} instance
     * @return
     */
    private boolean checkOSBuildArchitecture(ComputeImage os) {
        return os.getOsBuild() != null
                && os.getOsArchitecture() != null && os.getOsArchitecture().equals("x86_64");
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

    /**
     * Method to import an image
     *
     * @param ciId {@link URI} computeImage URI
     * @param imageServer {@link ComputeImageServer} imageServer instance
     * @param opName operation Name
     * @param stepId {@link String} step Id
     */
    public void importImageMethod(URI ciId, ComputeImageServer imageServer,
            String opName, String stepId) {
        log.info("importImageMethod importing image {} on to imageServer {}",
                ciId, imageServer.getId());
        ImageServerDialog d = null;
        ComputeImage ci = null;
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            ci = dbClient.queryObject(ComputeImage.class, ciId);
            SSHSession session = new SSHSession();
            session.connect(imageServer.getImageServerIp(), imageServer.getSshPort(),
                    imageServer.getImageServerUser(), imageServer.getImageServerPassword());
            d = new ImageServerDialog(session, imageServer.getSshTimeoutMs());
            importImage(imageServer, ci, d);

            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (InternalException e) {
            log.error("Exception importing image: " + e.getMessage(), e);
            updateFailedImages(imageServer.getId(), ci);
            WorkflowStepCompleter.stepFailed(stepId, e);
        } catch (Exception e) {
            log.error(
                    "Unexpected exception importing image: " + e.getMessage(),
                    e);
            String operationName = opName;
            if (null == operationName) {
                operationName = ResourceOperationTypeEnum.IMPORT_IMAGE
                        .getName();
            }
            updateFailedImages(imageServer.getId(), ci);
            WorkflowStepCompleter.stepFailed(stepId,
                    ImageServerControllerException.exceptions
                            .unexpectedException(operationName, e));
        } finally {
            try {
                if (d != null && d.isConnected()) {
                    d.close();
                }
            } catch (Exception e) {
                log.error(FAILED_TO_CLOSE_STR, e);
            }
        }
    }

    /**
     * Utility method to import an image to the given computeimage server
     *
     * @param imageServer {@link ComputeImageServer} instance.
     * @param ci {@link ComputeImage} instance
     * @param imageserverDialog {@link ImageServerDialog} instance
     */
    private void importImage(ComputeImageServer imageServer, ComputeImage ci,
            ImageServerDialog imageserverDialog) {
        log.info("Importing image {} on to {} imageServer", ci.getLabel(),
                imageServer.getLabel());
        String deCrpytedURL = decryptImageURLPassword(ci.getImageUrl());
        deCrpytedURL = sanitizeUrl(deCrpytedURL);

        String ts = String.valueOf(System.currentTimeMillis());
        String[] tokens = ci.getImageUrl().split("/");
        String imageName = tokens[tokens.length - 1];
        String imagePath = TMP + "/" + imageName;
        String tempDir = TMP + "/os" + ts + "/";

        imageserverDialog.init();
        log.info("connected to image server {}", imageServer.getLabel());

        log.info("cd to {}", TMP);
        imageserverDialog.cd(TMP);

        log.info("download image");
        // CTRL-12030: special characters in URL's password cause issues on
        // Image Server. Adding quotes.
        boolean res = imageserverDialog.wget("'" + deCrpytedURL + "'",
                imageName, imageServer.getImageImportTimeoutMs());

        if (res) {
            log.info("downloaded image successfully on to {}  imageServer",
                    imageServer.getLabel());
        } else {
            throw ImageServerControllerException.exceptions
                    .fileDownloadFailed(maskImageURLPassword(ci.getImageUrl()));
        }

        log.info("create temp dir {}", tempDir);
        imageserverDialog.mkdir(tempDir);

        log.info("mount image onto temp dir of {}", imageServer.getLabel());
        imageserverDialog.mount(imageName, tempDir);

        log.info("Analyze metadata");
        ComputeImage osMetadata = getOsMetadata(imageserverDialog, imagePath,
                tempDir);

        isSupportedImage(osMetadata);

        // make sure it is not already loaded
        List<URI> ids = dbClient.queryByType(ComputeImage.class, true);
        Iterator<ComputeImage> iter = dbClient.queryIterativeObjects(
                ComputeImage.class, ids);
        while (iter.hasNext()) {
            ComputeImage existingImage = iter.next();
            if (osMetadata.fullName().equals(existingImage.getImageName())
                    && imageServer.getComputeImages() != null
                    && imageServer.getComputeImages().contains(
                            existingImage.getId().toString())) {
                log.error("This image is already imported, id: {}",
                        existingImage.getId());
                cleanupTemp(imageserverDialog, tempDir, imagePath);
                throw ImageServerControllerException.exceptions
                        .duplicateImage(osMetadata.fullName());
            }
        }
        log.info("Compute image '" + osMetadata.fullName()
                + "' will be loaded.");

        // copy OS into TFTP boot directory
        String targetDir = imageServer.getTftpBootDir()
                + imageServer.getImageDir() + osMetadata.fullName();

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
        ci.setPathToDirectory(imageServer.getImageDir() + osMetadata.fullName()
                + "/");
        ci.setImageName(osMetadata.fullName());
        ci.setImageType(osMetadata.getImageType());
        ci.setComputeImageStatus(ComputeImageStatus.AVAILABLE.toString());

        dbClient.updateObject(ci);
        String ciURIString = ci.getId().toString();
        // update the imageServer with the successfully updated image.
        if (imageServer.getComputeImages() == null) {
            imageServer.setComputeImages(new StringSet());
        }
        imageServer.getComputeImages().add(ciURIString);
        //check if this image was previously failed, if so remove from fail list
        if (imageServer.getFailedComputeImages() != null &&
                imageServer.getFailedComputeImages().contains(ciURIString)) {
            imageServer.getFailedComputeImages().remove(ciURIString);
        }
        log.info("Successfully imported image {} on to {} imageServer", ci.getLabel(),
                imageServer.getLabel());
        dbClient.updateObject(imageServer);
        // clean up
        cleanupTemp(imageserverDialog, tempDir, imagePath);
    }

    @Override
    public void deleteImage(AsyncTask task) throws InternalException {
        log.info("deleteImage " + task._id);

        URI ciId = task._id;

        TaskCompleter completer = null;
        try {
            completer = new ComputeImageCompleter(ciId, task._opId,
                    OperationTypeEnum.DELETE_COMPUTE_IMAGE, EVENT_SERVICE_TYPE);
            Workflow workflow = workflowService.getNewWorkflow(this,
                    DELETE_IMAGE_WF, true, task._opId);
            List<URI> ids = dbClient
                    .queryByType(ComputeImageServer.class, true);
            for (URI imageServerId : ids) {
                ComputeImageServer imageServer = dbClient.queryObject(
                        ComputeImageServer.class, imageServerId);
                if (imageServer.getComputeImages() != null
                        && imageServer.getComputeImages().contains(
                                ciId.toString())) {
                    boolean imageServerVerified = verifyImageServer(imageServer);

                    if (!imageServerVerified) {
                        throw ImageServerControllerException.exceptions
                                .imageServerNotSetup("Can't delete image: "
                                        + imageServerErrorMsg);
                    }

                    workflow.createStep(DELETE_IMAGE_STEP, String.format(
                            "removing image %s", ciId), null, ciId, ciId
                                    .toString(),
                            this.getClass(), new Workflow.Method(
                                    "deleteImageMethod", ciId, imageServer.getId()),
                            null, null);
                }
                //The image being deleted/cleaned up must also be removed from the
                //imageServer's failedImages list, because the image can be AVAILABLE
                //but could have failed to import on some of the imageServers.
                //So this cleanup needs to be performed.
                if(imageServer.getFailedComputeImages() != null
                        && imageServer.getFailedComputeImages().contains(
                                ciId.toString())) {
                    imageServer.getFailedComputeImages().remove(ciId.toString());
                    dbClient.updateObject(imageServer);
                }
            }

            workflow.executePlan(completer, SUCCESS);
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
            d.rm(imageServer.getTftpBootDir() + ci.getPathToDirectory());
            log.info("delete done");
            if (imageServer.getComputeImages() != null && imageServer.getComputeImages().contains(ciId.toString())) {
                imageServer.getComputeImages().remove(ciId.toString());
                dbClient.updateObject(imageServer);
            }
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
                log.error(FAILED_TO_CLOSE_STR, e);
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
        ComputeImageServer imageServer = dbClient.queryObject(ComputeImageServer.class, job.getComputeImageServerId());
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

            workflow.executePlan(completer, SUCCESS);
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
            if (!d.directoryExists(imageServer.getTftpBootDir() + img.getPathToDirectory())) {
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
                d.cd(imageServer.getTftpBootDir() + HTTP_DIR);
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
                log.error(FAILED_TO_CLOSE_STR, e);
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
                log.error(FAILED_TO_CLOSE_STR, e);
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
                dbClient.updateObject(job);
            } else {
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
                    d.rm(imageServer.getTftpBootDir() + HTTP_SUCCESS_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpBootDir() + HTTP_FAILURE_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpBootDir() + HTTP_KICKSTART_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpBootDir() + HTTP_FIRSTBOOT_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpBootDir() + PXELINUX_CFG_DIR + job.getPxeBootIdentifier());
                    d.rm(imageServer.getTftpBootDir() + PXELINUX_CFG_DIR + job.getPxeBootIdentifier() + ".boot.cfg");
                } catch (Exception e) {
                    log.error("exception when trying to poll for status", e);
                } finally {
                    try {
                        if (d != null && d.isConnected()) {
                            d.close();
                        }
                    } catch (Exception e) {
                        log.error(FAILED_TO_CLOSE_STR, e);
                    }
                }
            }

            log.info("job status: {}", status);

            if (status == OsInstallStatus.SUCCESS) {
                log.info("session {} - marking job as SUCCESS", job.getPxeBootIdentifier());
                job.setJobStatus(JobStatus.SUCCESS.name());
                dbClient.updateObject(job);
                WorkflowStepCompleter.stepSucceded(stepId);
            } else if (status == OsInstallStatus.FAILURE) {
                log.info("session {} - marking job as FAILED", job.getPxeBootIdentifier());
                job.setJobStatus(JobStatus.FAILED.name());
                dbClient.updateObject(job);
                WorkflowStepCompleter.stepFailed(stepId,
                        ImageServerControllerException.exceptions.osInstallationFailed("failure in the post-install"));
            } else { // timed out
                log.info("session {} - marking job as TIMEDOUT", job.getPxeBootIdentifier());
                job.setJobStatus(JobStatus.TIMEDOUT.name());
                dbClient.updateObject(job);
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
     * Method to verify and import images on the imageserver
     *
     * @param task {@link AsyncTask} instance
     * @param opName operation Name
     *
     */
    @Override
    public void verifyImageServerAndImportExistingImages(AsyncTask task, String opName) {
        TaskCompleter completer = null;
        log.info("Verifying imageServer and importing any existing images on to the server");
        try {
            URI computeImageServerID = task._id;
            completer = new ComputeImageServerCompleter(computeImageServerID,
                    task._opId,
                    OperationTypeEnum.IMAGESERVER_VERIFY_IMPORT_IMAGES,
                    EVENT_SERVICE_TYPE);

            Workflow workflow = workflowService.getNewWorkflow(this,
                    IMAGESERVER_VERIFY_IMPORT_IMAGE_WF, true, task._opId);
            workflow.createStep(IMAGESERVER_VERIFICATION_STEP, String.format(
                    "Verfiying ImageServer %s", computeImageServerID), null,
                    computeImageServerID, computeImageServerID.toString(), this
                            .getClass(),
                    new Workflow.Method(
                            "verifyComputeImageServer", computeImageServerID),
                    null, null);
            List<ComputeImage> computeImageList = getAllComputeImages();
            if (!CollectionUtils.isEmpty(computeImageList)) {
                ComputeImageServer imageServer = dbClient.queryObject(
                        ComputeImageServer.class, computeImageServerID);
                for (ComputeImage computeImage : computeImageList) {
                    if (null == imageServer.getComputeImages()
                            || !imageServer.getComputeImages().contains(
                                    computeImage.getId().toString())) {
                        StringBuilder msg = new StringBuilder(
                                "Importing image ");
                        msg.append(computeImage.getLabel()).append(
                                " on to imageServer - ");
                        msg.append(imageServer.getImageServerIp()).append(".");

                        workflow.createStep(IMAGESERVER_IMPORT_IMAGES_STEP, msg
                                .toString(), IMAGESERVER_VERIFICATION_STEP,
                                computeImageServerID, computeImageServerID
                                        .toString(), this.getClass(),
                                new Workflow.Method("importImageMethod",
                                        computeImage.getId(), imageServer,
                                        opName), null, null);
                    }
                }
            }
            workflow.executePlan(completer, SUCCESS);
        } catch (Exception ex) {
            log.error(
                    "Unexpected exception waiting for finish: "
                            + ex.getMessage(),
                    ex);
        }
    }

    /**
     * Method to fetch all compute images present in the db.
     *
     * @return {@link List<ComputeImage>}
     */
    private List<ComputeImage> getAllComputeImages() {
        List<ComputeImage> imageList = null;
        List<URI> imageURIList = dbClient.queryByType(ComputeImage.class, true);
        if (imageURIList == null || !imageURIList.iterator().hasNext()) {
            log.info("There are no images to be imported.");
        } else {
            imageList = dbClient.queryObject(ComputeImage.class, imageURIList);
            if (CollectionUtils.isEmpty(imageList)) {
                log.error("Could not find the ComputeImage's for the Ids {}",
                        imageURIList.toString());
            }
        }

        return imageList;
    }

    /**
     * This method verifies if the given image Server is a valid imageServer.
     *
     * @param imageServerId {@link URI} of ComputeImageServer
     * @param stepId workflow stepid being executed.
     */
    public void verifyComputeImageServer(URI imageServerId, String stepId) {
        log.info("entering method verifyComputeImageServer");
        WorkflowStepCompleter.stepExecuting(stepId);

        ComputeImageServer imageServer = dbClient.queryObject(
                ComputeImageServer.class, imageServerId);

        if (verifyImageServer(imageServer)) {
            imageServer
                    .setComputeImageServerStatus(ComputeImageServer.ComputeImageServerStatus.AVAILABLE
                            .name());
            dbClient.updateObject(imageServer);
            WorkflowStepCompleter.stepSucceded(stepId);
        } else {
            log.error("Unable to verify imageserver");
            imageServer
                    .setComputeImageServerStatus(ComputeImageServer.ComputeImageServerStatus.NOT_AVAILABLE
                            .name());
            dbClient.updateObject(imageServer);
            WorkflowStepCompleter
                    .stepFailed(
                            stepId,
                            ImageServerControllerException.exceptions
                                    .unexpectedException(
                                            OperationTypeEnum.IMAGESERVER_VERIFY_IMPORT_IMAGES
                                                    .name(),
                                            new Exception(
                                                    "Unable to verify imageserver")));
        }
    }

    /**
     * Updates the imageServer with the image that failed import, this method updates
     * it as failed only after making sure that the image was not previously successful.
     * @param imageServerURI {@link URI} imageServerURI instance to which import was made.
     * @param image {@link ComputeImage} instance that failed the import.
     */
    private void updateFailedImages(URI imageServerURI, ComputeImage image) {
        if (null != imageServerURI && null != image) {
            String imageURIStr = image.getId().toString();
            log.info("updateFailedImages : update failed image import details.");
            // first fetch updated imageServer details from DB and
            // verify if image was previously loaded successfully on to
            // the imageServer, if so then skip updating it as failed else
            // update it as failed.
            ComputeImageServer imageServer = dbClient.queryObject(
                    ComputeImageServer.class, imageServerURI);
            if (imageServer.getComputeImages() == null
                    || !imageServer.getComputeImages().contains(imageURIStr)) {
                // update the imageServer with the failed image.
                if (imageServer.getFailedComputeImages() == null) {
                    imageServer.setFailedComputeImages(new StringSet());
                }
                log.info(
                        "Image - {} failed to import on imageServer - {}",
                        image.getLabel(), imageServer.getLabel());
                imageServer.getFailedComputeImages().add(imageURIStr);
                dbClient.updateObject(imageServer);
            }
        }
    }

    /**
     * Method to decrypt the imageURL password before it can be used.
     * This method also takes care of encoding the password before use.
     * @param imageUrl {@link String} compute image URL string
     * @return {@link String}
     */
    private String decryptImageURLPassword(String imageUrl) {
        String password = extractPasswordFromImageUrl(imageUrl);
        if (StringUtils.isNotBlank(password)) {
            String encPwd = null;
            try {
                EncryptionProviderImpl encryptionProviderImpl = new EncryptionProviderImpl();
                encryptionProviderImpl.setCoordinator(_coordinator);
                encryptionProviderImpl.start();
                EncryptionProvider encryptionProvider = encryptionProviderImpl;
                encPwd = URLEncoder.encode(encryptionProvider.decrypt(Base64
                        .decodeBase64(password)), "UTF-8");
                return StringUtils.replace(imageUrl, ":" + password + "@", ":"
                        + encPwd + "@");
            } catch (UnsupportedEncodingException e) {
                log.warn(
                        "Unable to encode compute image password '{}'."
                                + "Special characters may cause issues loading compute image.",
                        imageUrl, e.getMessage());
            } catch (Exception e) {
                log.error("Cannot decrypt compute image password :"
                        + e.getLocalizedMessage());
                e.printStackTrace();
                throw e;
            }
        }
        return imageUrl;
    }

    /**
     * Extract password if present from the given imageUrl string
     * @param imageUrl {@link String} image url
     * @return {@link String} password
     */
    public static String extractPasswordFromImageUrl(String imageUrl)
    {
        Pattern r = Pattern.compile(IMAGEURL_PASSWORD_SPLIT_REGEX);
        Matcher m = r.matcher(imageUrl);
        String password = null;
        if (m.find() && m.groupCount() >= 2
                && StringUtils.isNotBlank(m.group(2))) {
            password = m.group(2);
            Pattern hostpattern = Pattern.compile(IMAGEURL_HOST_REGEX);
            Matcher hostMatcher = hostpattern.matcher(password);
            if(hostMatcher.find()) {
                String preHostregex = "^(.*?)\\@"+hostMatcher.group(1);
                Pattern pwdPattern = Pattern.compile(preHostregex);
                Matcher pwdMatcher = pwdPattern.matcher(password);
                if(pwdMatcher.find())
                {
                    password = pwdMatcher.group(1);
                }
            }
        }
        return password;
    }

    /**
     * Mask the encrypted password for UI
     * @param imageUrl {@link String} image url
     * @return {@link String} password masked image url
     */
    public static String maskImageURLPassword(String imageUrl) {
        String password = extractPasswordFromImageUrl(imageUrl);
        String maskedPasswordURL = imageUrl;
        if (StringUtils.isNotBlank(password)) {
            imageUrl = StringUtils.replace(imageUrl, ":" + password + "@", ":"
                    + MASKED_PASSWORD + "@");
        }
        return maskedPasswordURL;
    }
}
