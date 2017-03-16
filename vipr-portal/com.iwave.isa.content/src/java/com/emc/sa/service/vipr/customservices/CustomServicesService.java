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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.customservices.gson.ViprOperation;
import com.emc.sa.service.vipr.customservices.gson.ViprTask;
import com.emc.sa.service.vipr.customservices.tasks.CustomServicesRestTaskResult;
import com.emc.sa.service.vipr.customservices.tasks.CustomServicesRESTExecution;
import com.emc.sa.service.vipr.customservices.tasks.CustomServicesTaskResult;
import com.emc.sa.service.vipr.customservices.tasks.RunViprREST;
import com.emc.sa.service.vipr.customservices.tasks.RunAnsible;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesConstants.InputType;
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

@Service("CustomServicesService")
public class CustomServicesService extends ViPRService {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesService.class);
    // <StepId, {"key" : "values...", "key" : "values ..."} ...>
    final private Map<String, Map<String, List<String>>> inputPerStep = new HashMap<String, Map<String, List<String>>>();
    final private Map<String, Map<String, List<String>>> outputPerStep = new HashMap<String, Map<String, List<String>>>();
    private Map<String, Object> params;
    private String oeOrderJson;
    @Autowired
    private CoordinatorClient coordinatorClient;
    @Autowired
    private DbClient dbClient;
    @Autowired
    private CustomServicesPrimitiveDAOs daos;

    private ImmutableMap<String, Step> stepsHash;
    private CustomServicesWorkflowDocument obj;
    private int code;

    @Override
    public void precheck() throws Exception {

        // get input params from order form
        params = ExecutionUtils.currentContext().getParameters();
        final String raw = ExecutionUtils.currentContext().getOrder().getWorkflowDocument();

        if (null == raw) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Invalid custom service.  Workflow document cannot be null");
        }

        obj = WorkflowHelper.toWorkflowDocument(raw);
        final List<Step> steps = obj.getSteps();
        final ImmutableMap.Builder<String, Step> builder = ImmutableMap.builder();
        for (final Step step : steps) {
            builder.put(step.getId(), step);
        }
        stepsHash = builder.build();
    }

    @Override
    public void execute() throws Exception {
        ExecutionUtils.currentContext().logInfo("customServicesService.title");
        try {
            wfExecutor();
            ExecutionUtils.currentContext().logInfo("customServicesService.successStatus");
        } catch (final Exception e) {
            ExecutionUtils.currentContext().logError("customServicesService.failedStatus");

            throw e;
        }
    }

    /**
     * Method to parse Workflow Definition JSON
     *
     * @throws Exception
     */
    public void wfExecutor() throws Exception {

        logger.info("Parsing Workflow Definition");

        ExecutionUtils.currentContext().logInfo("customServicesService.status", obj.getName(), obj.getDescription());
        final String orderDir = String.format("%s%s/", CustomServicesConstants.ORDER_DIR_PATH,
                ExecutionUtils.currentContext().getOrder().getOrderNumber());

        Step step = stepsHash.get(StepType.START.toString());
        String next = step.getNext().getDefaultStep();
        long timeout = System.currentTimeMillis();
        while (next != null && !next.equals(StepType.END.toString())) {
            step = stepsHash.get(next);

            ExecutionUtils.currentContext().logInfo("customServicesService.stepStatus", step.getId(), step.getType());

            updateInputPerStep(step);

            final CustomServicesTaskResult res;

            try {
                StepType type = StepType.fromString(step.getType());
                switch (type) {
                    case VIPR_REST: {

                        final CustomServicesPrimitiveType primitive = daos.get("vipr").get(step.getOperation());

                        if (null == primitive) {
                            throw InternalServerErrorException.internalServerErrors
                                    .customServiceExecutionFailed("Primitive not found: " + step.getOperation());
                        }

                        res = ViPRExecutionUtils.execute(new RunViprREST((CustomServicesViPRPrimitive) (primitive),
                                getClient().getRestClient(), inputPerStep.get(step.getId())));

                        break;
                    }
                    case REST:
                        logger.info("Start REST execution");
                        res = ViPRExecutionUtils
                                .execute(new CustomServicesRESTExecution(coordinatorClient, inputPerStep.get(step.getId()), step));
                        break;
                    case LOCAL_ANSIBLE:
                        logger.info("Executing Local Ansible step");
                        createOrderDir(orderDir);
                        res = ViPRExecutionUtils.execute(new RunAnsible(step, inputPerStep.get(step.getId()), params, dbClient, orderDir));
                        break;
                    case SHELL_SCRIPT:
                        logger.info("Executing Shell Script step");
                        createOrderDir(orderDir);
                        res = ViPRExecutionUtils.execute(new RunAnsible(step, inputPerStep.get(step.getId()), params, dbClient, orderDir));
                        break;
                    case REMOTE_ANSIBLE: {
                        logger.info("Executing remote ansible step");
                        res = ViPRExecutionUtils.execute(new RunAnsible(step, inputPerStep.get(step.getId()), params, dbClient, orderDir));
                        break;
                    }
                    default:
                        logger.error("Operation Type Not found. Type:{}", step.getType());

                        throw InternalServerErrorException.internalServerErrors
                                .customServiceExecutionFailed("Operation Type not supported" + type);
                }

                boolean isSuccess = isSuccess(step, res);
                if (isSuccess) {
                    try {
                        updateOutputPerStep(step, res);
                    } catch (final Exception e) {
                        logger.info("Failed to parse output" + e);

                        isSuccess = false;
                    }
                }
                next = getNext(isSuccess, res, step);
            } catch (final Exception e) {
                logger.info("failed to execute step. Try to get rollback step");
                next = getNext(false, null, step);
            } finally {
                orderDirCleanup(orderDir);
            }

            if (next == null) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Failed to get next step");
            }
            if ((System.currentTimeMillis() - timeout) > CustomServicesConstants.TIMEOUT) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Operation Timed out");
            }
        }
    }

    private boolean createOrderDir(final String orderDir) {
        try {
            final File file = new File(orderDir);
            if (!file.exists()) {
                return file.mkdir();
            } else {
                logger.info("Order directory already exists: {}", orderDir);
                return true;
            }
        } catch (final Exception e) {
            logger.error("Failed to create directory" + e);
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Failed to create Order directory " + orderDir);
        }

    }

    private void orderDirCleanup(final String orderDir) {
        try {
            final File file = new File(orderDir);
            if (file.exists()) {
                final String[] cmd = { CustomServicesConstants.REMOVE, CustomServicesConstants.REMOVE_OPTION, orderDir };
                Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmd);
            }
        } catch (final Exception e) {
            logger.error("Failed to cleanup OrderDir directory" + e);
        }
    }

    private boolean isSuccess(Step step, CustomServicesTaskResult result) {
        if (result == null)
            return false;
        // TODO commented this till I fix evaluation from the primitive
        return true;
        /*
         * if (step.getSuccessCriteria() == null) {
         * return evaluateDefaultValue(step, result.getReturnCode());
         * } else {
         * return findStatus(step.getSuccessCriteria(), result);
         * }
         */
    }

    private String getNext(final boolean status, final CustomServicesTaskResult result, final Step step) {
        if (status) {
            ExecutionUtils.currentContext().logInfo("customServicesService.stepSuccessStatus", step, result.getReturnCode());

            return step.getNext().getDefaultStep();
        }

        ExecutionUtils.currentContext().logError("customServicesService.stepFailedStatus", step);

        return step.getNext().getFailedStep();
    }

    /**
     * Method to collect all required inputs per step for execution
     *
     * @param step It is the JSON Object of Step
     */
    private void updateInputPerStep(final Step step) throws Exception {
        if (step.getInputGroups() == null) {
            return;
        }

        final Map<String, List<String>> inputs = new HashMap<String, List<String>>();
        for (final CustomServicesWorkflowDocument.InputGroup inputGroup : step.getInputGroups().values()) {
            for (final Input value : inputGroup.getInputGroup()) {
                final String name = value.getName();
                switch (CustomServicesConstants.InputType.fromString(value.getType())) {
                    case FROM_USER:
                    case ASSET_OPTION: {
                        final String friendlyName = value.getFriendlyName();
                        if (params.get(friendlyName) != null) {
                            inputs.put(name, Arrays.asList(params.get(friendlyName).toString()));
                        } else {
                            if (value.getDefaultValue() != null) {
                                inputs.put(name, Arrays.asList(value.getDefaultValue()));
                            }
                        }
                        break;
                    }
                    // TODO: Handle multi value
                    // case ASSET_OPTION_MULTI_VALUE:
                    case FROM_STEP_INPUT:
                    case FROM_STEP_OUTPUT: {
                        final String[] paramVal = value.getValue().split("\\.");
                        final String stepId = paramVal[CustomServicesConstants.STEP_ID];
                        final String attribute = paramVal[CustomServicesConstants.INPUT_FIELD];

                        Map<String, List<String>> stepInput;
                        if (value.getType().equals(InputType.FROM_STEP_INPUT.toString())) {
                            stepInput = inputPerStep.get(stepId);
                        } else {
                            stepInput = outputPerStep.get(stepId);
                        }
                        if (stepInput != null) {
                            if (stepInput.get(attribute) != null) {
                                inputs.put(name, stepInput.get(attribute));
                                break;
                            }
                        }
                        if (value.getDefaultValue() != null) {
                            inputs.put(name, Arrays.asList(value.getDefaultValue()));
                            break;
                        }

                        break;
                    }
                    default:
                        throw InternalServerErrorException.internalServerErrors
                                .customServiceExecutionFailed("Invalid input type:" + value.getType());
                }
            }
        }

        inputPerStep.put(step.getId(), inputs);
    }

    private List<String> evaluateAnsibleOut(final String result, final String key) throws Exception {
        final List<String> out = new ArrayList<String>();

        final JsonNode arrNode = new ObjectMapper().readTree(result).get(key);

        if (arrNode.isNull()) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Could not parse the output" + key);
        }

        if (arrNode.isArray()) {
            for (final JsonNode objNode : arrNode) {
                out.add(objNode.toString());
            }
        } else {
            out.add(arrNode.toString());
        }

        return out;
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
    private void updateOutputPerStep(final Step step, final CustomServicesTaskResult res) throws Exception {
        final List<CustomServicesWorkflowDocument.Output> output = step.getOutput();
        if (output == null)
            return;
        final String result = res.getOut();
        final Map<String, List<String>> out = new HashMap<String, List<String>>();

        for (final CustomServicesWorkflowDocument.Output o : output) {
            if (isAnsible(step)) {
                out.put(o.getName(), evaluateAnsibleOut(result, o.getName()));
            } else if (step.getType().equals(StepType.REST.toString())) {
                final CustomServicesRestTaskResult restResult = (CustomServicesRestTaskResult) res;
                final Set<Map.Entry<String, List<String>>> headers = restResult.getHeaders();
                for (final Map.Entry<String, List<String>> entry : headers) {
                    if (entry.getKey().equals(o.getName())) {
                        out.put(o.getName(), entry.getValue());
                    }
                }

            } else {

                // TODO: Remove this after parsing output is fully implemented
                // out.put(o.getName(), evaluateValue(result, o.getName()));
                return;
            }
        }
        //set the default result.
        out.put(CustomServicesConstants.OPERATION_OUTPUT, Arrays.asList(res.getOut()));
        out.put(CustomServicesConstants.OPERATION_ERROR, Arrays.asList(res.getErr()));
        out.put(CustomServicesConstants.OPERATION_RETURNCODE, Arrays.asList(String.valueOf(res.getReturnCode())));
        outputPerStep.put(step.getId(), out);
    }

    private boolean isAnsible(final Step step) {
        if (step.getType().equals(StepType.LOCAL_ANSIBLE.toString()) || step.getType().equals(StepType.REMOTE_ANSIBLE.toString())
                || step.getType().equals(StepType.SHELL_SCRIPT.toString()))
            return true;

        return false;
    }

    /**
     * Evaluate
     *
     * @param step
     * @param returnCode
     * @return
     */
    private boolean evaluateDefaultValue(final Step step, final int returnCode) {
        if (isAnsible(step)) {
            if (returnCode == 0)
                return true;

            return false;
        }

        // TODO get returncode for REST API from DB. Now it is hard coded.
        int code = 200;
        if (returnCode == code)
            return true;

        return false;
    }

    /**
     * This evaluates the expression from ViPR GSON structure.
     * e.g: It evaluates "task.resource.id" from ViPR REST Response
     *
     * @param result
     * @param value
     * @return
     */
    private List<String> evaluateValue(final String result, String value) throws Exception {

        final Gson gson = new Gson();
        final ViprOperation res = gson.fromJson(result, ViprOperation.class);
        final ExpressionParser parser = new SpelExpressionParser();

        logger.debug("Find value of:{}", value);
        List<String> valueList = new ArrayList<String>();

        if (!value.contains(CustomServicesConstants.TASK)) {
            Expression expr = parser.parseExpression(value);
            EvaluationContext context = new StandardEvaluationContext(res);
            String val = (String) expr.getValue(context);

            valueList.add(val);

        } else {

            String[] values = value.split("task.", 2);
            if (values.length != 2) {
                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed("Cannot evaluate values with statement:" + value);
            }
            value = values[1];
            Expression expr = parser.parseExpression(value);

            ViprTask[] tasks = res.getTask();
            for (ViprTask task : tasks) {
                EvaluationContext context = new StandardEvaluationContext(task);
                String v = (String) expr.getValue(context);
                valueList.add(v);
            }

            logger.info("valueList is:{}", valueList);
        }

        return valueList;
    }

    /**
     * This evaluates the status of a step from the SuccessCriteria mentioned in workflow definition JSON
     * e.g: Supported Expression Language for SuccessCriteria
     * Supported condition type code == x [x can be any number]
     * "returnCode == 404"
     * "returnCode == 0"
     * "task_state == 'pending' and description == 'create export1' and returnCode == 400"
     * "state == 'ready'";
     * Note: and, or cannot be part of lvalue or rvalue
     *
     * @param successCriteria
     * @param res
     * @return
     */
    private boolean findStatus(String successCriteria, final CustomServicesTaskResult res) {
        try {

            if (successCriteria == null)
                return true;

            if (successCriteria != null && res == null)
                return false;

            String result = res.getOut();

            SuccessCriteria sc = new SuccessCriteria();
            ExpressionParser parser = new SpelExpressionParser();
            EvaluationContext con2 = new StandardEvaluationContext(sc);
            String[] statements = successCriteria.split("\\bor\\b|\\band\\b");

            if (statements.length == 0)
                return false;

            int p = 0;
            for (String statement : statements) {
                if (statement.trim().startsWith(CustomServicesConstants.RETURN_CODE)) {
                    Expression e2 = parser.parseExpression(statement);

                    sc.setCode(res.getReturnCode());
                    boolean val = e2.getValue(con2, Boolean.class);
                    logger.info("Evaluated value for errorCode or returnCode is:{}", val);

                    successCriteria = successCriteria.replace(statement, " " + val + " ");

                    continue;
                }

                String arr[] = StringUtils.split(statement);
                String lvalue = arr[0];

                if (!lvalue.contains(CustomServicesConstants.TASK)) {

                    List<String> evaluatedValues = evaluateValue(result, lvalue);
                    sc.setEvaluateVal(evaluatedValues.get(0), p);
                    successCriteria = successCriteria.replace(lvalue, " evaluateVal[" + p + "]");
                    p++;

                    continue;
                }

                // TODO accepted format is task_state but spel expects task.state. Could not find a regex for that
                String lvalue1 = lvalue.replace("_", ".");

                List<String> evaluatedValues = evaluateValue(result, lvalue1);

                boolean val2 = true;

                if (evaluatedValues.isEmpty())
                    return false;

                String exp1 = statement.replace(lvalue, "eval");
                for (String evaluatedValue : evaluatedValues) {
                    sc.setEval(evaluatedValue);
                    Expression e = parser.parseExpression(exp1);
                    val2 = val2 && e.getValue(con2, Boolean.class);
                }

                successCriteria = successCriteria.replace(statement, " " + val2 + " ");
            }

            logger.info("Success Criteria to evaluate:{}", successCriteria);
            Expression e1 = parser.parseExpression(successCriteria);
            boolean val1 = e1.getValue(con2, Boolean.class);

            logger.info("Evaluated Value is:{}" + val1);

            return val1;
        } catch (final Exception e) {
            logger.error("Cannot evaluate success Criteria:{} Exception:{}", successCriteria, e);

            return false;
        }
    }
}

