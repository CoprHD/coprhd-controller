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
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptResource;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class CustomServicesShellScriptExecution extends ViPRExecutionTask<CustomServicesTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesShellScriptExecution.class);
    private final CustomServicesWorkflowDocument.Step step;
    private final Map<String, List<String>> input;
    private  String orderDir = String.format("%s%s/", CustomServicesConstants.ORDER_DIR_PATH,
            ExecutionUtils.currentContext().getOrder().getOrderNumber());
    private final long timeout;

    private DbClient dbClient;

    public CustomServicesShellScriptExecution(final Map<String, List<String>> input,final CustomServicesWorkflowDocument.Step step,final DbClient dbClient) {
        this.input = input;
        this.step = step;
        if (step.getAttributes() == null || step.getAttributes().getTimeout() == -1) {
            this.timeout = Exec.DEFAULT_CMD_TIMEOUT;
        } else {
            this.timeout = step.getAttributes().getTimeout();
        }
	this.dbClient = dbClient;
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {
        ExecutionUtils.currentContext().logInfo("runCustomScript.statusInfo", step.getId());
        final Exec.Result result;
        try {
            final URI scriptid = step.getOperation();
logger.info("got scriptid{}", scriptid);
            // get the resource database
            final CustomServicesDBScriptPrimitive primitive = dbClient.queryObject(CustomServicesDBScriptPrimitive.class, scriptid);
logger.info("got primitive");
            if (null == primitive) {
                logger.error("Error retrieving the script primitive from DB. {} not found in DB", scriptid);
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed(scriptid + " not found in DB");
            }

            final CustomServicesDBScriptResource script = dbClient.queryObject(CustomServicesDBScriptResource.class,
                    primitive.getResource());
logger.info("got script");
            if (null == script) {
                logger.error("Error retrieving the resource for the script primitive from DB. {} not found in DB",
                        primitive.getResource());

                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed(primitive.getResource() + " not found in DB");
            }

            // Currently, the stepId is set to random hash values in the UI. If this changes then we have to change the following to
            // generate filename with URI from step.getOperation()
            final String scriptFileName = String.format("%s%s.sh", orderDir, step.getId());
logger.info("get scriptFileName");
            final byte[] bytes = Base64.decodeBase64(script.getResource());
            writeShellScripttoFile(bytes, scriptFileName);
logger.info("write the script file");
            final String inputToScript = makeParam(input);
            logger.info("input is {}", inputToScript);

            result = executeCmd(scriptFileName, inputToScript);
logger.info("exec done");
        } catch (final Exception e) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }

        ExecutionUtils.currentContext().logInfo("runCustomScript.doneInfo", step.getId());

        if (result == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Script/Ansible execution Failed");
        }

        logger.info("CustomScript Execution result:output{} error{} exitValue:{}", result.getStdOutput(), result.getStdError(),
                result.getExitValue());

        return new CustomServicesTaskResult(result.getStdOutput(), result.getStdError(), result.getExitValue(), null);
    }


    private void writeShellScripttoFile(final byte[] bytes, final String scriptFileName){
        try (FileOutputStream fileOuputStream = new FileOutputStream(scriptFileName)) {
            fileOuputStream.write(bytes);
        } catch (final IOException e) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Creating Shell Script file failed with exception:" +
                            e.getMessage());
        }
    }

    // Execute Shell Script resource
    private Exec.Result executeCmd(final String playbook, final String extraVars) {
	logger.info("in exec");
        final AnsibleCommandLine cmd = new AnsibleCommandLine(CustomServicesConstants.SHELL_BIN, playbook);
        cmd.setShellArgs(extraVars);
	logger.info("set extra var");
        final String[] cmds = cmd.build();
	logger.info("start exec");
        return Exec.exec(timeout, cmds);
    }

    private String makeParam(final Map<String, List<String>> input) throws Exception {
        final StringBuilder sb = new StringBuilder();
        for (List<String> value : input.values()) {
            // TODO find a better way to fix this
            sb.append(value.get(0).replace("\"", "")).append(" ");
        }
        return sb.toString();
    }
}
