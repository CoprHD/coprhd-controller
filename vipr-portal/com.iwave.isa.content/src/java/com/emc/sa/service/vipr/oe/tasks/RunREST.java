package com.emc.sa.service.vipr.oe.tasks;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationService;
import com.emc.sa.service.vipr.oe.primitive.PrimitiveHelper;
import com.emc.sa.service.vipr.oe.primitive.RestPrimitive;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.OERestCall;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClient;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClientFactory;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sonalisahu on 10/20/16.
 */
public class RunREST extends ViPRExecutionTask<String> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrchestrationService.class);

    private String name;
    private Map<String, List<String>> input;
    private String token;
    private OrchestrationEngineRestClient restClient;

    private String endPoint = "localhost";
    private String userId = "root";
    private String password = "ChangeMe1!";
    private String protocol = "https";

    private URI id;
    private DbClient dbClient;

    public RunREST(final DbClient dbClient, final String name, final String token, final Map<String, List<String>> input)
    {
        this.name = name;
        this.input = input;
        this.token = token;
	this.dbClient = dbClient;

	logger.info("input is :{}", this.input);
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
                getRESTClient(URI.create(endPoint), userId, password, true);

        restClient.setAuthToken(token);

        ExecutionUtils.currentContext().logInfo("Setting up REST Client to execute" + name + "REST Primitive");
    }

    @Override
    public String executeTask() throws Exception {

        //final RestPrimitive primitive = PrimitiveHelper.query(name, RestPrimitive.class, dbClient);

        String result = null;
        String postBody = null;

        String uri_db = null;
        String body_db = null;
        String method = null;
        if (name.equals("createVolumes")) {
            uri_db = "/block/volumes.json";
            body_db = "{\n" +
                    "  \"consistency_group\": \"$consistency_group\",\n" +
                    "  \"count\": \"$count\",\n" +
                    "  \"name\": \"$name\",\n" +
                    "  \"project\": \"$project\",\n" +
                    "  \"size\": \"$size\",\n" +
                    "  \"varray\": \"$varray\",\n" +
                    "  \"vpool\": \"$vpool\"\n" +
                    "}";
            method = "POST";
        }
        else if (name.equals("deleteVolumes")) {
            uri_db = "/block/volumes/{id}/deactivate.json";
            body_db = "";
            method = "POST";
        }

	logger.info("Params for rest uri_db:{} method:{} body:{}", uri_db, method, body_db);
        //String uri = makeUri(primitive.uri());
        /*if (primitive.method().equals("POST"))
            postBody = makePostBody(primitive.body());
        */
        if (method.equals("POST") && !body_db.isEmpty())
        {
            postBody = makePostBody(body_db);
        }

	logger.info("Make URI uri_db:{}", uri_db);
        String uri = makeUri(uri_db);
	logger.info("uri is:{}", uri);

        ExecutionUtils.currentContext().logInfo("Started Executing REST API with uri:" +
                uri+ " and POST Body: " + postBody);

        result = makeRestCall(uri, postBody, method);

        ExecutionUtils.currentContext().logInfo("Done Executing REST Step. REST result:" + result);

        return result;
    }

    private String makeRestCall(final String uriString, final String postBody, final String method)
    {

        ClientResponse response = null;
        logger.info("Running RESTClient with uri:{} and postBody:{}", uriString, postBody);

        if(method.equals("GET"))
            response = restClient.get(uri(uriString));
        else
            response = restClient.post(uri(uriString), postBody);


        //SuccessCriteria o = new SuccessCriteria();
        //o.setCode(response.getStatus());
	logger.info("Status:{}", response.getStatus());

        String responseString = null;
        try {
            responseString = IOUtils.toString(response.getEntityInputStream(), "UTF-8");
        } catch (IOException e) {
            error("Error getting response from Orchestration Engine for: " + uriString +
                    " :: "+ e.getMessage());
            e.printStackTrace();
        }

        return responseString;
    }

    private String makeUri(String s)
    {
        ///block/volumes/{id} substitute in the path
        //Stris = "/block/volumes/{id}/findname/{name}";
        logger.info("Path is:{}", s);

        Pattern p = Pattern.compile("(\\{(.*?)\\})");
        Matcher m = p.matcher(s);
        while (m.find()) {
            String val1 = m.group().replace("{", "").replace("}", "");

            s = (s.replace(m.group(), input.get(val1).get(0)));
            logger.info("new path is:{}", s);
        }

        ExecutionUtils.currentContext().logInfo("URI string is:" + protocol + "://" + endPoint + ":" + "4443" + s);

        return protocol + "://" + endPoint + ":" + "4443" + s;
    }


    private String makePostBody(String body) {

        body =
                "{\n" +
                        "  \"consistency_group\": \"$consistency_group\",\n" +
                        "  \"count\": \"$count\",\n" +
                        "  \"name\": \"$name\",\n" +
                        "  \"project\": \"$project\",\n" +
                        "  \"size\": \"$size\",\n" +
                        "  \"varray\": \"$varray\",\n" +
                        "  \"vpool\": \"$vpool\"\n" +
                        "}";

        Matcher m = Pattern.compile("\\$(\\w+)").matcher(body);

        while (m.find()) {
            logger.info("in loop");
            String pat = m.group(1);
            logger.info(pat);
            logger.info("value from map" + input.get(pat));
            String newpat = "$" + pat;
            logger.info("New pat" + newpat);
            body = body.replace(newpat, input.get(pat).get(0));
        }

        logger.info("new body: {}", body);

        return body;
    }


}
