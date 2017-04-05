/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsiblePrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsibleResource;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Runs CustomServices Primitives - Shell script or Ansible Playbook.
 * It can run Ansible playbook on local node as well as on Remote node
 *
 */
public class CustomServicesLocalAnsibleExecution extends ViPRExecutionTask<CustomServicesTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesLocalAnsibleExecution.class);
    private final Step step;
    private final Map<String, List<String>> input;
    private  String orderDir = String.format("%s%s/", CustomServicesConstants.ORDER_DIR_PATH,
            ExecutionUtils.currentContext().getOrder().getOrderNumber());
    private final long timeout;

    @Autowired
    private DbClient dbClient;

    public CustomServicesLocalAnsibleExecution(final Map<String, List<String>> input, Step step) {
        this.input = input;
        this.step = step;
        if (step.getAttributes() == null || step.getAttributes().getTimeout() == -1) {
            this.timeout = Exec.DEFAULT_CMD_TIMEOUT;
        } else {
            this.timeout = step.getAttributes().getTimeout();
        }
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {

        ExecutionUtils.currentContext().logInfo("runCustomScript.statusInfo", step.getId());
        final URI scriptid = step.getOperation();

        final StepType type = StepType.fromString(step.getType());

        final Exec.Result result;
        try {

            final CustomServicesDBAnsiblePrimitive ansiblePrimitive = dbClient.queryObject(CustomServicesDBAnsiblePrimitive.class,
                    scriptid);
            final CustomServicesDBAnsibleResource ansiblePackageId = dbClient.queryObject(CustomServicesDBAnsibleResource.class,
                    ansiblePrimitive.getResource());

            // get the playbook which the user has specified during primitive creation from DB.
            // The playbook (resolved to the path in the archive) represents the playbook to execute
            final String playbook = ansiblePrimitive.getAttributes().get("playbook");

            // get the archive from AnsiblePackage CF
            final byte[] ansibleArchive = Base64.decodeBase64(ansiblePackageId.getResource());

            // uncompress Ansible archive to orderDir
            uncompressArchive(ansibleArchive);

            // TODO: Hard coded for testing. The following will be removed after completing COP-27888
            final String hosts = "/opt/storageos/ansi_logs/hosts";

            final String user = ExecutionUtils.currentContext().getOrder().getSubmittedByUserId();
            result = executeLocal(hosts, AnsibleHelper.makeExtraArg(input), String.format("%s%s", orderDir, playbook), user);

        } catch (final Exception e) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }

        ExecutionUtils.currentContext().logInfo("runCustomScript.doneInfo", step.getId());

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
                    final OutputStream out = new FileOutputStream(curTarget);
                    IOUtils.copy(tarIn, out);
                    out.close();

                }
                entry = tarIn.getNextTarEntry();
            }
        } catch (final IOException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Invalid ansible archive", e);
        }
    }


    // TODO: Hard coded everything for testing. The following will be removed after completing COP-27888
    // During upload of primitive, user will specify if hosts file is already present or not?
    // If already present, then get it from the param. currently the host file is not stored in DB
    // If not present, dynamically create one with the given hostgroups and IpAddress(e.g: webservers, linuxhosts ...etc)
    // If nothing is given by user default to localhost

    private String getHostFile() throws IOException {
        final boolean isHostFilePresent = false;
        String hosts;
        if (isHostFilePresent) {
            hosts = "/opt/storageos/ansi/hosts";
        } else {
            List<String> lines = Arrays.asList("[webservers]", "10.247.66.88");
            Path file = Paths.get("/opt/storageos/ansi/hosts");
            Files.write(file, lines, Charset.forName("UTF-8"));
            hosts = "/opt/storageos/ansi/hosts";
        }

        if (hosts == null || hosts.isEmpty())
            hosts = "localhost,";

        return hosts;
    }


    // Execute Ansible playbook on given nodes. Playbook in local node
    private Exec.Result executeLocal(final String ips, final String extraVars, final String playbook, final String user) {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(CustomServicesConstants.ANSIBLE_LOCAL_BIN, playbook);
        final String[] cmds = cmd.setHostFile(ips).setUser(user)
                .setLimit(null)
                .setTags(null)
                .setExtraVars(extraVars)
                .setCommandLine(AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_COMMAND_LINE, input))
                .build();

        return Exec.exec(timeout, cmds);
    }
}
