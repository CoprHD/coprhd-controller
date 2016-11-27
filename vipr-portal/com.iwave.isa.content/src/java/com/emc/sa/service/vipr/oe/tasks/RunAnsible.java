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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument.Step;
import com.emc.storageos.services.util.Exec;

public class RunAnsible  extends ViPRExecutionTask<OrchestrationTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunAnsible.class);

    private final Step step;
    private final Map<String, List<String>> input;

    public RunAnsible(final Step step, final Map<String, List<String>> input)
    {
        this.step = step;
        this.input = input;
    }

    @Override
    public OrchestrationTaskResult executeTask() throws Exception {

        ExecutionUtils.currentContext().logInfo("Starting Ansible Workflow step:{} of type:{}", step.getId(), step.getType());

        final String extraVars = makeExtraArg(input);

        final OrchestrationServiceConstants.StepType type = OrchestrationServiceConstants.StepType.fromString(step.getType());
        final Exec.Result result;
        switch (type) {
            case SHELL_SCRIPT:
                result = executeCmd(OrchestrationServiceConstants.DATA_PATH+step.getOperation(), extraVars);
                break;
            case LOCAL_ANSIBLE:
                final Exec.Result result1 = UntarPackage(step.getAnsiblePackage());
                if (result1.execFailed()) {
                    logger.error("Failed to Untar package: %s", step.getAnsiblePackage());

                    return null;
                }

                result = executeCmd(OrchestrationServiceConstants.DATA_PATH+
                        FilenameUtils.removeExtension(step.getAnsiblePackage())+"/"+step.getOperation(), extraVars);
                break;
            case REMOTE_ANSIBLE:
                //TODO impl remote exec
                result = executeCmd(null, null);;
                break;
            default:
                logger.error("Ansible Operation type:{} not supported", type);

                throw new IllegalStateException("Unsupported Operation");
        }

        ExecutionUtils.currentContext().logInfo("Done Executing Ansible Workflow Step:{}", step.getId());

        if (result == null)
            return null;

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

    /**
     * Ansible extra Argument format:
     * --extra_vars "key1=value1 key2=value2"
     *
     * @param input
     * @return
     * @throws Exception
     */
    private String makeExtraArg(final Map<String, List<String>> input) throws Exception {
        String extra_vars = "\"";
        Set s = input.keySet();

        Iterator it = s.iterator();
        while(it.hasNext())
        {
            String key = it.next().toString();
            String value = input.get(key).get(0);
            extra_vars = extra_vars + key + "=" +value;
        }
        extra_vars = extra_vars + "\"";
        logger.debug("extra vars:{}", extra_vars);

        return extra_vars;
    }
}
