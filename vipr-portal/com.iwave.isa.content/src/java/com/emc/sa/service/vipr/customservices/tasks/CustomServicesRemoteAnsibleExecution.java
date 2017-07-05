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

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBRemoteAnsiblePrimitive;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.iwave.ext.command.Command;
import com.iwave.ext.command.CommandOutput;
import com.iwave.utility.ssh.SSHCommandExecutor;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

public class CustomServicesRemoteAnsibleExecution extends ViPRExecutionTask<CustomServicesTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesRemoteAnsibleExecution.class);
    private final CustomServicesWorkflowDocument.Step step;
    private final Map<String, List<String>> input;
    private final long timeout;
    private final DbClient dbClient;


    public CustomServicesRemoteAnsibleExecution(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step,final DbClient dbClient) {
        this.input = input;
        this.step = step;
        if (step.getAttributes() == null || step.getAttributes().getTimeout() == -1) {
            this.timeout = Exec.DEFAULT_CMD_TIMEOUT;
        } else {
            this.timeout = step.getAttributes().getTimeout();
        }
        this.dbClient = dbClient;
        provideDetailArgs(step.getId(), step.getFriendlyName());
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {

        ExecutionUtils.currentContext().logInfo("customServicesScriptExecution.statusInfo", step.getId(), step.getFriendlyName());

        final CommandOutput result;
        try {
            final CustomServicesDBRemoteAnsiblePrimitive primitive = dbClient.queryObject(CustomServicesDBRemoteAnsiblePrimitive.class,
                    step.getOperation());
            if (null == primitive) {
                logger.error("Error retrieving the ansible primitive from DB. {} not found in DB", step.getOperation());
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "\"Error retrieving the Remote Ansible primitive from DB.");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed(step.getOperation() + " not found in DB");
            }

            result = executeRemoteCmd(AnsibleHelper.makeExtraArg(input, step), primitive);

        } catch (final Exception e) {
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                    "Custom Service Task Failed" + e);
            logger.error("Exception:", e);
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }

        ExecutionUtils.currentContext().logInfo("customServicesScriptExecution.doneInfo", step.getId(), step.getFriendlyName());

        if (result == null) {
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                    "Remote Ansible execution Failed");
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Remote Ansible execution Failed");
        }

        logger.info("CustomScript Execution result:output{} error{} exitValue:{}", result.getStdout(), result.getStderr(),
                result.getExitValue());

        final String parsedOut = AnsibleHelper.parseOut(result.getStdout());
        if (!StringUtils.isEmpty(parsedOut)) {
            ExecutionUtils.currentContext().logInfo("customServicesScriptExecution.doneInfo", step.getId(), step.getFriendlyName());

            return new CustomServicesScriptTaskResult(parsedOut, result.getStdout(), result.getStderr(), result.getExitValue());             
        }
        return new CustomServicesScriptTaskResult(result.getStdout(), result.getStdout(), result.getStderr(), result.getExitValue());
    }


    // Execute Ansible playbook on remote node. Playbook is also in remote node
    private CommandOutput executeRemoteCmd(final String extraVars, final CustomServicesDBRemoteAnsiblePrimitive primitive) {
        final Map<String, CustomServicesWorkflowDocument.InputGroup> inputType = step.getInputGroups();
        if (inputType == null) {
            return null;
        }
        logger.debug("user:{} password:{}",AnsibleHelper.getOptions(
                CustomServicesConstants.REMOTE_USER, input), AnsibleHelper.getOptions(CustomServicesConstants.REMOTE_PASSWORD, input));

        final SSHCommandExecutor executor = new SSHCommandExecutor(AnsibleHelper.getOptions(CustomServicesConstants.REMOTE_NODE, input), 22, AnsibleHelper.getOptions(
                CustomServicesConstants.REMOTE_USER, input), AnsibleHelper.getOptions(CustomServicesConstants.REMOTE_PASSWORD, input));
        executor.setCommandTimeout((int)timeout);
        final Command command = new RemoteCommand(extraVars, input, primitive);
        command.setCommandExecutor(executor);
        command.execute();

        return command.getOutput();
    }

    public static final class RemoteCommand extends Command {
        public RemoteCommand(final String extraVars, final Map<String, List<String>> input, final CustomServicesDBRemoteAnsiblePrimitive primitive) {

            final AnsibleCommandLine cmd = new AnsibleCommandLine( primitive.getAttributes().get(CustomServicesConstants.ANSIBLE_BIN),
                    primitive.getAttributes().get(CustomServicesConstants.ANSIBLE_PLAYBOOK));

            final String[] cmds = cmd.setHostFile(AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_HOST_FILE, input))
                    .setCommandLine(AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_COMMAND_LINE, input))
                    .setIsRemoteAnsible(true)
                    .setExtraVars(extraVars)
                    .build();

            final String cmdToRun = StringUtils.join(cmds, ' ');
            logger.info("cmd is:{}", cmdToRun);
            setCommand(cmdToRun);
        }
    }
}
