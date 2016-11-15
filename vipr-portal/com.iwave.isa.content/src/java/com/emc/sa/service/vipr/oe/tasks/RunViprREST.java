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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationService;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants;
import com.emc.sa.service.vipr.oe.SuccessCriteria;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.orchestration.internal.ViPRPrimitive;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

public class RunViprREST extends ViPRExecutionTask<String> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunViprREST.class);

    private final Map<String, List<String>> input;
    private final RestClient client;
    private final ViPRPrimitive primitive;

    public RunViprREST(final ViPRPrimitive primitive, final RestClient client, final Map<String, List<String>> input) {
        this.input = input;
        this.client = client;
        this.primitive = primitive;
    }

    @Override
    public String executeTask() throws Exception {

        final String result;
        final String requestBody;

        final String templatePath = primitive.path();
        final String body = primitive.body();
        final String method = primitive.method();

        if (OrchestrationServiceConstants.BODY_REST_METHOD.contains(method) && !body.isEmpty()) {
            requestBody = makePostBody(body);
        } else {
            requestBody = "";
        }

        String path = makePath(templatePath);

        ExecutionUtils.currentContext().logInfo(String.format("Started Executing REST API with uri: %s and POST Body: %s ", path, requestBody));

        result = makeRestCall(path, requestBody, method);

        ExecutionUtils.currentContext().logInfo(String.format("Done Executing REST Step. REST result: %s", result));

        return result;
    }

    private String makeRestCall(final String path, final Object requestBody, final String method) throws Exception {

        final ClientResponse response;
        OrchestrationServiceConstants.restMethods restmethod = OrchestrationServiceConstants.restMethods.valueOf(method);

        switch(restmethod) {
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
                logger.error("Unknown REST method type");
		        throw new IllegalStateException("Invalid REST method type" + method);
        }

        SuccessCriteria o = new SuccessCriteria();
        o.setReturnCode(response.getStatus());
        logger.info("Status of ViPR REST Operation:{} is :{}", primitive.getName(), response.getStatus());

        String responseString = null;
        try {
            responseString = IOUtils.toString(response.getEntityInputStream(), "UTF-8");
        } catch (IOException e) {
            error("Error getting response from Orchestration Engine for: " + path +
                    " :: " + e.getMessage());
        }

        return responseString;
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
                //TODO convert to a better exception?
                throw new IllegalStateException("Unfulfilled path parameter: " + key);
            }
            pathParameterMap.put(key, value);
        }
        
        final String path = template.expand(pathParameterMap).getPath(); 

        ExecutionUtils.currentContext().logInfo(String.format("URI string is: %s", path));

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
    private String makePostBody(String body) {

        Matcher m = Pattern.compile("\\$(\\w+)").matcher(body);

        while (m.find()) {
            String pat = m.group(1);
            String newpat = "$" + pat;
            body = body.replace(newpat, input.get(pat).get(0));
        }

        return body;
    }
}

