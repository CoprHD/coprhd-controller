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
                result = executeCmd(OrchestrationServiceConstants.PATH + step.getOperation(), extraVars);
                cleanUp(step.getOperation(), false);
                break;
            case LOCAL_ANSIBLE:
                final Exec.Result untarResult = untarPackage(step.getOperation());
                if (!untarResult.exitedNormally()) {
                    logger.error("Failed to Untar package. Error:{}", untarResult.getStdError());
                    cleanUp(step.getOperation(), true);
                    return null;
                }

                //TODO Hard coded the playbook name. Get it from primitive
                result = executeCmd(OrchestrationServiceConstants.PATH +
                        FilenameUtils.removeExtension(step.getOperation()) + "/" + "helloworld.yml", extraVars);
                cleanUp(step.getOperation(), true);
                break;
            case REMOTE_ANSIBLE:
                logger.info("Executing Remote ansible");
                //String ip = input.get("remoteIp").get(0);//"10.247.66.88,";
                String ip = "10.247.66.88"; //Get from Param/JSON
		String user = "root";
                String remotePlaybook = "/data/hello.yml"; //TODO Get from primitive remote playbook
                String remoteBin = "/usr/bin/ansible-playbook";//Get from Param/JSON
                result = executeRemoteCmd(user, ip, remotePlaybook, remoteBin, extraVars);

                logger.info("Result: out:{} err:{} exitVal:{}", result.getStdOutput(), result.getStdError(), result.getExitValue());
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

    ///usr/bin/ssh", "root@10.247.66.88", "/usr/bin/ansible-playbook /data/hello.yml --extra-var \" \"
    private Exec.Result executeRemoteCmd(final String user, final String ip, final String path, final String binPath, final String extraVars) {
        logger.info("executing remote ansi ip:{} path:{} bin:{} extravar:{} user:{}", ip, path, binPath, extraVars, user);
	String commands = binPath+" "+path+" --extra-var "+extraVars;
        final String[] cmds = {"/usr/bin/ssh", user+"@"+ip, commands};
        
	return Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
    }

    private Exec.Result executeCmd(final String path, final String extraVars) {
        final String[] cmds = {OrchestrationServiceConstants.ANSIBLE_LOCAL_BIN, path,
                OrchestrationServiceConstants.EXTRA_VARS, extraVars};

        return Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
    }

    private Exec.Result untarPackage(final String tarFile) throws IOException {
        final String[] cmds = {OrchestrationServiceConstants.UNTAR, OrchestrationServiceConstants.UNTAR_OPTION, OrchestrationServiceConstants.PATH + tarFile,
                "-C", OrchestrationServiceConstants.PATH};
        Exec.Result result = Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);

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
        final String[] cmds = {OrchestrationServiceConstants.REMOVE, OrchestrationServiceConstants.REMOVE_OPTION, OrchestrationServiceConstants.PATH + path};
        Exec.Result result = Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
        if (isTar) {
            String[] rmDir = {OrchestrationServiceConstants.REMOVE, OrchestrationServiceConstants.REMOVE_OPTION, OrchestrationServiceConstants.PATH +
                    FilenameUtils.removeExtension(path)};
            if (!Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, rmDir).exitedNormally())
                logger.error("Failed to remove directory:{}", FilenameUtils.removeExtension(path));
        }

        if (!result.exitedNormally())
            logger.error("Failed to cleanup:{} error:{}", path, result.getStdError());

        return result;
    }
}

