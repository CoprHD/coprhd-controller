/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.sa.service.vipr.customservices.tasks;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsibleInventoryResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsiblePrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsibleResource;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

/**
 * Runs CustomServices Operation: Ansible Playbook.
 * It can run Ansible playbook on local node
 *
 */
public class CustomServicesLocalAnsibleExecution extends ViPRExecutionTask<CustomServicesTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesLocalAnsibleExecution.class);
    private final Step step;
    private final Map<String, List<String>> input;
    private final String orderDir;
    private final String chrootOrderDir;
    private final long timeout;
    private final DbClient dbClient;

    public CustomServicesLocalAnsibleExecution(final Map<String, List<String>> input, final Step step, final DbClient dbClient,
            final String orderDir) {
        this.input = input;
        this.step = step;
        if (step.getAttributes() == null || step.getAttributes().getTimeout() == -1) {
            this.timeout = CustomServicesConstants.OPERATION_TIMEOUT;
        } else {
            this.timeout = step.getAttributes().getTimeout();
        }
        this.dbClient = dbClient;
        provideDetailArgs(step.getId(), step.getFriendlyName());
        this.orderDir = orderDir;
        final String folderUniqueStep = step.getId().replace("-", "");
        this.chrootOrderDir = String.format("%s%s/%s/", CustomServicesConstants.CHROOT_ORDER_DIR_PATH,
                ExecutionUtils.currentContext().getOrder().getOrderNumber(),folderUniqueStep);
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {

        ExecutionUtils.currentContext().logInfo("customServicesScriptExecution.statusInfo", step.getId(), step.getFriendlyName());
        final URI scriptid = step.getOperation();
        final List<String> fileSoftLink = new ArrayList<String>();
        final List<String> fileAbsolutePath = new ArrayList<String>();

        final Exec.Result result;
        try {

            final CustomServicesDBAnsiblePrimitive ansiblePrimitive = dbClient.queryObject(CustomServicesDBAnsiblePrimitive.class,
                    scriptid);
            if (null == ansiblePrimitive) {
                logger.error("Error retrieving the Ansible primitive from DB. {} not found in DB", scriptid);
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "Error retrieving Ansible primitive from DB.");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed(scriptid + " not found in DB");
            }
            final CustomServicesDBAnsibleResource ansiblePackageId = dbClient.queryObject(CustomServicesDBAnsibleResource.class,
                    ansiblePrimitive.getResource());
            if (null == ansiblePackageId) {
                logger.error("Error retrieving the resource for the Ansible primitive from DB. {} not found in DB",
                        ansiblePrimitive.getResource());
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "Error retrieving the resource for the Ansible primitive from DB. ");

                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed(ansiblePrimitive.getResource() + " not found in DB");
            }
            // get the playbook which the user has specified during primitive creation from DB.
            // The playbook (resolved to the path in the archive) represents the playbook to execute
            final String playbook = ansiblePrimitive.getAttributes().get("playbook");

            // get the archive from AnsiblePackage CF
            final byte[] ansibleArchive = Base64.decodeBase64(ansiblePackageId.getResource());

            // uncompress Ansible archive to orderDir
            // Supply two list that will hold file name and absolute path for soft links
            uncompressArchive(ansibleArchive, fileSoftLink, fileAbsolutePath);

            final String hostFileFromStep = AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_HOST_FILE, input);

            if (StringUtils.isBlank(hostFileFromStep)) {
                logger.error("CS: Inventory file not set in operation:{}", step.getId());
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),"Inventory file not set");
                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed("Inventory file not set");
            }

            final CustomServicesDBAnsibleInventoryResource inventoryResource = dbClient
                    .queryObject(CustomServicesDBAnsibleInventoryResource.class, URI.create(hostFileFromStep));

            if (null == inventoryResource) {
                logger.error("Error retrieving the inventory resource for the Ansible primitive from DB. {} not found in DB",
                        hostFileFromStep);
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "Error retrieving the inventory resource  from DB");
                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed(hostFileFromStep + " not found in DB");
            }

            if (!URIUtil.identical(inventoryResource.getParentId(), ansiblePackageId.getId())) {
                logger.error("The inventory file and the Ansible package that are passed do not match. inventory.parentId {} ansiblePackage.id {}",
                        inventoryResource.getParentId(),ansiblePackageId.getId());
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "The inventory file and the Ansible package that are passed do not match");
                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed("The inventory file and the Ansible package that are passed do not match");
            }

            final String inventoryFileName = String.format("%s%s", orderDir,
                    URIUtil.parseUUIDFromURI(URI.create(hostFileFromStep)).replace("-", ""));

            final byte[] inventoryResourceBytes = Base64.decodeBase64(inventoryResource.getResource());
            AnsibleHelper.writeResourceToFile(inventoryResourceBytes, inventoryFileName);

            final String user = ExecutionUtils.currentContext().getOrder().getSubmittedByUserId();

            final String chrootInventoryFileName = String.format("%s%s", chrootOrderDir,
                    URIUtil.parseUUIDFromURI(URI.create(hostFileFromStep)).replace("-", ""));

            // Soft link all files from Ansible tar
            final Exec.Result softlinkResult = Exec.exec(new File(CustomServicesConstants.CHROOT_DIR),timeout,null,new HashMap<String,String>(), softLinkCmd(fileAbsolutePath));
            if (softlinkResult == null) {
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "Local Ansible execution Failed");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Softlinking failed for scripts");
            }

            // Make sure we have all permission for soft link files
            final Exec.Result chmodResult = Exec.exec(new File(CustomServicesConstants.CHROOT_DIR),timeout,null,new HashMap<String,String>(), chmodCmd(fileSoftLink));
            if (chmodResult == null) {
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "Local Ansible execution Failed");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("chmod command failed for scripts");
            }

            result = executeLocal(chrootInventoryFileName, AnsibleHelper.makeExtraArg(input,step), String.format("%s%s", chrootOrderDir, playbook), user);

            // unlink all Ansible package files for cleanup
            for(final String filename: fileSoftLink) {
                final String[] unlinkFiles = unlinkCmd(filename);
                Exec.exec(timeout, unlinkFiles);
            }
        } catch (final Exception e) {
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                    "Custom Service Task Failed" + e);
            logger.error("Exception:", e);
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }

        ExecutionUtils.currentContext().logInfo("customServicesScriptExecution.doneInfo", step.getId(), step.getFriendlyName());

        if (result == null) {
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                    "Local Ansible execution Failed");
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Local Ansible execution Failed");
        }


        logger.info("CustomScript Execution result:output{} error{} exitValue:{}", result.getStdOutput(), result.getStdError(),
                result.getExitValue());

        if (result.getExitValue()!=0) {
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                    "Local Ansible execution Failed. ReturnCode:" + result.getExitValue());
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Local Ansible execution Failed");

        }

        return new CustomServicesScriptTaskResult(AnsibleHelper.parseOut(result.getStdOutput()), result.getStdOutput(), result.getStdError(), result.getExitValue());
    }

    private void uncompressArchive(final byte[] ansibleArchive, final List<String> fileList, final List<String> pathList) {
        try (final TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new GzipCompressorInputStream(new ByteArrayInputStream(
                        ansibleArchive)))) {
            TarArchiveEntry entry = tarIn.getNextTarEntry();
            while (entry != null) {
                final File curTarget = new File(orderDir, entry.getName());
                if (entry.isDirectory()) {
                    curTarget.mkdirs();
                } else {
                    final File parent = curTarget.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    try (final OutputStream out = new FileOutputStream(curTarget)) {
                        IOUtils.copy(tarIn, out);
                    }
                    // Add file name and file path for softlinks
                    fileList.add(curTarget.getName());
                    pathList.add(curTarget.getAbsolutePath().replaceFirst(CustomServicesConstants.CHROOT_DIR + "/", ""));
                }
                entry = tarIn.getNextTarEntry();
            }
        } catch (final IOException e) {
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),"Invalid Ansible archive");
            logger.error("Exception:", e);
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Invalid Ansible archive", e);
        }
    }

    // Execute Ansible playbook on given nodes. Playbook in local node
    private Exec.Result executeLocal(final String ips, final String extraVars, final String playbook, final String user) {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(CustomServicesConstants.ANSIBLE_LOCAL_BIN, playbook);
        final String[] cmds = cmd.setHostFile(ips).setUser(user)
                .setChrootCmd(CustomServicesConstants.CHROOT_CMD)
                .setLimit(null)
                .setTags(null)
                .setExtraVars(extraVars)
                .setCommandLine(AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_COMMAND_LINE, input))
                .build();

        return Exec.sudo(new File(orderDir), timeout, null, new HashMap<String,String>(), cmds);
    }

    private String[] softLinkCmd(final List <String> fileAbsolutePath) {
        final ImmutableList.Builder<String> builder = ImmutableList.builder();

        // ln -s with full path
        builder.add(CustomServicesConstants.SUDO_CMD);
        builder.add(CustomServicesConstants.SOFTLINK_CMD);
        builder.add(CustomServicesConstants.SOFTLINK_OPTION);
        // Add all files with absolute path
        for(final String absoluteDir: fileAbsolutePath) {
            builder.add(absoluteDir);
        }

        // Added the chroot path
        builder.add(CustomServicesConstants.CHROOT_DIR);

        final ImmutableList<String> cmdList = builder.build();
        return cmdList.toArray(new String[cmdList.size()]);
    }

    private String[] chmodCmd(final List <String> fileList) {
        final ImmutableList.Builder<String> builder = ImmutableList.builder();

        builder.add(CustomServicesConstants.SUDO_CMD);
        builder.add(CustomServicesConstants.CHMOD_CMD);
        builder.add(CustomServicesConstants.CHMOD_OPTION);
        // Add all files for chmod 777
        for(final String filename: fileList) {
            builder.add(filename);
        }

        final ImmutableList<String> cmdList = builder.build();
        return cmdList.toArray(new String[cmdList.size()]);
    }

    private String[] unlinkCmd(String filename) {
        final String orderFile = CustomServicesConstants.CHROOT_DIR + "/" + filename;
        return new String[] { "/usr/bin/unlink", orderFile};
    }
}
