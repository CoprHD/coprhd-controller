package com.emc.sa.service.vipr.customservices.tasks;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesPrimitive;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class CustomServicesRemoteAnsibleExecution extends ViPRExecutionTask<CustomServicesTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesRemoteAnsibleExecution.class);
    private final CustomServicesWorkflowDocument.Step step;
    private final Map<String, List<String>> input;
    private  String orderDir;
    private final long timeout;

    @Autowired
    private DbClient dbClient;

    public CustomServicesRemoteAnsibleExecution(final Map<String, List<String>> input, CustomServicesWorkflowDocument.Step step) {
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

        final CustomServicesPrimitive.StepType type = CustomServicesPrimitive.StepType.fromString(step.getType());

        final Exec.Result result;
        try {
                    result = executeRemoteCmd(AnsibleHelper.makeExtraArg(input));

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



    // Execute Ansible playbook on remote node. Playbook is also in remote node
    private Exec.Result executeRemoteCmd(final String extraVars) {
        final Map<String, CustomServicesWorkflowDocument.InputGroup> inputType = step.getInputGroups();
        if (inputType == null) {
            return null;
        }

        final AnsibleCommandLine cmd = new AnsibleCommandLine(
                AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_BIN, input),
                AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_PLAYBOOK, input));

        final String[] cmds = cmd.setSsh(CustomServicesConstants.SHELL_LOCAL_BIN)
                .setUserAndIp(AnsibleHelper.getOptions(CustomServicesConstants.REMOTE_USER, input),
                        AnsibleHelper.getOptions(CustomServicesConstants.REMOTE_NODE, input))
                .setHostFile(AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_HOST_FILE, input))
                .setUser(AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_USER, input))
                .setCommandLine(AnsibleHelper.getOptions(CustomServicesConstants.ANSIBLE_COMMAND_LINE, input))
                .setExtraVars(extraVars)
                .build();

        return Exec.exec(timeout, cmds);
    }
}

