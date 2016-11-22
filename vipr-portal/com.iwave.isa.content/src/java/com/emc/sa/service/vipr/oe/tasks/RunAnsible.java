package com.emc.sa.service.vipr.oe.tasks;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationService;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument;
import com.emc.storageos.services.util.Exec;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RunAnsible  extends ViPRExecutionTask<OrchestrationTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunAnsible.class);

    private final OrchestrationWorkflowDocument.Step step;
    private final Map<String, List<String>> input;

    public RunAnsible(final OrchestrationWorkflowDocument.Step step, final Map<String, List<String>> input)
    {
        this.step = step;
        this.input = input;
    }

    //ansible-playbook -i "localhost" release.yml --extra-vars "version=1.23.45 other_variable=foo"
    @Override
    public OrchestrationTaskResult executeTask() throws Exception {

        ExecutionUtils.currentContext().logInfo("Starting Ansible Workflow step:{} of type:{}", step.getId(), step.getType());

        final String extra_vars = makeExtraArg(input);

        final OrchestrationServiceConstants.StepType type = OrchestrationServiceConstants.StepType.fromString(step.getType());
        Exec.Result result = null;
        switch (type) {
            case SHELL_SCRIPT:
                result = executeCmd(extra_vars, OrchestrationServiceConstants.DATA_PATH+step.getOperation());
                break;
            case LOCAL_ANSIBLE:
                result = UntarPackage(step.getPath());
                if (result.execFailed()) {
                    ExecutionUtils.currentContext().logInfo("Failed to Untar package: %s", step.getPath());

                    return null;
                }

                result = executeCmd(extra_vars, OrchestrationServiceConstants.DATA_PATH+step.getPath()+"/"+step.getOperation());
                break;
            case REMOTE_ANSIBLE:
                break;
            default:
                logger.error("Ansible Operation type:{} not supported", type);

                throw new IllegalStateException("Unsupported Operation");
        }

        ExecutionUtils.currentContext().logInfo("Done Executing Ansible Workflow Step:{}", step.getId());

        if (result == null) {
            ExecutionUtils.currentContext().logInfo("Failed to Execute playbook %s", "");

            return null;
        }

        return new OrchestrationTaskResult(result.getStdOutput(), result.getStdError(), result.getExitValue());
    }

    private Exec.Result executeCmd(final String path, final String extra_vars) {
        final String[] cmds = {OrchestrationServiceConstants.ANSIBLE_LOCAL_BIN, path,
                         OrchestrationServiceConstants.EXTRA_VARS + extra_vars};

        return Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
    }

    private Exec.Result UntarPackage(final String tarFile) throws IOException
    {
        //TODO Get packge from ViPR DB
        final String[] cmds = {OrchestrationServiceConstants.UNTAR, tarFile , "-c",
                            OrchestrationServiceConstants.DATA_PATH+tarFile};

        return Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
    }

    private String makeExtraArg(Map<String, List<String>> input) throws Exception {
        String extra_vars = "\"";
        Set s = input.keySet();

        Iterator it = s.iterator();
        while(it.hasNext())
        {
            String key = it.next().toString();
            String value = input.get(key).get(0);
            logger.info("key:{} value:{}", key, value);
            extra_vars = extra_vars + key + "=" +value;
        }
        extra_vars = extra_vars + "\"";
        logger.info("extra vars:{}", extra_vars);

        return extra_vars;
    }
}
