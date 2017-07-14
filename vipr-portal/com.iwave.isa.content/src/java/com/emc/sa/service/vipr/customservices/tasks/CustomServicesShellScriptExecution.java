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

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptResource;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public class CustomServicesShellScriptExecution extends ViPRExecutionTask<CustomServicesTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesShellScriptExecution.class);
    private final CustomServicesWorkflowDocument.Step step;
    private final Map<String, List<String>> input;
    private  String orderDir = String.format("%s%s/", CustomServicesConstants.ORDER_DIR_PATH,
            ExecutionUtils.currentContext().getOrder().getOrderNumber());
    private String chrootOrderDir = String.format("%s%s/", CustomServicesConstants.CHROOT_ORDER_DIR_PATH,
            ExecutionUtils.currentContext().getOrder().getOrderNumber());
    private final long timeout;

    private final DbClient dbClient;

    private int iterCount;

    public CustomServicesShellScriptExecution(final Map<String, List<String>> input,final CustomServicesWorkflowDocument.Step step,final DbClient dbClient, final int iterCount) {
        this.input = input;
        this.step = step;
        if (step.getAttributes() == null || step.getAttributes().getTimeout() == -1) {
            this.timeout = CustomServicesConstants.OPERATION_TIMEOUT;
        } else {
            this.timeout = step.getAttributes().getTimeout();
        }
        this.dbClient = dbClient;
        this.iterCount = iterCount;
        provideDetailArgs(step.getId(), step.getFriendlyName());

    }


    @Override
    public CustomServicesTaskResult executeTask() throws Exception {
        ExecutionUtils.currentContext().logInfo("customServicesScriptExecution.statusInfo", step.getId(), step.getFriendlyName());
        final Exec.Result result;
        try {
            final URI scriptid = step.getOperation();

            logger.debug("CS: Get the resources for script execution");
            final CustomServicesDBScriptPrimitive primitive = dbClient.queryObject(CustomServicesDBScriptPrimitive.class, scriptid);
            if (null == primitive) {
                logger.error("Error retrieving script primitive from DB. {} not found in DB", scriptid);
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "Error retrieving script primitive from DB.");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed(scriptid + " not found in DB");
            }

            final CustomServicesDBScriptResource script = dbClient.queryObject(CustomServicesDBScriptResource.class,
                    primitive.getResource());
            if (null == script) {
                logger.error("Error retrieving resource for the script primitive from DB. {} not found in DB",
                        primitive.getResource());

                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "Error retrieving resource for the script primitive from DB.");
                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed(primitive.getResource() + " not found in DB");
            }

            logger.debug("CS: Execute primitive:{} with script:{}", primitive.getId(), script.getId());

            final String scriptFileName = String.format("%s%s.sh", orderDir, URIUtil.parseUUIDFromURI(scriptid).replace("-", ""));

            final byte[] bytes = Base64.decodeBase64(script.getResource());
            AnsibleHelper.writeResourceToFile(bytes, scriptFileName);

            final String exeScriptFileName = String.format("%s%s.sh", chrootOrderDir, URIUtil.parseUUIDFromURI(scriptid).replace("-", ""));
            result = executeCmd(exeScriptFileName);

        } catch (final Exception e) {
            logger.error("CS: Could not execute shell script step:{}. Exception:", step.getId(), e);
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                    "Could not execute shell script step"+e);

            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }

        ExecutionUtils.currentContext().logInfo("customServicesScriptExecution.doneInfo", step.getId(), step.getFriendlyName());

        if (result == null) {
            logger.error("CS: Script Execution result is null for step:{}", step.getId());
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                    " Script Execution result is null");
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Script/Ansible execution Failed");
        }

        logger.info("CustomScript Execution result:output{} error{} exitValue:{} ", result.getStdOutput(), result.getStdError(),
                result.getExitValue());

        if (result.getExitValue()!=0) {
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                    "Shell Script execution Failed. ReturnCode:" + result.getExitValue());
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Shell Script execution Failed");
        }

        return new CustomServicesScriptTaskResult(AnsibleHelper.parseOut(result.getStdOutput()), result.getStdOutput(), result.getStdError(), result.getExitValue());
    }

    // Execute Shell Script resource

    // chroot / bash -c 'arg1=5 arg2=3 sh +x /root/t.sh'
    private Exec.Result executeCmd(final String shellScript) throws Exception {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(CustomServicesConstants.SHELL_BIN, shellScript);
        cmd.setChrootCmd(CustomServicesConstants.CHROOT_CMD);
        cmd.setShellArgs(makeParam(input));
        final String[] cmds = cmd.build();

        logger.info("cmd to exec:{}", Arrays.toString(cmds));
        return Exec.sudo(new File(orderDir), timeout, null, new HashMap<String,String>(), cmds);
    }

    //Format: arg1=5 arg2=3
    private String makeParam(final Map<String, List<String>> input) throws Exception {
        final StringBuilder str = new StringBuilder();
        if (input == null) {
            return null;
        }

        for(Map.Entry<String, List<String>> e : input.entrySet()) {

            if (StringUtils.isEmpty(e.getKey()) || e.getValue().isEmpty()) {
                continue;
            }

            logger.info("make the arguments");
            final List<String> listVal = e.getValue();
            final StringBuilder sb = new StringBuilder();
            sb.append("\"");
            logger.info("val is listVal.get(iterCount)");
            sb.append(listVal.get(iterCount).replace("\"", ""));
            sb.append("\"");
            /*String prefix = "";
            for (final String val : listVal) {
                sb.append(prefix);
                prefix = ",";
                sb.append(val.replace("\"", ""));
            }
            sb.append("\"");*/
            str.append(e.getKey()).append("=").append(sb.toString().trim()).append(" ");
        }

        logger.info("CS: Shell arguments:{}", str.toString());

        return str.toString();
    }
}
