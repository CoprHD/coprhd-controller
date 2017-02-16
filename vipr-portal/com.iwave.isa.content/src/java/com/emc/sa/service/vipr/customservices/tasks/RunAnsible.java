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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.customservices.CustomServicesConstants;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.uimodels.CustomServicesScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesScriptResource;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.Primitive.StepType;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

/**
 * Runs Custom Services Shell script or Ansible Playbook.
 * It can run Ansible playbook on local node as well as on Remote node
 *
 */
public class RunAnsible extends ViPRExecutionTask<CustomServicesTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunAnsible.class);

    private final Step step;
    private final Map<String, List<String>> input;
    private final String orderDir;
    private final long timeout;
    private final Map<String, Object> params;
    private final DbClient dbClient;

    public RunAnsible(final Step step, final Map<String, List<String>> input, final Map<String, Object> params, final DbClient dbClient) {
        this.step = step;
        this.input = input;
        if (step.getAttributes() == null || step.getAttributes().getTimeout() == -1) {
            this.timeout = Exec.DEFAULT_CMD_TIMEOUT;
        } else {
            this.timeout = step.getAttributes().getTimeout();
        }
        this.params = params;
        this.dbClient = dbClient;

        orderDir = String.format("%sCS%s/", CustomServicesConstants.PATH, ExecutionUtils.currentContext().getOrder().getOrderNumber());

    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {

        // TODO: change the message for shell script. The following is for ansible. will refactor after the local ansible work
        ExecutionUtils.currentContext().logInfo("runAnsible.statusInfo", step.getId());
        final URI scriptid = step.getOperation();
        if (!createOrderDir(orderDir)) {
            logger.error("Failed to create Order directory:{}", orderDir);
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Failed to create Order directory " + orderDir);
        }

        final StepType type = StepType.fromString(step.getType());

        final Exec.Result result;
        try {
            switch (type) {
                case SHELL_SCRIPT:
                    // get the resource database
                    final CustomServicesScriptPrimitive primitive = dbClient.queryObject(CustomServicesScriptPrimitive.class, scriptid);
                    if (null == primitive) {
                        logger.error("Error retrieving the script primitive from DB. {} not found in DB", scriptid);
                        throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed(scriptid + " not found in DB");
                    }

                    final CustomServicesScriptResource script = dbClient.queryObject(CustomServicesScriptResource.class,
                            primitive.getScript());

                    if (null == script) {
                        logger.error("Error retrieving the resource for the script primitive from DB. {} not found in DB",
                                primitive.getScript());

                        throw InternalServerErrorException.internalServerErrors
                                .customServiceExecutionFailed(primitive.getScript() + " not found in DB");
                    }

                    // Currently, the stepId is set to random hash values in the UI. If this changes then we have to change the following to
                    // generate filename with URI from step.getOperation()
                    final String scriptFileName = String.format("%s%s.sh", orderDir, step.getId());

                    final byte[] bytes = Base64.decodeBase64(script.getResource());
                    try (FileOutputStream fileOuputStream = new FileOutputStream(scriptFileName)) {
                        fileOuputStream.write(bytes);
                    } catch (IOException e) {
                        logger.error("Creating Shell Script file failed with exception: {}",
                                e.getMessage());
                        throw InternalServerErrorException.internalServerErrors
                                .customServiceExecutionFailed("Creating Shell Script file failed with exception:" +
                                        e.getMessage());
                    }

                    final String inputToScript = makeParam(input);
                    logger.debug("input is {}", inputToScript);

                    result = executeCmd(scriptFileName, inputToScript);

                    // TODO: refactor after local ansible work
                    cleanUp(orderDir, false);

                    break;
                case LOCAL_ANSIBLE:
                    untarPackage("ansi.tar");
                    final String hosts = getHostFile();
                    result = executeLocal(hosts, makeExtraArg(input), CustomServicesConstants.PATH +
                            FilenameUtils.removeExtension("ansi.tar") + "/" + "helloworld.yml", "root");
                    // TODO: refactor after local ansible work
                    cleanUp("ansi.tar", true);
                    break;
                case REMOTE_ANSIBLE:
                    result = executeRemoteCmd(makeExtraArg(input));

                    break;
                default:
                    logger.error("Ansible Operation type:{} not supported", type);

                    throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Unsupported Operation");
            }
        } catch (final Exception e) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }

        ExecutionUtils.currentContext().logInfo("runAnsible.doneInfo", step.getId());

        if (result == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Script/Ansible execution Failed");
        }

        logger.info("Ansible Execution result:output{} error{} exitValue:{}", result.getStdOutput(), result.getStdError(),
                result.getExitValue());

        return new CustomServicesTaskResult(parseOut(result.getStdOutput()), result.getStdError(), result.getExitValue(), null);
    }

    private String parseOut(final String out) {
        if (step.getType().equals(StepType.SHELL_SCRIPT.toString())) {
            logger.info("Type is shell script");

            return out;
        }
        final String regexString = Pattern.quote("output_start") + "(?s)(.*?)" + Pattern.quote("output_end");
        final Pattern pattern = Pattern.compile(regexString);
        final Matcher matcher = pattern.matcher(out);

        while (matcher.find()) {
            return matcher.group(1);
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

    // TODO Hard coded everything for testing.
    // During upload of primitive, user will specify if hosts file is already present or not?
    // If already present, then get it from the param
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

    // Execute Ansible playbook on remote node. Playbook is also in remote node
    private Exec.Result executeRemoteCmd(final String extraVars) {
        final Map<String, CustomServicesWorkflowDocument.InputGroup> inputType = step.getInputGroups();
        if (inputType == null) {
            return null;
        }

        final AnsibleCommandLine cmd = new AnsibleCommandLine(
                getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_BIN,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()),
                getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_PLAYBOOK,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()));
        final String[] cmds = cmd.setSsh(CustomServicesConstants.SHELL_LOCAL_BIN)
                .setUserAndIp(getAnsibleConnAndOptions(CustomServicesConstants.REMOTE_USER,
                        inputType.get(CustomServicesConstants.CONNECTION_DETAILS).getInputGroup()),
                        getAnsibleConnAndOptions(CustomServicesConstants.REMOTE_NODE,
                                inputType.get(CustomServicesConstants.CONNECTION_DETAILS).getInputGroup()))
                .setHostFile(getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_HOST_FILE,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()))
                .setUser(getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_USER,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()))
                .setCommandLine(getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_COMMAND_LINE,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()))
                .setExtraVars(extraVars)
                .build();

        return Exec.exec(timeout, cmds);
    }

    // Execute Ansible playbook on given nodes. Playbook in local node
    private Exec.Result executeLocal(final String ips, final String extraVars, final String playbook, final String user) {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(CustomServicesConstants.ANSIBLE_LOCAL_BIN, playbook);
        final String[] cmds = cmd.setHostFile(ips).setUser(user)
                .setLimit(null)
                .setTags(null)
                .setExtraVars(extraVars)
                .build();

        return Exec.exec(timeout, cmds);
    }

    // Execute Ansible playbook on localhost
    private Exec.Result executeCmd(final String playbook, final String extraVars) {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(CustomServicesConstants.SHELL_BIN, playbook);
        cmd.setShellArgs(extraVars);
        final String[] cmds = cmd.build();

        return Exec.exec(timeout, cmds);
    }

    private String getAnsibleConnAndOptions(final String key, final List<Input> stepInput) {
        if (params.get(key) != null) {
            return StringUtils.strip(params.get(key).toString(), "\"");
        }

        for (final Input in : stepInput) {
            if (in.getName().equals(key)) {
                if (in.getDefaultValue() != null) {
                    return in.getDefaultValue();
                }
            }
        }

        logger.error("Can't find the value for:{}", key);
        return null;
    }

    private Exec.Result untarPackage(final String tarFile) throws IOException {
        final String[] cmds = { CustomServicesConstants.UNTAR, CustomServicesConstants.UNTAR_OPTION,
                CustomServicesConstants.PATH + tarFile, "-C", CustomServicesConstants.PATH };
        Exec.Result result = Exec.exec(timeout, cmds);

        if (result == null || result.getExitValue() != 0) {
            logger.error("Failed to Untar package. Error:{}", result.getStdError());

            throw new IOException("Unable to untar package" + result.getStdError());
        }
        logger.info("Ansible Execution untar result:output{} error{} exitValue:{}", result.getStdOutput(), result.getStdError(),
                result.getExitValue());

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
        if (input == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder("\"");
        for (Map.Entry<String, List<String>> e : input.entrySet()) {
            // TODO find a better way to fix this
            sb.append(e.getKey()).append("=").append(e.getValue().get(0).replace("\"", "")).append(" ");
        }
        sb.append("\"");
        logger.info("extra vars:{}", sb.toString());

        return sb.toString();
    }

    private String makeParam(final Map<String, List<String>> input) throws Exception {
        final StringBuilder sb = new StringBuilder();
        for (List<String> value : input.values()) {
            // TODO find a better way to fix this
            sb.append(value.get(0).replace("\"", "")).append(" ");
        }
        return sb.toString();
    }

    private Exec.Result cleanUp(final String path, final boolean isTar) {
        final String[] cmds = { CustomServicesConstants.REMOVE, CustomServicesConstants.REMOVE_OPTION,
                CustomServicesConstants.PATH + path };
        Exec.Result result = Exec.exec(timeout, cmds);
        if (isTar) {
            String[] rmDir = { CustomServicesConstants.REMOVE, CustomServicesConstants.REMOVE_OPTION,
                    CustomServicesConstants.PATH + FilenameUtils.removeExtension(path) };

            if (!Exec.exec(timeout, rmDir).exitedNormally())
                logger.error("Failed to remove directory:{}", FilenameUtils.removeExtension(path));
        }

        if (!result.exitedNormally())
            logger.error("Failed to cleanup:{} error:{}", path, result.getStdError());

        // cleanup order context dir
        final String[] cmd = { CustomServicesConstants.REMOVE, CustomServicesConstants.REMOVE_OPTION, orderDir };

        return Exec.exec(timeout, cmd);
    }
}
