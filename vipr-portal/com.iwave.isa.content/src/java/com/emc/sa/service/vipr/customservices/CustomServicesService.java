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
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.model.*;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.customservices.gson.ViprOperation;
import com.emc.sa.service.vipr.customservices.gson.ViprTask;
import com.emc.sa.service.vipr.customservices.tasks.CustomServicesExecutors;
import com.emc.sa.service.vipr.customservices.tasks.CustomServicesRestTaskResult;
import com.emc.sa.service.vipr.customservices.tasks.CustomServicesTaskResult;
import com.emc.sa.service.vipr.customservices.tasks.MakeCustomServicesExecutor;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesConstants.InputType;
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

import javax.xml.bind.annotation.XmlElement;

@Service("CustomServicesService")
public class CustomServicesService extends ViPRService {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesService.class);
    // <StepId, {"key" : "values...", "key" : "values ..."} ...>
    final private Map<String, Map<String, List<String>>> inputPerStep = new HashMap<String, Map<String, List<String>>>();
    final private Map<String, Map<String, List<String>>> outputPerStep = new HashMap<String, Map<String, List<String>>>();
    private Map<String, Object> params;
    private String oeOrderJson;

    @Autowired
    private DbClient dbClient;
    @Autowired
    private CustomServicesExecutors executor;

    private int code;

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
            wfExecutor(null, null);
            ExecutionUtils.currentContext().logInfo("customServicesService.successStatus");
        } catch (final Exception e) {
            ExecutionUtils.currentContext().logError("customServicesService.failedStatus");

            throw e;
        } finally {
            orderDirCleanup(orderDir);
        }
    }
    /**
     * Method to parse Workflow Definition JSON
     *
     * @throws Exception
     */
    public void wfExecutor(final URI uri, final Map<String, CustomServicesWorkflowDocument.InputGroup> stepInput) throws Exception {

        logger.info("Parsing Workflow Definition");

        final ImmutableMap<String, Step> stepsHash = getStepHash(uri);


        Step step = stepsHash.get(StepType.START.toString());
        String next = step.getNext().getDefaultStep();
        long timeout = System.currentTimeMillis();
        while (next != null && !next.equals(StepType.END.toString())) {
            step = stepsHash.get(next);

            ExecutionUtils.currentContext().logInfo("customServicesService.stepStatus", step.getId(), step.getType());

            final Step updatedStep = updatesubWfInput(step, stepInput);

            updateInputPerStep(updatedStep);

            final CustomServicesTaskResult res;
            try {
                if (updatedStep.getType().equals(StepType.WORKFLOW.toString())) {

                    wfExecutor(updatedStep.getOperation(), updatedStep.getInputGroups());

                    // We Don't evaluate output/result for Workflow Step. It is already evaluated.
                    // We would have got exception if Sub WF has failed
                    res = new CustomServicesTaskResult("Success", "No Error", 200, null);
                } else {
                    final MakeCustomServicesExecutor task = executor.get(updatedStep.getType());
                    task.setParam(getClient().getRestClient());

                    res = ViPRExecutionUtils.execute(task.makeCustomServicesExecutor(inputPerStep.get(updatedStep.getId()), updatedStep));
                }

                boolean isSuccess = isSuccess(updatedStep, res);
                if (isSuccess) {
                    try {
                        updateOutputPerStep(updatedStep, res);
                    } catch (final Exception e) {
                        logger.info("Failed to parse output" + e);

                        isSuccess = false;
                    }
                }
                next = getNext(isSuccess, res, updatedStep);
            } catch (final Exception e) {
                logger.info(
                        "failed to execute step. Try to get failure path. Exception Received:" + e + e.getStackTrace()[0].getLineNumber());
                next = getNext(false, null, updatedStep);
            }
            if (next == null) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Failed to get next step");
            }
            if ((System.currentTimeMillis() - timeout) > CustomServicesConstants.TIMEOUT) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Operation Timed out");
            }
        }
    }

    private ImmutableMap<String, Step> getStepHash(final URI uri) throws Exception {

        final String raw;

        if (uri == null) {
            raw = ExecutionUtils.currentContext().getOrder().getWorkflowDocument();
        } else {
            //Get it from DB
            final CustomServicesWorkflow wf = dbClient.queryObject(CustomServicesWorkflow.class, uri);
            raw = WorkflowHelper.toWorkflowDocumentJson(wf);
        }
        if (null == raw) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Invalid custom service.  Workflow document cannot be null");
        }
        final CustomServicesWorkflowDocument obj = WorkflowHelper.toWorkflowDocument(raw);

        final List<Step> steps = obj.getSteps();
        final ImmutableMap.Builder<String, Step> builder = ImmutableMap.builder();
        for (final Step step : steps) {
            builder.put(step.getId(), step);
        }
        final ImmutableMap<String, Step> stepsHash = builder.build();

        ExecutionUtils.currentContext().logInfo("customServicesService.status", obj.getName(), obj.getDescription());

        return stepsHash;
    }

    private boolean needUpdate(final Step step, final Map<String, CustomServicesWorkflowDocument.InputGroup> stepInput) {
        if (step.getType().equals(StepType.WORKFLOW.toString()) || stepInput == null || step.getInputGroups() == null) {
            return false;
        }

        return true;
    }

    private Step updatesubWfInput(final Step step, final Map<String, CustomServicesWorkflowDocument.InputGroup> stepInput) {
        if(!needUpdate(step, stepInput)) {
            return step;
        }

        for (final CustomServicesWorkflowDocument.InputGroup inputGroup : step.getInputGroups().values()) {
            for (final Input value : inputGroup.getInputGroup()) {
                final String name = value.getFriendlyName();
                switch (CustomServicesConstants.InputType.fromString(value.getType())) {
                    case FROM_USER:
                    case ASSET_OPTION:
                        for (final CustomServicesWorkflowDocument.InputGroup inputGroup1 : stepInput.values()) {
                            for (final Input value1 : inputGroup1.getInputGroup()) {
                                final String name1 = value1.getFriendlyName();
                                if (name1.equals(name)) {
                                    logger.debug("Change the type name:{}", name);
                                    value.setType(value1.getType());
                                    value.setValue(value1.getValue());
                                    value.setDefaultValue(value1.getDefaultValue());
                                }
                            }
                        }
                        break;
                    default:
                        logger.info("do nothing");
                }
            }
        }

        return step;
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

    private boolean updateInput(final Step step) {
        if (step.getType().equals(StepType.WORKFLOW.toString()) || step.getInputGroups() == null) {
            return false;
        }

        return true;
    }
    /**
     * Method to collect all required inputs per step for execution
     *
     * @param step It is the JSON Object of Step
     */
    private void updateInputPerStep(final Step step) throws Exception {
        if (!updateInput(step)) {
            return;
        }

        final Map<String, List<String>> inputs = new HashMap<String, List<String>>();
        for (final CustomServicesWorkflowDocument.InputGroup inputGroup : step.getInputGroups().values()) {
            for (final Input value : inputGroup.getInputGroup()) {
                final String name = value.getName();

                switch (InputType.fromString(value.getType())) {
                    case FROM_USER:
                    case ASSET_OPTION:
                        final String friendlyName = value.getFriendlyName();
                        if (params.get(friendlyName) != null && !StringUtils.isEmpty(params.get(friendlyName).toString())) {
                            inputs.put(name, Arrays.asList(params.get(friendlyName).toString().split(",")));
                        } else {
                            if (value.getDefaultValue() != null) {
                                inputs.put(name, Arrays.asList(value.getDefaultValue().split(",")));
                            }
                        }
                        break;
                    case ASSET_OPTION_MULTI:
                    case ASSET_OPTION_SINGLE:
                    // TODO: Handle multi value

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

        if (step.getType().equals(CustomServicesConstants.VIPR_PRIMITIVE_TYPE) ) {
            logger.info("call updateViproutput");
            updateViproutput(step, res.getOut());
        }
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

    private void updateViproutput(final Step step, String res) throws Exception {
        logger.info("updateViproutput is called");
        //TODO get the classname from primitive
        final String classname = "com.emc.storageos.model.TaskList";
        //TODO Hard code for create volume response
        res = "{\n"
                + "  \"task\": [\n"
                + "    {\n"
                + "      \"resource\": {\n"
                + "        \"name\": \"volume5678\",\n"
                + "        \"id\": \"urn:storageos:Volume:08a68c03-ec03-4c43-be6b-d69d8d10f3cb:\",\n"
                + "        \"link\": {\n"
                + "          \"rel\": \"self\",\n"
                + "          \"href\": \"/block/volumes/urn:storageos:Volume:08a68c03-ec03-4c43-be6b-d69d8d10f3cb:\"\n"
                + "        }\n"
                + "      },\n"
                + "      \"state\": \"pending\",\n"
                + "      \"start_time\": 1380042421406,\n"
                + "      \"op_id\": \"3e97cf33-cf9f-4b06-914a-c9286d34bcb7\",\n"
                + "      \"link\": {\n"
                + "        \"rel\": \"self\",\n"
                + "        \"href\": \"/block/volumes/urn:storageos:Volume:08a68c03-ec03-4c43-be6b-d69d8d10f3cb:/tasks/3e97cf33-cf9f-4b06-914a-c9286d34bcb7\"\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        logger.info("res string is:{}", res);
        final ObjectMapper MAPPER = new ObjectMapper();
        MAPPER.setAnnotationIntrospector(new JaxbAnnotationIntrospector());
        final Class<?> clazz = Class.forName(classname);
        logger.info("didnt receive classNF exception");

        final Object taskList = MAPPER.readValue(res, clazz.newInstance().getClass());

        if (taskList instanceof TaskList) {
            logger.info("it is an instance for TaskList");
            updateViprTaskoutput((TaskList) taskList, step);
            logger.info("done with updateViprTaskoutput");
        } else {
            DataObjectRestRep obj = (DataObjectRestRep)taskList;
            Method m = findMethod("name", taskList.getClass().getMethods());
            m.invoke(this, null);
        }
    }
    private void updateViprTaskoutput(final TaskList taskList, final Step step) throws Exception {
        logger.info("in updateViprTaskoutput");
       // final List<CustomServicesWorkflowDocument.Output> stepOut = step.getOutput();
        final List<CustomServicesWorkflowDocument.Output> stepOut = new ArrayList<CustomServicesWorkflowDocument.Output>();
        //TODO remove this after primitive code is ready

        CustomServicesWorkflowDocument.Output stepout1 = new CustomServicesWorkflowDocument.Output();
        stepout1.setName("tasks.task.op_id");
        CustomServicesWorkflowDocument.Output stepout2 = new CustomServicesWorkflowDocument.Output();
        stepout2.setName("tasks.task.resource.name");
        CustomServicesWorkflowDocument.Output stepout3 = new CustomServicesWorkflowDocument.Output();
        stepout3.setName("tasks.task.resource.id");
        //CustomServicesWorkflowDocument.Output stepout4 = new CustomServicesWorkflowDocument.Output();
        //stepout4.setName("tasks.@task.associated_resources.@associated_resource.name");

        stepOut.add(stepout1);
        stepOut.add(stepout2);
        stepOut.add(stepout3);
        //stepOut.add(stepout4);

        for (final CustomServicesWorkflowDocument.Output out : stepOut) {
            final String outName = out.getName();
            logger.info("output name is :{}", outName);

            String[] bits = outName.split("\\.");
            String lastOne = bits[bits.length-1];

            logger.info("lastOne:{}", lastOne);

            ifprimitivetheninvoke(bits, 1, taskList);
            /*
            final List<TaskResourceRep> taskResourceReps = taskList.getTaskList();
            for (final TaskResourceRep task : taskResourceReps) {
                if (outName.contains(".resource")) {
                    logger.info("it is of resource type");
                    //tasks.@task.resource.resource.id
                    final Method method = findMethod(lastOne, task.getResource().getClass().getMethods());
                    if (method == null) {
                        logger.info("method is null");
                    } else {
                        logger.info("invoke the method");
                        Object out1 = method.invoke(task.getResource(), null);
                        logger.info("output value in resource:{}", out1);
                    }
                    break;
                }

                if (outName.contains("associated_resources")) {
                    logger.info("it is of associated_resources type");
                    for (final NamedRelatedResourceRep assres : task.getAssociatedResources()) {
                        final Method method = findMethod(lastOne, assres.getClass().getMethods());

                        if (method == null) {
                            logger.info("method is null");
                        } else {
                            logger.info("invoke the method");

                            Object out1 = method.invoke(task.getAssociatedResources(), null);
                            logger.info("output value in associated_resource:{}", out1);
                        }
                        break;
                    }
                    break;

                }
                if (outName.contains("tenant")) {
                    logger.info("it is of tenant type");
                    RelatedResourceRep resourcerep = task.getTenant();
                    final Method method = findMethod(lastOne, resourcerep.getClass().getMethods());
                    if (method == null) {
                        logger.info("method is null");
                    } else {
                        logger.info("invoke the method");

                        Object out1 = method.invoke(task.getTenant(), null);
                        logger.info("output value in associated_resource:{}", out1);
                    }
                    break;
                }

                if (outName.contains("serviceerror")) {
                    logger.info("it is of serviceerror type");
                    ServiceErrorRestRep resourcerep = task.getServiceError();
                    final Method method = findMethod(lastOne, resourcerep.getClass().getMethods());
                    if (method == null) {
                        logger.info("method is null");
                    } else {
                        logger.info("invoke the method");

                        Object out1 = method.invoke(task.getServiceError(), null);
                        logger.info("output value in associated_resource:{}", out1);
                    }
                    break;
                }

                final Method method = findMethod(lastOne, task.getClass().getMethods());
                Object out1 = method.invoke(task, null);
                logger.info("output value else:{}", out1);
                */
            }
            //TODO parse the name tasks.@task.op_id, tasks.@task.resource.resource.id, tasks.@task.associated_resources.@associated_resource.associated_resources.@associated_resource.name
        }


        /*Field fieldList[] = taskList.getClass().getDeclaredFields();
        for (int i=0; i<fieldList.length; i++) {
            fieldList[0].getType().isPrimitive();
            Method[] methods = taskList.getClass().getDeclaredMethods();
            if (methods[0].isAnnotationPresent(XmlElement.class)) {
                XmlElement xyz = methods[0].getAnnotation(XmlElement.class);
                if (xyz.name().equals("op_id")) {
                    methods[0].invoke(this, null);
                }
            }
        }*/
    //}

    private void ifprimitivetheninvoke(String[] bits, int i, Object className ) throws Exception {

        logger.info("in ifprimitivetheninvoke");
        Method method = findMethod(bits[i], className.getClass().getMethods());

        logger.info("bit:{}", bits[i]);

        //1) primitive
        if (method.getReturnType().isPrimitive() || method.getReturnType().equals(String.class)) {
            logger.info("it is of primitive type");
            if (i == bits.length - 1) {
                logger.info("it is the last bit also:{}", bits[i]);
                logger.info("value:{}", method.invoke(className, null));
            }
            return;
        }
        //3) Collection primitive
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType) {
            logger.info("return type is parameterized");
            Type t = ((ParameterizedType) returnType).getRawType();
            if (t.getClass().isPrimitive()) {
                logger.info("it is primitive array type");
            }
            return;
        }
        //2) Class single object
        if (returnType instanceof Class<?>) {
            logger.info("return type class single obj");
            ifprimitivetheninvoke(bits, i + 1, method.invoke(className, null));

        }


        //4) Collection Non primitive
        if (returnType instanceof ParameterizedType) {
            logger.info("return type of ParameterizedType 2");
            Type t = ((ParameterizedType) returnType).getRawType();
            if (!t.getClass().isPrimitive()) {
                logger.info("it is not primitive array type");
                List<?> o = (List<?>) method.invoke(className, null);
                for (Object o1 : o) {
                    logger.info("invoke again");
                    ifprimitivetheninvoke(bits, i + 1, o1);
                }
            }
        }
    }

    private Method findMethod(final String str, final Method[] methods) {
        logger.info("in findMethod");
        for (Method method : methods) {
            logger.info("method name:{}", method.getName());
            Annotation[] annotations = method.getDeclaredAnnotations();
            for(Annotation annotation : annotations){
                if (annotation instanceof XmlElement) {
                    logger.info("annotation of type xmlelem");
                    logger.info("xml annot name:{}", ((XmlElement) annotation).name());
                    if(str.equals(((XmlElement) annotation).name())){
                        logger.info("FOUND annot:{}", str);
                    }
                }
            }
            XmlElement elem = method.getAnnotation(XmlElement.class);
            if (elem != null) {
                logger.info("elem name:{}", elem.name());
                if (elem.name().equals(str) && isGetter(method)) {
                    logger.info("name matched elem:{} str:{}", elem.name(), str);
                    return method;
                }
            } else {
                logger.info("elem is null for method:{}", method.getName());
            }
        }
        logger.info("didnt match in xml. str:{}", str);
        for (Method method : methods) {
            final String methodName = "get"+str;
            if (method.getName().equals(methodName)) {
                return method;
            }
        }

        return null;
    }

    public static boolean isGetter(Method method){
        if(!method.getName().startsWith("get"))      return false;
        if(method.getParameterTypes().length != 0)   return false;
        if(void.class.equals(method.getReturnType())) return false;
        return true;
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

