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
import org.apache.commons.lang.StringUtils;
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

            logger.warn("Exception received:{}", e);
            if (e.getServiceCode().getCode() == ServiceCode.CUSTOM_SERVICE_NOTASK.getCode()) {
                return new CustomServicesTaskResult(responseString, responseString, response.getStatus(), null);
            }

            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Failed to Execute REST request" + e.getMessage());
        } catch (final Exception e) {
            logger.warn("Exception received:{}", e);
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

        logger.debug("make body for" + body);
        final String[] strs = body.split(":");

        for (int j = 0; j < strs.length; j++) {
            if (StringUtils.isEmpty(strs[j])) {
                continue;
            }

            if (!strs[j].contains("{")) {

                if ((!strs[j].contains("["))) {
                    //Single type parameter
                    strs[j] = findReplace(strs[j], pos, false);
                    continue;
                } else {
                    //Array type parameter
                    strs[j] = findReplace(strs[j], pos, true);
                    continue;
                }
            }

            //Complex Array of Objects type
            if (strs[j].contains("[{")) {
                int start = j;
                final StringBuilder secondPart = new StringBuilder(strs[j].split("\\[")[1]);

                final String firstPart = strs[j].split("\\[")[0];
                j++;
                List<String> vals = null;
                while (!strs[j].contains("}]")) {
                    //Get the number of Objects in array of object type
                    if (vals == null) {
                        vals = getListofObjects(strs[j]);
                    }
                    secondPart.append(":").append(strs[j]);

                    j++;
                }
                final String[] splits = strs[j].split("\\}]");
                final String firstOfLastLine = splits[0];
                final String end = splits[1];
                secondPart.append(":").append(firstOfLastLine).append("}");

                int last = j;

                //join all the objects in an array
                strs[start] = firstPart + "[" + makeComplexBody(vals,secondPart.toString()) + "]" + end;

                while (start + 1 <= last) {
                    strs[++start] = "";
                }
            }
        }

        logger.info("ViPR Request body" + joinStrs(strs));

        return joinStrs(strs);
    }

    private List<String> getListofObjects(final String strs) {
        final Matcher m = Pattern.compile("\\$([\\w\\.\\@]+)").matcher(strs);
        while (m.find()) {
            final String p = m.group(1);
            return input.get(p);
        }

        return null;
    }

    private String joinStrs(final String[] strs) {
        final StringBuilder sb = new StringBuilder(strs[0]);
        for (int i = 1; i < strs.length; i++) {
            if (!strs[i].isEmpty()) {
                sb.append(":").append(strs[i]);
            }
        }

        return sb.toString();
    }

    private String makeComplexBody(final List<String> vals, final String secondPart) {
        String get = "";
        if (vals == null) {
            logger.error("Cannot Build ViPR Request body");
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Cannot Build ViPR Request body");
        }
        for (int k = 0; k < vals.size(); k++) {
            // Recur for number of Objects
            get = get + makePostBody(secondPart, k) + ",";
        }

        //remove the trailing "," of json body and return
        return get.replaceAll(",$", "");
    }

    private String findReplace(final String str, final int pos, final boolean isArraytype) {
        final Matcher m = Pattern.compile("\\$([\\w\\.\\@]+)").matcher(str);
        while (m.find()) {
            final String pat = m.group(0);
            final String pat1 = m.group(1);

            final List<String> val = input.get(pat1);
            final StringBuilder sb = new StringBuilder();
            String vals = "";
            if (val != null && !StringUtils.isEmpty(val.get(pos))) {
                if (!isArraytype) {
                    sb.append("\"").append(val.get(pos)).append("\"");
                    vals = sb.toString();

                } else {

                    final String temp = val.get(pos);
                    final String[] strs = temp.split(",");
                    for (int i = 0; i < strs.length; i++) {
                        sb.append("\"").append(strs[i]).append("\"").append(",");
                    }
                    final String value = sb.toString();

                    vals = value.replaceAll(",$", "");
                }
            } else {
                vals = "\"\"";
            }
            return str.replace(pat, vals);
        }

        return "";
    }
}

