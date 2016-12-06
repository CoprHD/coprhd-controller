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

package com.emc.sa.service.vipr.oe.tasks;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument.Step;
import com.emc.storageos.services.util.Exec;

/**
 * Runs Orchestration Shell script or Ansible Playbook Tasks.
 * It can run Ansible playbook on local node as well as on Remote node
 *
 */
public class RunAnsible  extends ViPRExecutionTask<OrchestrationTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunAnsible.class);

    private final Step step;
    private final Map<String, List<String>> input;

    public RunAnsible(final Step step, final Map<String, List<String>> input) {
        this.step = step;
        this.input = input;
    }

    @Override
    public OrchestrationTaskResult executeTask() throws Exception {

        ExecutionUtils.currentContext().logInfo("runAnsible.statusInfo", step.getId());
        //TODO Get playbook/package from DB

        final String extraVars = makeExtraArg(input);

        final OrchestrationServiceConstants.StepType type = OrchestrationServiceConstants.StepType.fromString(step.getType());

        final Exec.Result result;
        switch (type) {
            case SHELL_SCRIPT:
                //TODO Dynamically create the playbook from shell script
                result = executeCmd(OrchestrationServiceConstants.PATH + step.getOperation(), extraVars);
                cleanUp(step.getOperation(), false);

                break;
            case LOCAL_ANSIBLE:
                try {
                    untarPackage("ansi.tar");
                    final String ips = getHosts();
                    result = executeLocal(ips, extraVars, OrchestrationServiceConstants.PATH +
                            FilenameUtils.removeExtension("ansi.tar") + "/" + "helloworld.yml", "root");
                } catch (final IOException e) {
                    logger.info("Unable to perform Local Ansible task {}", e);

                    return null;
                } finally {
                    cleanUp("ansi.tar", true);
                }

                break;
            case REMOTE_ANSIBLE:
                //TODO get the information from JSON and primitive
                final String ip = "10.247.66.88";
                final String user = "root";
                final String remotePlaybook = "/data/hello.yml";
                final String remoteBin = "/usr/bin/ansible-playbook";
                result = executeRemoteCmd(user, ip, remotePlaybook, remoteBin, extraVars);

                break;
            default:
                logger.error("Ansible Operation type:{} not supported", type);

                throw new IllegalStateException("Unsupported Operation");
        }

        ExecutionUtils.currentContext().logInfo("runAnsible.doneInfo", step.getId());

        if (result == null)
            return null;

        logger.info("Ansible Execution result:output{} error{} exitValue:{}", result.getStdOutput(), result.getStdError(), result.getExitValue());

        return new OrchestrationTaskResult(result.getStdOutput(), result.getStdError(), result.getExitValue());
    }

    //TODO Hard coded everything for testing.
    //During upload of primitive, user will specify if hosts file is needed or not?
    //If needed then they will specify the List of hosts group (e.g: webservers, linuxhosts ...etc)
    //These information Runner will get from primitives.
    //Once it gets the meta data from the primitive it will get the values from input

    String getHosts() throws IOException {
        final boolean isHostFileRequired = true;
        final String ips;
        if (isHostFileRequired) {
            List<String> lines = Arrays.asList("[webservers]", "10.247.66.88");
            Path file = Paths.get("/opt/storageos/hosts");
            Files.write(file, lines, Charset.forName("UTF-8"));
            ips = "/opt/storageos/hosts";
        } else {
            ips = "10.247.66.88,";
        }

        return ips;
    }

    private Exec.Result executeRemoteCmd(final String user, final String ip, final String path, final String binPath,
                                         final String extraVars) {
        logger.info("executing remote ansi ip:{} path:{} bin:{} extravar:{} user:{}", ip, path, binPath, extraVars, user);
        String commands = binPath + " " + path + " --extra-var " + extraVars;
        final String[] cmds = {"/usr/bin/ssh", user + "@" + ip, commands};

        return Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
    }

    private Exec.Result executeLocal(final String ips, final String extraVars, final String path, final String user) {
        logger.info("local Ansible Execution ips:{} extra var:{} path:{}, user:{}", ips, extraVars, path, user);
        if (user != null) {
            final String[] cmds = {OrchestrationServiceConstants.ANSIBLE_LOCAL_BIN, "-i", ips, "-u", user, path,
                    OrchestrationServiceConstants.EXTRA_VARS, extraVars};
            return Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
        }

        final String[] cmds = {OrchestrationServiceConstants.ANSIBLE_LOCAL_BIN, "-i", ips, path,
                OrchestrationServiceConstants.EXTRA_VARS, extraVars};

        return Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
    }

    private Exec.Result executeCmd(final String path, final String extraVars) {
        final String[] cmds = {OrchestrationServiceConstants.ANSIBLE_LOCAL_BIN, path,
                OrchestrationServiceConstants.EXTRA_VARS, extraVars};

        return Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
    }

    private Exec.Result untarPackage(final String tarFile) throws IOException {
        final String[] cmds = {OrchestrationServiceConstants.UNTAR, OrchestrationServiceConstants.UNTAR_OPTION,
                OrchestrationServiceConstants.PATH + tarFile, "-C", OrchestrationServiceConstants.PATH};
        Exec.Result result = Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);

        if (result == null || result.getExitValue() != 0) {
            logger.error("Failed to Untar package. Error:{}", result.getStdError());

            throw new IOException("Unable to untar package" + result.getStdError());
        }
        logger.info("Ansible Execution untar result:output{} error{} exitValue:{}", result.getStdOutput(), result.getStdError(), result.getExitValue());

        return result;
    }

    /**
     * Ansible extra Argument format:
     * --extra_vars "key1=value1 key2=value2"
     *
     * @param input
     * @return
     * @throws Exception
     */
    private String makeExtraArg(final Map<String, List<String>> input) throws Exception {
        final StringBuilder sb = new StringBuilder("\"");
        for (Map.Entry<String, List<String>> e : input.entrySet())
            sb.append(e.getKey()).append("=").append(e.getValue().get(0)).append(" ");

        sb.append("\"");
        logger.info("extra vars:{}", sb.toString());

        return sb.toString();
    }

    private Exec.Result cleanUp(final String path, final boolean isTar) {
        final String[] cmds = {OrchestrationServiceConstants.REMOVE, OrchestrationServiceConstants.REMOVE_OPTION,
                OrchestrationServiceConstants.PATH + path};
        Exec.Result result = Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
        if (isTar) {
            String[] rmDir = {OrchestrationServiceConstants.REMOVE, OrchestrationServiceConstants.REMOVE_OPTION,
                    OrchestrationServiceConstants.PATH + FilenameUtils.removeExtension(path)};

            if (!Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, rmDir).exitedNormally())
                logger.error("Failed to remove directory:{}", FilenameUtils.removeExtension(path));
        }

        if (!result.exitedNormally())
            logger.error("Failed to cleanup:{} error:{}", path, result.getStdError());

        return result;
    }
}