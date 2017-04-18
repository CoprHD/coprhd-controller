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

import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;

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

    private boolean isSuccess(final Step step, final CustomServicesTaskResult result) {
        if (result == null)
            return false;

        if (step.getType().equals(CustomServicesConstants.VIPR_PRIMITIVE_TYPE) ||  step.getType().equals(
                CustomServicesConstants.REST_API_PRIMITIVE_TYPE)) {
            if (result.getReturnCode() >= 200 && result.getReturnCode() < 300) {
                return true;
            } else {
                return false;
            }
        }

        if (result.getReturnCode() == 0) {
            return true;
        } else {
            return false;
        }
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
            try {
                updateViproutput(step, res.getOut());
            } catch (Exception e) {
                logger.warn("Could not parse ViPR REST Output properly:{}", e);
            }
        } else {
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
                }
            }
        }
        //set the default result.
        out.put(CustomServicesConstants.OPERATION_OUTPUT, Arrays.asList(res.getOut()));
        out.put(CustomServicesConstants.OPERATION_ERROR, Arrays.asList(res.getErr()));
        out.put(CustomServicesConstants.OPERATION_RETURNCODE, Arrays.asList(String.valueOf(res.getReturnCode())));
        outputPerStep.put(step.getId(), out);
    }

    private void updateViproutput(final Step step, final String res) throws Exception {
        //TODO get the classname from primitive
        //final String classname = "com.emc.storageos.model.TaskList";
        final String classname = "com.emc.storageos.model.block.VolumeRestRep";

        logger.debug("Result is:{}", res);
        final ObjectMapper MAPPER = new ObjectMapper();
        MAPPER.setAnnotationIntrospector(new JaxbAnnotationIntrospector());
        final Class<?> clazz = Class.forName(classname);

        final Object taskList = MAPPER.readValue(res, clazz.newInstance().getClass());

        outputPerStep.put(step.getId(), updateViprTaskoutput(taskList, step));
    }

    private Map<String, List<String>> updateViprTaskoutput(final Object taskList, final Step step) throws Exception {
        final List<CustomServicesWorkflowDocument.Output> stepOut = step.getOutput();

        final Map<String, List<String>> output = new HashMap<String, List<String>>();
        for (final CustomServicesWorkflowDocument.Output out : stepOut) {
            final String outName = out.getName();
            logger.info("output to parse:{}", outName);

            final String[] bits = outName.split("\\.");

            output.put(out.getName(),ifprimitivetheninvoke(bits, 0, taskList) );
        }

        return output;
    }

    private boolean isPrimitive(final Class<?> primitiveclass){

        if (primitiveclass.isPrimitive()
                || primitiveclass.equals(String.class)
                || primitiveclass.equals(URI.class)
                || primitiveclass.equals(Calendar.class)
                || primitiveclass.equals(Date.class)) {
            return true;
        }

        return false;
    }

    private List<String> ifprimitivetheninvoke(final String[] bits, final int i, final Object className ) throws Exception {

        if (className == null) {
            logger.error("class name is null, cannot parse output");

            return null;
        }
        final Method method = findMethod(bits[i], className);

        if (method == null) {
            logger.info("method is null. cannot parse output");
            return null;
        }
        logger.debug("bit:{}", bits[i]);

        //1) primitive
        if (isPrimitive(method.getReturnType())) {
            if (i == bits.length - 1) {
                final Object value = method.invoke(className, null);
                logger.info("value:{}", value);
                return Arrays.asList((String) value);
            }
        }

        final Type returnType = method.getGenericReturnType();
        if (returnType == null) {
            logger.info("Cound not find return type of method:{}", method.getName());

            return null;
        }

        //2) Class single object
        if (returnType instanceof Class<?>) {
            logger.info("return type class single obj");
            ifprimitivetheninvoke(bits, i + 1, method.invoke(className, null));

        }

        //3) Collection primitive
        if (Collection.class.isAssignableFrom(method.getReturnType())) {
            return getCollectionValue(method, bits, i, className);

        }

        return null;
    }

    private List<String> getCollectionValue(final Method method, final String[] bits, final int i, final Object className) throws Exception {
        final Class returnClass = method.getReturnType();
        if (List.class.isAssignableFrom(returnClass)) {
            logger.info("type is list");
            final Type returnType = method.getGenericReturnType();
            if (returnType instanceof ParameterizedType) {
                final ParameterizedType paramType = (ParameterizedType) returnType;
                final Class<?> stringListClass = (Class<?>) paramType.getActualTypeArguments()[0];
                if (isPrimitive(stringListClass)) {
                    final Object val = method.invoke(className, null);
                    logger.info("array value:{}", method.invoke(className, null));
                    return (List<String>)val;
                } else {
                    final Type o = paramType.getActualTypeArguments()[0];
                    if (o instanceof Class<?>) {
                        for (final Object o1 : (List<?>) method.invoke(className, null)) {
                            ifprimitivetheninvoke(bits, i + 1, o1);
                        }
                    }
                }
            }
        }
        else if (Set.class.isAssignableFrom(returnClass)) {
            logger.info("type is set");
            final Type returnType = method.getGenericReturnType();
            if (returnType instanceof ParameterizedType) {
                final ParameterizedType paramType = (ParameterizedType) returnType;
                final Class<?> stringListClass = (Class<?>) paramType.getActualTypeArguments()[0];
                if (isPrimitive(stringListClass)) {
                    final Object set = method.invoke(className, null);
                    logger.info("array value:{}", method.invoke(className, null));
                    final List<String> list = new ArrayList<String>((Set<String>)set);

                    return list;
                } else {
                    final Type o = paramType.getActualTypeArguments()[0];
                    if (o instanceof Class<?>) {
                        for (final Object o1 : (Set<?>) method.invoke(className, null)) {
                            ifprimitivetheninvoke(bits, i + 1, o1);
                        }
                    }
                }
            }
        }

        //We do not support any other collection type
        return null;
    }

    private Method findMethod(final String str, final Object className) throws Exception {
        final Method[] methods = className.getClass().getMethods();
        for (Method method : methods) {
            logger.info("method name:{}", method.getName());
            XmlElement elem = method.getAnnotation(XmlElement.class);
            if (elem != null) {
                if (elem.name().equals(str) && isGetter(method)) {
                    logger.info("name matched elem:{} str:{}", elem.name(), str);
                    return method;
                }
            }
        }
        logger.info("didn't match in xml. str:{} check for getter", str);

        final Field field = getField(className.getClass(), str);
        if (field != null) {
            final PropertyDescriptor pd = new PropertyDescriptor(str, className.getClass());
            if (pd == null) {
                return null;
            }
            final Method getter = pd.getReadMethod();
            if (getter != null) {
                if (getter.getAnnotation(XmlElement.class) == null) {
                    return null;
                }
                if (getter.getAnnotation(XmlElement.class).name().equals("##default")) {
                    return getter;
                }
            }
        }

        logger.info("could not find getter");

        return null;
    }

    private static Field getField(Class<?> clazz, final String name) {
        Field field = null;
        while (clazz != null && field == null) {
            try {
                field = clazz.getDeclaredField(name);
            } catch (Exception e) {
            }
            clazz = clazz.getSuperclass();
        }

        return field;
    }

    public static boolean isGetter(Method method){
        if(!method.getName().startsWith("get")
                || method.getParameterTypes().length != 0
                || void.class.equals(method.getReturnType())) {
            return false;
        }

        return true;
    }

    private boolean isAnsible(final Step step) {
        if (step.getType().equals(StepType.LOCAL_ANSIBLE.toString()) || step.getType().equals(StepType.REMOTE_ANSIBLE.toString())
                || step.getType().equals(StepType.SHELL_SCRIPT.toString()))
            return true;

        return false;
    }
}

