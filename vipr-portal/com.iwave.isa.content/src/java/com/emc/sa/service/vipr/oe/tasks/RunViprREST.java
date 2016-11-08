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

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationService;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants;
import com.emc.sa.service.vipr.oe.SuccessCriteria;
import com.emc.sa.service.vipr.oe.primitive.PrimitiveHelper;
import com.emc.sa.service.vipr.oe.primitive.RestPrimitive;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClient;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClientFactory;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunViprREST extends ViPRExecutionTask<String> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrchestrationService.class);

    private String name;
    private Map<String, List<String>> input;
    private String token;
    private OrchestrationEngineRestClient restClient;

    //TODO use Proxy user
    private String endPoint = "localhost";

    private DbClient dbClient;
    private RestPrimitive primitive;

    public RunViprREST(final DbClient dbClient, final String name, final String token, final Map<String, List<String>> input) {
        this.name = name;
        this.input = input;
        this.token = token;
        this.dbClient = dbClient;

        initRestClient();
    }
    

    private void initRestClient()
    {
        OrchestrationEngineRestClientFactory factory = new OrchestrationEngineRestClientFactory();

        factory.setMaxConnections(100);

        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();

        restClient = (OrchestrationEngineRestClient) factory.
                getRESTClient(URI.create(endPoint), "", "", true);

        primitive = PrimitiveHelper.query(name, RestPrimitive.class, dbClient);

        Set<String> extraHeaders = primitive.extraHeaders();
        Iterator it = extraHeaders.iterator();
        while(it.hasNext()) {
            restClient.setAuthHeaders(token, it.next().toString());
        }
        
        ExecutionUtils.currentContext().logInfo("Setting up REST Client to execute %s REST Primitive", name);
    }

    @Override
    public String executeTask() throws Exception {

        String result;
        String postBody = null;

        String uriDb = primitive.uri();
        String body = primitive.body();
        String method = primitive.method();

        if (OrchestrationServiceConstants.BODY_REST_METHOD.contains(method) && !body.isEmpty()) {
            postBody = makePostBody(body);
        }

        URI uri = makeUri(uriDb);

        ExecutionUtils.currentContext().logInfo("Started Executing REST API with uri:%s and POST Body:%s ", uri, postBody);

        result = makeRestCall(uri, postBody, method);

        ExecutionUtils.currentContext().logInfo("Done Executing REST Step. REST result:%s", result);

        return result;
    }
    

    private String makeRestCall(final URI uri, final String postBody, final String method) {

        ClientResponse response = null;
        OrchestrationServiceConstants.restMethods restmethod = OrchestrationServiceConstants.restMethods.valueOf(method);
        switch(restmethod) {
            case GET:
                response = restClient.get(uri);
                break;
            case PUT:
                response = restClient.put(uri, postBody);
                break;
            case POST:
                response = restClient.post(uri, postBody);
                break;
            case DELETE:
                response = restClient.delete(uri);
                break;
            default:
                logger.error("Unknown REST method type");
        }

        SuccessCriteria o = new SuccessCriteria();
        o.setReturnCode(response.getStatus());
        logger.info("Status of ViPR REST Operation:{} is :{}", name, response.getStatus());

        String responseString = null;
        try {
            responseString = IOUtils.toString(response.getEntityInputStream(), "UTF-8");
        } catch (IOException e) {
            error("Error getting response from Orchestration Engine for: " + uri +
                    " :: " + e.getMessage());
        }

        return responseString;
    }


    /**
     * Example uri: "/block/volumes/{id}/findname/{name}";
     * @param s
     * @return
     */
    private URI makeUri(String s) {
    
        Pattern p = Pattern.compile("(\\{(.*?)\\})");
        Matcher m = p.matcher(s);
        while (m.find()) {
            String val1 = m.group().replace("{", "").replace("}", "");

            s = (s.replace(m.group(), input.get(val1).get(0)));
            
        }
        UriTemplate template = new UriTemplate(OrchestrationServiceConstants.VIPR_REST_URI);
        URI restUri = template.expand(primitive.scheme(), endPoint, primitive.port(), s); 
        
        ExecutionUtils.currentContext().logInfo("URI string is: %s", restUri);

        return restUri;
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

