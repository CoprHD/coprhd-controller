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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final long timeout;
    private final DbClient dbClient;

    public CustomServicesLocalAnsibleExecution(final Map<String, List<String>> input, final Step step, final DbClient dbClient,
            final String orderDir) {
        this.input = input;
        this.step = step;
        if (step.getAttributes() == null || step.getAttributes().getTimeout() == -1) {
            this.timeout = Exec.DEFAULT_CMD_TIMEOUT;
        } else {
            this.timeout = step.getAttributes().getTimeout();
        }
        this.dbClient = dbClient;
        this.orderDir = orderDir;
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {

        ExecutionUtils.currentContext().logInfo("customServicesScriptExecution.statusInfo", step.getId());
        final URI scriptid = step.getOperation();

        final Exec.Result result;
        try {

            final CustomServicesDBAnsiblePrimitive ansiblePrimitive = dbClient.queryObject(CustomServicesDBAnsiblePrimitive.class,
                    scriptid);
            if (null == ansiblePrimitive) {
                logger.error("Error retrieving the ansible primitive from DB. {} not found in DB", scriptid);
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed(scriptid + " not found in DB");
            }
            final CustomServicesDBAnsibleResource ansiblePackageId = dbClient.queryObject(CustomServicesDBAnsibleResource.class,
                    ansiblePrimitive.getResource());
            if (null == ansiblePackageId) {
                logger.error("Error retrieving the resource for the ansible primitive from DB. {} not found in DB",
                        ansiblePrimitive.getResource());

                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed(ansiblePrimitive.getResource() + " not found in DB");
            }
            // get the playbook which the user has specified during primitive creation from DB.
            // The playbook (resolved to the path in the archive) represents the playbook to execute
            final String playbook = ansiblePrimitive.getAttributes().get("playbook");

            // get the archive from AnsiblePackage CF
            final byte[] ansibleArchive = Base64.decodeBase64(ansiblePackageId.getResource());

            // uncompress Ansible archive to orderDir
            uncompressArchive(ansibleArchive);

            final String hostFileFromStep = AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_HOST_FILE, input);

            if (StringUtils.isBlank(hostFileFromStep)) {
                logger.error("Error retrieving the inventory resource from the ansible primitive step");

                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed("Inventory resource not found in step input");
            }

            final CustomServicesDBAnsibleInventoryResource inventoryResource = dbClient
                    .queryObject(CustomServicesDBAnsibleInventoryResource.class, URI.create(hostFileFromStep));

            if (null == inventoryResource) {
                logger.error("Error retrieving the inventory resource for the ansible primitive from DB. {} not found in DB",
                        hostFileFromStep);

                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed(hostFileFromStep + " not found in DB");
            }

            final String inventoryFileName = String.format("%s%s", orderDir,
                    URIUtil.parseUUIDFromURI(URI.create(hostFileFromStep)).replace("-", ""));

            final byte[] inventoryResourceBytes = Base64.decodeBase64(inventoryResource.getResource());
            AnsibleHelper.writeResourceToFile(inventoryResourceBytes, inventoryFileName);

            final String user = ExecutionUtils.currentContext().getOrder().getSubmittedByUserId();
            result = executeLocal(inventoryFileName, AnsibleHelper.makeExtraArg(input), String.format("%s%s", orderDir, playbook), user);

        } catch (final Exception e) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }

        ExecutionUtils.currentContext().logInfo("customServicesScriptExecution.doneInfo", step.getId());

        if (result == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Script/Ansible execution Failed");
        }

        logger.info("CustomScript Execution result:output{} error{} exitValue:{}", result.getStdOutput(), result.getStdError(),
                result.getExitValue());

        return new CustomServicesTaskResult(AnsibleHelper.parseOut(result.getStdOutput()), result.getStdError(), result.getExitValue(), null);
    }

    private void uncompressArchive(final byte[] ansibleArchive) {
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
                }
                entry = tarIn.getNextTarEntry();
            }
        } catch (final IOException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Invalid ansible archive", e);
        }
    }

    // Execute Ansible playbook on given nodes. Playbook in local node
    private Exec.Result executeLocal(final String ips, final String extraVars, final String playbook, final String user) {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(CustomServicesConstants.ANSIBLE_LOCAL_BIN, playbook);
        final String[] cmds = cmd.setHostFile(ips).setUser(user)
                .setPrefix(CustomServicesConstants.CHROOT_PREFIX)
                .setLimit(null)
                .setTags(null)
                .setExtraVars(extraVars)
                .setCommandLine(AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_COMMAND_LINE, input))
                .build();
        //default to no host key checking
        final Map<String,String> environment = new HashMap<>();
        environment.put("ANSIBLE_HOST_KEY_CHECKING", "false");
        return Exec.exec(timeout, null, environment, cmds);
    }
}
