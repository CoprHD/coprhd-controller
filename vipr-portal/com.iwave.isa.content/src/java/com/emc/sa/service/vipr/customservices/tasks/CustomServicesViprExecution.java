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

package com.emc.sa.service.vipr.customservices.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.customservices.CustomServicesUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

/**
 * This provides ability to run ViPR REST APIs
 */
public class CustomServicesViprExecution extends ViPRExecutionTask<CustomServicesTaskResult> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesViprExecution.class);
    private static final String TASKLIST = "TaskList";
    private final Map<String, List<String>> input;
    private final RestClient client;
    private final CustomServicesViPRPrimitive primitive;
    private final CustomServicesWorkflowDocument.Step step;

    public CustomServicesViprExecution(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step, final CustomServicesPrimitiveDAOs daos, final RestClient client) {
        this.input = input;
        this.step = step;
        if (daos.get(CustomServicesConstants.VIPR_PRIMITIVE_TYPE) == null) {
            logger.error("ViPR operation DAO not found");
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("ViPR operation DAO not found: " + step.getOperation());
        }
        final CustomServicesPrimitiveType primitive = daos.get(CustomServicesConstants.VIPR_PRIMITIVE_TYPE).get(step.getOperation());

        if (null == primitive) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Primitive not found: " + step.getOperation());
        }
        this.primitive = (CustomServicesViPRPrimitive)primitive;
        this.client = client;
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {

        final String requestBody;

        final String templatePath = primitive.path();
        final String body = primitive.body();
        final String method = primitive.method();

        if (CustomServicesConstants.BODY_REST_METHOD.contains(method) && !body.isEmpty()) {
            requestBody = makePostBody(body, 0);
        } else {
            requestBody = "";
        }

        String path = makePath(templatePath);

        ExecutionUtils.currentContext().logInfo("customServicesViprExecution.startInfo", primitive.friendlyName());

        CustomServicesTaskResult result = makeRestCall(path, requestBody, method);

        logger.info("result is:{}", result.getOut());
        ExecutionUtils.currentContext().logInfo("customServicesViprExecution.doneInfo", primitive.friendlyName());

        return result;
    }

    private Map<URI, String> waitForTask(final String result) throws Exception
    {
        final List<URI> uris = new ArrayList<URI>();
        //TODO get the class name from primitive
        final String classname = "com.emc.storageos.model.block.VolumeRestRep";
        if (classname.contains(TASKLIST)) {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector());
            final Class<?> clazz = Class.forName(classname);

            final Object taskList = mapper.readValue(result, clazz.newInstance().getClass());
            List<TaskResourceRep> resources = ((TaskList)taskList).getTaskList();

            for ( TaskResourceRep res : resources) {
                uris.add(res.getId());
            }

        }
        if (!uris.isEmpty()) {
            return CustomServicesUtils.waitForTasks(uris, getClient());
        }

        return null;
    }

    private CustomServicesTaskResult makeRestCall(final String path, final Object requestBody, final String method) throws InternalServerErrorException {

        ClientResponse response = null;
        String responseString = null;
        CustomServicesConstants.RestMethods restmethod = CustomServicesConstants.RestMethods.valueOf(method);

        try {
            switch (restmethod) {
                case GET:
                    response = client.get(ClientResponse.class, path);
                    break;
                case PUT:
                    response = client.put(ClientResponse.class, requestBody, path);
                    break;
                case POST:
                    response = client.post(ClientResponse.class, requestBody, path);
                    break;
                case DELETE:
                    response = client.delete(ClientResponse.class, path);
                    break;
                default:
                    throw InternalServerErrorException.internalServerErrors.
                            customServiceExecutionFailed("Invalid REST method type" + method);
            }

            if (response == null) {
                throw InternalServerErrorException.internalServerErrors.
                        customServiceExecutionFailed("REST Execution Failed. Response returned is null");
            }

            logger.info("Status of ViPR REST Operation:{} is :{}", primitive.name(), response.getStatus());


            responseString = IOUtils.toString(response.getEntityInputStream(), "UTF-8");

            final Map<URI, String> taskState = waitForTask(responseString);
            return new CustomServicesTaskResult(responseString, responseString, response.getStatus(), taskState);

        } catch (final InternalServerErrorException e) {

            if (e.getServiceCode().getCode() == ServiceCode.CUSTOM_SERVICE_NOTASK.getCode()) {
                return new CustomServicesTaskResult(responseString, responseString, response.getStatus(), null);
            }

            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Failed to Execute REST request" + e.getMessage());
        } catch (final Exception e) {

            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("REST Execution Failed" + e.getMessage());
        }
    }


    /**
     * Example uri: "/block/volumes/{id}/findname/{name}";
     * @param templatePath
     * @return
     */
    private String makePath(String templatePath) {
        final UriTemplate template = new UriTemplate(templatePath);
        final List<String> pathParameters = template.getVariableNames();
        final Map<String, Object> pathParameterMap = new HashMap<String, Object>();
        
        for(final String key : pathParameters) {
            List<String> value = input.get(key);
            if(null == value) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Unfulfilled path parameter: " + key);
            }
	    //TODO find a better fix
            pathParameterMap.put(key, value.get(0).replace("\"",""));
        }
        
        final String path = template.expand(pathParameterMap).getPath(); 

        logger.info("URI string is: {}", path);

        return path;
    }


    /**
     * POST body format:
     *  "{\n" +
     "  \"consistency_group\": \"$consistency_group\",\n" +
     "  \"count\": \"$count\",\n" +
     "  \"name\": \"$name\",\n" +
     "  \"project\": \"$project\",\n" +
     "  \"size\": \"$size\",\n" +
     "  \"varray\": \"$varray\",\n" +
     "  \"vpool\": \"$vpool\"\n" +
     "}";
     * @param body
     * @return
     */
    private String makePostBody(final String body, final int pos) {

        logger.info("make body for" + body);
        String[] strs = body.split(":");
        for (int i = 0; i < strs.length; i++) {
            //System.out.println("val:"+strs[i]);
        }

        for (int j = 0; j < strs.length; j++) {
            if (!strs[j].contains("[") && !strs[j].contains("{")) {
                logger.info("doesn't contain" + strs[j]);
                Matcher m = Pattern.compile("\\$(\\w+)").matcher(strs[j]);
                while (m.find()) {
                    String pat = m.group(0);
                    String vals = input.get(m.group(1)).get(pos);
                    logger.info("new pat" + pat);
                    strs[j] = strs[j].replace(pat, vals);

                    logger.info("replaced new pat" + strs[j]);
                }
                continue;
            }

            if (strs[j].contains("[") && !strs[j].contains("{")) {
                logger.info("Array type" + strs[j]);
                Matcher m = Pattern.compile("\\$(\\w+)").matcher(strs[j]);
                while (m.find()) {
                    //System.out.println("pat" + m.group(1) + m.group(0));
                    String pat = m.group(0);
                    logger.info("new pat" + pat);
                    String p = m.group(1);
                    logger.info("group1:" + p + "val" + input.get(p));

                    String vals = input.get(m.group(1)).get(pos);
                    strs[j] = strs[j].replace(pat, vals);
                    logger.info("replaced new pat" + strs[j]);
                }
                continue;
            }

            if (strs[j].contains("[{")) {
                int start = j;
                String t1 = strs[j].split("\\[")[1];
                String first = strs[j].split("\\[")[0];
                j++;
                boolean doOnce = false;
                List<String> vals = new ArrayList<String>();
                while (!strs[j].contains("}]")) {
                    if (!doOnce) {
                        Matcher m = Pattern.compile("\\$(\\w+)").matcher(strs[j]);
                        while (m.find()) {

                            vals = input.get(m.group(1));
                            doOnce = true;
                        }
                    }
                    t1 = t1 + ":" + strs[j];
                    j++;
                }
                t1 = t1 + ":" + strs[j].split("\\]")[0];
                String end = strs[j].split("\\]")[1];
                int last = j;
                logger.info("recur:" + t1);
                String get = "";
                for (int k = 0; k < vals.size(); k++) {
                    get = get + makePostBody(t1, k) + ",";
                }
                get = get.replaceAll(",$", "");
                strs[start] = first + "[" + get + "]" + end;
                while (start + 1 <= last) {
                    strs[++start] = "";
                }
            }

        }
        String joinedstr = strs[0];
        for (int i = 1; i < strs.length; i++) {
            if (!strs[i].isEmpty()) {
                joinedstr = joinedstr + ":" + strs[i];
            }
        }

        logger.info("New String" + joinedstr);
        return joinedstr;
    }
}

