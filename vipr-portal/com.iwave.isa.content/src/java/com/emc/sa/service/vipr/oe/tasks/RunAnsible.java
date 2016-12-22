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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument.Step;
import com.emc.storageos.services.util.Exec;

/**
 * Runs Orchestration Shell script or Ansible Playbook.
 * It can run Ansible playbook on local node as well as on Remote node
 *
 */
public class RunAnsible  extends ViPRExecutionTask<OrchestrationTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunAnsible.class);

    private final Step step;
    private final Map<String, List<String>> input;
    private final String orderDir;
    private final long timeout;

    public RunAnsible(final Step step, final Map<String, List<String>> input) {
        this.step = step;
        this.input = input;
        this.timeout = (step.getAttributes().getTimeout()!= -1)?step.getAttributes().getTimeout():Exec.DEFAULT_CMD_TIMEOUT;
        orderDir = OrchestrationServiceConstants.PATH + "OE" + ExecutionUtils.currentContext().getOrder().getOrderNumber();
    }

    @Override
    public OrchestrationTaskResult executeTask() throws Exception {

        ExecutionUtils.currentContext().logInfo("runAnsible.statusInfo", step.getId());
        //TODO Get playbook/package from DB

        //TODO After the column family implementation will use this context directory instead of PATH
        if (!createOrderDir(orderDir)) {
            logger.error("Failed to create Order directory:{}", orderDir);
            return null;
        }

        final OrchestrationServiceConstants.StepType type = OrchestrationServiceConstants.StepType.fromString(step.getType());

        final Exec.Result result;
        switch (type) {
            case SHELL_SCRIPT:
                result = executeCmd(OrchestrationServiceConstants.PATH + "runscript.sh", makeParam(input));
                cleanUp("runscript.sh", false);

                break;
            case LOCAL_ANSIBLE:
                try {
                    untarPackage("ansi.tar");
                    final String hosts = getHostFile();
                    result = executeLocal(hosts, makeExtraArg(input), OrchestrationServiceConstants.PATH +
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
                result = executeRemoteCmd(user, ip, remotePlaybook, remoteBin, makeExtraArg(input));

                break;
            default:
                logger.error("Ansible Operation type:{} not supported", type);

                throw new IllegalStateException("Unsupported Operation");
        }

        ExecutionUtils.currentContext().logInfo("runAnsible.doneInfo", step.getId());

        if (result == null)
            return null;

        logger.info("Ansible Execution result:output{} error{} exitValue:{}", result.getStdOutput(), result.getStdError(), result.getExitValue());

        return new OrchestrationTaskResult(parseOut(result.getStdOutput()), result.getStdError(), result.getExitValue());
    }

    private String parseOut(final String out) {
        if (step.getType().equals(OrchestrationServiceConstants.StepType.SHELL_SCRIPT.toString())) 
            return out;

        final String regexString = Pattern.quote("output_start") + "(?s)(.*?)" + Pattern.quote("output_end");
        final Pattern pattern = Pattern.compile(regexString);
        final Matcher matcher = pattern.matcher(out);

        while (matcher.find()) {
            final String textInBetween = matcher.group(1);

            return textInBetween;
        }

        return out;
    }

    private boolean createOrderDir(String dir) {
        File file = new File(dir);
        if (!file.exists()) {
            return file.mkdir();
        } else {
            logger.error("Cannot create directory. Already exists. Dir:{}", dir);
            return false;
        }
    }

    //TODO Hard coded everything for testing.
    //During upload of primitive, user will specify if hosts file is already present or not?
    //If already present, then get it from the param
    //If not present, dynamically create one with the given hostgroups and IpAddress(e.g: webservers, linuxhosts ...etc)
    //If nothing is given by user default to localhost

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

    //TODO Implement for limit, tags
    //Execute Ansible playbook on remote node. Playbook is also in remote node
    private Exec.Result executeRemoteCmd(final String user, final String ip, final String playbook, final String ansiblePath,
                                         final String extraVars) {
        //TODO get it from param
        final String targetNodeIps = null;
        final String targetNodeUser = null;

        final AnsibleCommandLine cmd = new AnsibleCommandLine(ansiblePath, playbook);
        final String[] cmds = cmd.setPrefix(OrchestrationServiceConstants.SHELL_LOCAL_BIN+ " " +user + "@" + ip)
                .setHostFile(targetNodeIps)
                .setUser(targetNodeUser)
                .setLimit(null)
                .setTags(null)
                .setExtraVars(extraVars)
                .build();

        return Exec.exec(timeout, cmds);
    }

    //Execute Ansible playbook on given nodes. Playbook in local node
    private Exec.Result executeLocal(final String ips, final String extraVars, final String playbook, final String user) {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(OrchestrationServiceConstants.ANSIBLE_LOCAL_BIN, playbook);
        final String[] cmds = cmd.setHostFile(ips).setUser(user)
                .setLimit(null)
                .setTags(null)
                .setExtraVars(extraVars)
                .build();

        return Exec.exec(timeout, cmds);
    }


    //Execute Ansible playbook on localhost
    private Exec.Result executeCmd(final String playbook, final String extraVars) {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(OrchestrationServiceConstants.SHELL_BIN, playbook);
        final String[] cmds = cmd.build();

        return Exec.exec(timeout, cmds);
    }

    private Exec.Result untarPackage(final String tarFile) throws IOException {
        final String[] cmds = {OrchestrationServiceConstants.UNTAR, OrchestrationServiceConstants.UNTAR_OPTION,
                OrchestrationServiceConstants.PATH + tarFile, "-C", OrchestrationServiceConstants.PATH};
        Exec.Result result = Exec.exec(timeout, cmds);

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

    private String makeParam(final Map<String, List<String>> input) throws Exception {
        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : input.entrySet())
            sb.append(e.getValue().get(0)).append(" ");

        return sb.toString();
    }

    private Exec.Result cleanUp(final String path, final boolean isTar) {
        final String[] cmds = {OrchestrationServiceConstants.REMOVE, OrchestrationServiceConstants.REMOVE_OPTION,
                OrchestrationServiceConstants.PATH + path};
        Exec.Result result = Exec.exec(timeout, cmds);
        if (isTar) {
            String[] rmDir = {OrchestrationServiceConstants.REMOVE, OrchestrationServiceConstants.REMOVE_OPTION,
                    OrchestrationServiceConstants.PATH + FilenameUtils.removeExtension(path)};

            if (!Exec.exec(timeout, rmDir).exitedNormally())
                logger.error("Failed to remove directory:{}", FilenameUtils.removeExtension(path));
        }

        if (!result.exitedNormally())
            logger.error("Failed to cleanup:{} error:{}", path, result.getStdError());

        //cleanup order context dir
        final String[] cmd = {OrchestrationServiceConstants.REMOVE, OrchestrationServiceConstants.REMOVE_OPTION, orderDir};

        return Exec.exec(timeout, cmd);
    }
}
