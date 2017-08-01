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

package com.emc.sa.service.vipr.customservices;

import java.io.File;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.CustomServicesWorkflowManager;
import com.emc.sa.catalog.primitives.CustomServicesViprPrimitiveDAO;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.customservices.tasks.CustomServicesExecutors;
import com.emc.sa.service.vipr.customservices.tasks.CustomServicesTaskResult;
import com.emc.sa.service.vipr.customservices.tasks.MakeCustomServicesExecutor;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesConstants.InputType;
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.google.common.collect.ImmutableMap;

@Service("CustomServicesService")
public class CustomServicesService extends ViPRService {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesService.class);
    // <StepId, {"key" : "values...", "key" : "values ..."} ...>

    private Map<String, Object> params;

    @Autowired
    private DbClient dbClient;
    @Autowired
    private CustomServicesExecutors executor;
    @Autowired
    private CustomServicesViprPrimitiveDAO customServicesViprDao;
    @Autowired
    private CustomServicesWorkflowManager customServicesWorkflowManager;

    protected String decrypt(final String value) {
        if (StringUtils.isNotBlank(value)) {
            try {
                return getEncryptionProvider().decrypt(Base64.decodeBase64(value));
            } catch (RuntimeException e) {
                throw new IllegalStateException(String.format("Failed to decrypt value: %s", e.getMessage()), e);
            }
        }
        return value;
    }

    @Override
    public void precheck() throws Exception {
        // get input params from order form
        params = ExecutionUtils.currentContext().getParameters();
    }

    @Override
    public void execute() throws Exception {
        ExecutionUtils.currentContext().logInfo("customServicesService.title");
        final String orderDir = String.format("%s%s/", CustomServicesConstants.ORDER_DIR_PATH,
                ExecutionUtils.currentContext().getOrder().getOrderNumber());
        try {

            final int loopCount = getLoopCount();
            for (int i = 0; i < loopCount; i++) {
                wfExecutor(i);
            }
            ExecutionUtils.currentContext().logInfo("customServicesService.successStatus");
        } catch (final Exception e) {
            ExecutionUtils.currentContext().logError("customServicesService.failedStatus");

            throw e;
        } finally {
            orderDirCleanup(orderDir);
        }
    }

    private int getLoopCount() throws Exception {

        if (isLoop()) {
            ImmutableMap<String, Step> steps = getStepHash();
            for (Map.Entry<String, Step> stepEntry : steps.entrySet()) {

                logger.info("step key:{}", stepEntry.getKey());
                final Step step = stepEntry.getValue();
                logger.info("stepid:{}", step.getId());
                if (step.getInputGroups() == null) {
                    logger.info("it is null");
                }
                if ( step.getInputGroups() == null) {
                    logger.info("it is start or end so continue");
                    continue;
                }
                for (final CustomServicesWorkflowDocument.InputGroup inputGroup : step.getInputGroups().values()) {
                    for (final Input value : inputGroup.getInputGroup()) {

                        switch (InputType.fromString(value.getType())) {
                            case FROM_USER:
                            case FROM_USER_MULTI:
                            case ASSET_OPTION_SINGLE://todo assetoption multi
                                final String name = params.get(value.getFriendlyName()).toString(); //todo handle pw
                                if (!StringUtils.isEmpty(value.getTableName())) {
                                    logger.info("There is a WF loop");
                                    String[] size = name.replace("\"", "").split(",");
                                    logger.info("size is:{}", size.length);
                                    return size.length;
                                }
                        }
                    }
                }
            }
        }
        return 1;
    }

    private boolean isLoop() throws Exception {
        final CustomServicesWorkflowDocument obj = getwfDocument();
        final Map<String, String> attributes = obj.getAttributes();
        if (attributes == null) {
            return false;
        }
        final String isLoop = attributes.get("RunAsLoop");
        if (StringUtils.isEmpty(isLoop)) {
            return false;
        }

        logger.info("There might be a loop. isLoop:{}", isLoop);
        return (isLoop.equals("true") ?  true :  false) ;
    }

    /**
     * Method to parse Workflow Definition JSON
     *
     * @throws Exception
     */
    public void wfExecutor(final int loopCount) throws Exception {


        Map<String, Map<String, List<String>>> outputPerStep = new HashMap<String, Map<String, List<String>>>();
        Map<String, Map<String, List<String>>> inputPerStep = new HashMap<String, Map<String, List<String>>>();

        logger.info("CS: Parsing Workflow Definition");

        final ImmutableMap<String, Step> stepsHash = getStepHash();

        Step step = stepsHash.get(StepType.START.toString());
        String next = step.getNext().getDefaultStep();
        long timeout = System.currentTimeMillis();

        while (next != null && !next.equals(StepType.END.toString())) {
            step = stepsHash.get(next);

            ExecutionUtils.currentContext().logInfo("customServicesService.stepStatus", step.getId(), step.getFriendlyName(), step.getType());

            final Map<String, List<String>> inputs = updateInputPerStep(step, inputPerStep, outputPerStep, loopCount);
            inputPerStep.put(step.getId(), inputs);

            CustomServicesTaskResult res = null;
            try {
                final MakeCustomServicesExecutor task = executor.get(step.getType());
                task.setParam(getClient().getRestClient());

                if (step.getAttributes().getPolling()) {
                    final long polltimeout = System.currentTimeMillis();
                    while (true) {
                        logger.info("call poll executor");
                        res = ViPRExecutionUtils.execute(task.makeCustomServicesExecutor(inputPerStep.get(step.getId()), step));

                        final Map<String, List<String>> out = updateOutputPerStep(step, res);
                        logger.info("update non iter out");
                        outputPerStep.put(step.getId(), out);
                        if (isPollingSuccessful(step, out, polltimeout)) {
                            break;
                        }
                    }
                } else {
                    logger.info("call non poll executor");
                    res = ViPRExecutionUtils.execute(task.makeCustomServicesExecutor(inputPerStep.get(step.getId()), step));

                    final Map<String, List<String>> out = updateOutputPerStep(step, res);
                    logger.info("update non iter out");
                    outputPerStep.put(step.getId(), out);
                }

                next = getNext(true, res, step);

            } catch (final Exception e) {
                logger.warn(
                        "failed to execute step step Id:{}", step.getId() + "Try to get failure path. Exception Received:", e);

                next = getNext(false, null, step);
            }
            if (next == null) {
                ExecutionUtils.currentContext().logError("customServicesService.logStatus", "Step Id: " + step.getId() + "\t Step Name: " + step.getFriendlyName()
                + "Failed. Failing the Workflow");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Workflow Execution failed");
            }
            if ((System.currentTimeMillis() - timeout) > CustomServicesConstants.WORKFLOW_TIMEOUT) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Operation Timed out");
            }
        }
    }

    private boolean isPollingSuccessful(final Step step, final Map<String, List<String>> values, final long polltimeout) throws Exception {

        if (checkPolling(step.getAttributes().getSuccessCondition(), values)) {
            logger.info("Polling step is successful");
            return true;
        }

        if (checkPolling(step.getAttributes().getFailureCondition(), values)) {
            logger.info("Polling step failed");
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Polling failed");
        }

        TimeUnit.MINUTES.sleep(step.getAttributes().getInterval());

        if ((System.currentTimeMillis() - polltimeout) > step.getAttributes().getTimeout()) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Operation Timed out");
        }

        return false;
    }

    private boolean checkPolling(final List<CustomServicesWorkflowDocument.Condition> success, Map<String, List<String>> values) {
        for (CustomServicesWorkflowDocument.Condition cond : success) {
            String key = cond.getOutputName();
            List<String> out = values.get(key);
            if (cond.getCheckValue().equals(out.get(0))) {
                return true;
            }
        }

        return false;
    }

    private CustomServicesWorkflowDocument getwfDocument() throws Exception {
        final String raw;

        raw = ExecutionUtils.currentContext().getOrder().getWorkflowDocument();

        if (null == raw) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Invalid custom service.  Workflow document cannot be null");
        }
        final CustomServicesWorkflowDocument obj = WorkflowHelper.toWorkflowDocument(raw);

        final List<CustomServicesWorkflow> wfs = customServicesWorkflowManager.getByNameOrId(obj.getName());
        if (wfs == null  || wfs.isEmpty() || wfs.size() > 1) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Workflow list is null or empty or more than one workflow per Workflow name:" + obj.getName());
        }
        if (wfs.get(0) == null || StringUtils.isEmpty(wfs.get(0).getState())) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Workflow state is null or empty for workflow:" + obj.getName());
        }

        if(wfs.get(0).getState().equals(CustomServicesWorkflow.CustomServicesWorkflowStatus.NONE.toString()) ||
                wfs.get(0).getState().equals(CustomServicesWorkflow.CustomServicesWorkflowStatus.INVALID.toString())) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Workflow state is not valid. Cannot run workflow" + obj.getName() + "State:" + wfs.get(0).getState());
        }

        return obj;
    }

    private ImmutableMap<String, Step> getStepHash() throws Exception {

        logger.info("in stephash");
        final CustomServicesWorkflowDocument obj = getwfDocument();
        final List<Step> steps = obj.getSteps();
        final ImmutableMap.Builder<String, Step> builder = ImmutableMap.builder();
        for (final Step step : steps) {
            logger.info("stepid:{}", step.getId());
            builder.put(step.getId(), step);
        }
        final ImmutableMap<String, Step> stepsHash = builder.build();

        ExecutionUtils.currentContext().logInfo("customServicesService.status", obj.getName());

        return stepsHash;
    }

    private void orderDirCleanup(final String orderDir) {
        try {
            final File file = new File(orderDir);
            if (file.exists()) {
                final String[] cmd = { CustomServicesConstants.REMOVE, CustomServicesConstants.REMOVE_OPTION, orderDir };
                Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmd);
            }
        } catch (final Exception e) {
            logger.error("Failed to cleanup OrderDir directory", e);
        }
    }

    private String getNext(final boolean status, final CustomServicesTaskResult result, final Step step) {
        if (status) {
            ExecutionUtils.currentContext().logInfo("customServicesService.stepSuccessStatus", step.getId(), step.getFriendlyName(), result.getReturnCode());

            return step.getNext().getDefaultStep();
        }

        if (result!= null) {
            ExecutionUtils.currentContext().logError("customServicesService.stepFailedStatus", step.getId(), step.getFriendlyName(),
            result.getOut(), result.getErr(), result.getReturnCode());

        } else {
            ExecutionUtils.currentContext()
                    .logError("customServicesService.stepFailedWithoutStatus", step.getId(), step.getFriendlyName());
        }

        return step.getNext().getFailedStep();
    }

    private boolean updateInput(final Step step) {
        if (step.getType().equals(StepType.WORKFLOW.toString()) || step.getInputGroups() == null) {
            return false;
        }

        return true;
    }

    /**
     * Method to collect all required inputs per step for execution
     * Case :
     * SingleUserInput , InputFromUserMulti and AssetOptionSingle
     * The order form sends the value as String with double quotes for each value, we remove the quotes
     * the reason for splitting by ',' is, for table type cases, where the input name is part of a table and hence
     * we should store it as a list of values
     * Eg., of single input inside a table: say the name is "volume"
     * from order context value for name (which is part of table will be) = ""vol1","vol2","vol3""
     * These will be stored in the inputs Map as follows:
     * input[key] = "volumes"
     * input[value][0]=vol1
     * input[value][1]=vol2
     * input[value][2]=vol3
     *
     * Case 2: AssetOptionMulti
     * The array can be passed by itself or as part of a table
     * Since the order form sends the value as String with double quotes for each value, we remove the quotes
     * the reason for splitting by "," is for table type cases, where the input name is part of a table and hence we
     * should store it as a list of values
     * Eg., of array input without table:
     * arrayInputWithouttable = ""vol1","vol2","vol3""
     * These will be stored in the inputs Map as follows:
     * input[key] = "volumes"
     * input[value][0]=vol1,vol2,vol3
     *
     * Eg., of array input with table:
     * arrayInputWithtable = ""1,2,3","4","14,15","24,25,26"" ie., in a table (complex structure), the array input
     * is clubbed by row and separated by commans
     *
     * These will be stored in the inputs Map as follows:
     * input[key] = "volumes"
     * input[value][0]=1,2,3
     * input[value][1]=4
     * input[value][2]=14,15
     * input[value][3]=24,25,26
     *
     * @param step It is the JSON Object of Step
     */
    private Map<String, List<String>> updateInputPerStep(final Step step, final Map<String, Map<String, List<String>>> inputPerStep, Map<String, Map<String, List<String>>> outputPerStep, final int loopCount) throws Exception {

        final Map<String, List<String>> inputs = new HashMap<String, List<String>>();

        if (!updateInput(step)) {
            return inputs;
        }

        for (final CustomServicesWorkflowDocument.InputGroup inputGroup : step.getInputGroups().values()) {
            for (final Input value : inputGroup.getInputGroup()) {
                final String name = value.getName();
                String friendlyName = value.getFriendlyName();
                if(friendlyName != null){
                    friendlyName = friendlyName.replaceAll(CustomServicesConstants.SPACES_REGEX,StringUtils.EMPTY);
                }

                if (StringUtils.isEmpty(value.getType())) {
                    continue;
                }

                switch (InputType.fromString(value.getType())) {
                    case DISABLED:
                        inputs.put(name, Arrays.asList(""));
                        break;
                    case FROM_USER:
                    case FROM_USER_MULTI:
                    case ASSET_OPTION_SINGLE:
                        if (params.get(friendlyName) != null && !StringUtils.isEmpty(params.get(friendlyName).toString())) {
                            final String param;

                            if (!StringUtils.isEmpty(value.getInputFieldType()) && 
				                value.getInputFieldType().toUpperCase().equals(CustomServicesConstants.InputFieldType.PASSWORD.toString())) {
                                param = decrypt(params.get(friendlyName).toString());

                            } else {
                                param = params.get(friendlyName).toString();
                            }
                            if (StringUtils.isEmpty(value.getTableName())) {
                                inputs.put(name, Arrays.asList(param.replace("\"", "")));
                            } else {
                                if (isLoop()) {
                                    final String[] arr = param.replace("\"", "").split(",");
                                    inputs.put(name, Arrays.asList(arr[loopCount]));
                                } else {
                                    inputs.put(name, Arrays.asList(param.replace("\"", "").split(",")));
                                }
                            }
                        } else {
                            if (value.getDefaultValue() != null) {
                                inputs.put(name, Arrays.asList(value.getDefaultValue()));
                            } else {
                                inputs.put(name, Arrays.asList(""));
                            }
                        }
                        break;
                    case ASSET_OPTION_MULTI:
                        if (params.get(friendlyName) != null && !StringUtils.isEmpty(params.get(friendlyName).toString())) {
                            final String assetVal = params.get(friendlyName).toString();
                            final String[] rowsVal = assetVal.split("\",\"");
                            if (isLoop()) {
                                inputs.put(name, Arrays.asList(rowsVal[loopCount]));
                            }

                           // final List<String> arrayInput;

                            logger.info("multi assetoption:{}", params.get(friendlyName));
                            if (!StringUtils.isEmpty(value.getTableName())) {
                                if (isLoop()) {
                                    final String[] arr = params.get(friendlyName).toString().replace("\"", "").split(",");
                                    inputs.put(name, Arrays.asList(arr[loopCount]));
                                } else {
                                    inputs.put(name, Arrays.asList(params.get(friendlyName).toString().replace("\"", "").split(",")));
                                }
                            } else {
                                inputs.put(name, Arrays.asList(params.get(friendlyName).toString()));
                            }

                           /* int index = 0;
                            for (String eachVal : arrayInput) {
                                arrayInput.set(index++, eachVal.replace("\"", ""));
                            }
                            inputs.put(name, arrayInput);*/
                        } else {
                            if (value.getDefaultValue() != null) {
                                // The default value is copied only for the first index
                                // in case of table type, it is not evident how many times the default value need to be copied.
                                inputs.put(name, Arrays.asList(value.getDefaultValue()));
                            } else {
                                inputs.put(name, Arrays.asList(""));
                            }
                        }
                        break;

                    case FROM_STEP_INPUT:
                    case FROM_STEP_OUTPUT: {
                        final String[] paramVal = value.getValue().split("\\.", 2);
                        final String stepId = paramVal[CustomServicesConstants.STEP_ID];
                        final String attribute = paramVal[CustomServicesConstants.INPUT_FIELD];

                        final Map<String, List<String>> stepInput;
                        boolean fromStepOutput = true;
                        if (value.getType().equals(InputType.FROM_STEP_INPUT.toString())) {
                            stepInput = inputPerStep.get(stepId);
                            fromStepOutput = false;
                        } else {
                            stepInput = outputPerStep.get(stepId);
                        }
                        if (stepInput != null && stepInput.get(attribute) != null) {
                            if (fromStepOutput && StringUtils.isEmpty(value.getTableName())) {
                                inputs.put(name, Arrays.asList(String.join(", ", stepInput.get(attribute)).replace("\"", "")));
                                break;
                            } else {
                                inputs.put(name, stepInput.get(attribute));
                                break;
                            }
                        } else {
                            if (value.getRequired()) {
                                throw InternalServerErrorException.internalServerErrors
                                        .customServiceExecutionFailed("Value mapped is null : " + value.getValue());
                            }
                        }

                        if (value.getDefaultValue() != null) {
                            inputs.put(name, Arrays.asList(value.getDefaultValue()));
                            break;
                        }

                        if (value.getRequired()) {
                            throw InternalServerErrorException.internalServerErrors
                                    .customServiceExecutionFailed("Value mapped is null : " + value.getValue());
                        }

                        break;
                    }
                    default:
                        throw InternalServerErrorException.internalServerErrors
                                .customServiceExecutionFailed("Invalid input type:" + value.getType());
                }
            }
        }

        return inputs;
    }

    /**
     * Parse REST Response and get output values as specified by the user in the workflow definition
     * <p/>
     * Example of Supported output variable formats:
     * "state"
     * "task.resource.id"
     * "task.state"
     *
     * @param step
     * @param res
     */

    private final Map<String, List<String>>  updateOutputPerStep(final Step step, final CustomServicesTaskResult res) {
        final Map<String, List<String>> out = new HashMap<String, List<String>>();

        //set the default result.
        out.put(CustomServicesConstants.OPERATION_OUTPUT, Arrays.asList(res.getOut()));
        out.put(CustomServicesConstants.OPERATION_ERROR, Arrays.asList(res.getErr()));
        out.put(CustomServicesConstants.OPERATION_RETURNCODE, Arrays.asList(String.valueOf(res.getReturnCode())));
        out.putAll(res.getOutput());

        return out;
    }
}
