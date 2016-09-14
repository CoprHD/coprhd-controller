package com.emc.sa.service.vipr.oe.tasks;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.service.vipr.oe.OrchestrationService;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.RESTApiFormat;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClient;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClientFactory;
import com.emc.vipr.client.ViPRCoreClient;
import org.apache.commons.io.IOUtils;
import com.sun.jersey.api.client.ClientResponse;
import com.emc.sa.service.vipr.oe.gson.OEJson;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by sonalisahu on 9/7/16.
 */
public class RunREST extends ViPRExecutionTask<String>

{
    private OrchestrationEngineRestClient restClient;
   OEJson.Step step;
    HashMap<String, Map<String, String>> input;
    ModelClient modelClient;
    String opName;
    String postBody = null;
    String uri;

    public RunREST(String uri, String postBody) {


        //init rest client
        OrchestrationEngineRestClientFactory factory = new OrchestrationEngineRestClientFactory();

        factory.setMaxConnections(100);

        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = "http://localhost:4443";
        restClient = (OrchestrationEngineRestClient) factory.
                getRESTClient(URI.create(endpoint), "root", "ChangeMe1!", true);
        this.uri = uri;
        this.postBody = postBody;
    }

    @Override
    public String executeTask() throws Exception 
    {
	String RESTresult = null;
        if (step.getType().equals("ViPR REST")) {

    //        List<NamedElementQueryResultList.NamedElement> results = modelClient.findByAlternateId(RESTApiFormat.class, "RESTOp", "createvolume");

   //         for(NamedElementQueryResultList.NamedElement result : results) {
    //            RESTApiFormat restapiformat = modelClient.findById(RESTApiFormat.class, result.getId());
      //          String RESTformat = restapiformat.getRESTFormat();
		RESTresult = makeRestCall(uri, postBody);
        //    }

        } else {
            //Parse and get the API
        }

        //strore result to ViPR DB

        return RESTresult;
    }
    private String makeRestCall(String uriString, String postBody) {
        info("OE request uri: " + uriString);

        ClientResponse response = null;
        if(postBody == null) {
            response = restClient.get(uri(uriString));
        } else {
            info("OE request post body: " + postBody);
            response = restClient.post(uri(uriString),postBody);
        }

        String responseString = null;
        try {
            responseString = IOUtils.toString(response.getEntityInputStream(),"UTF-8");
        } catch (IOException e) {
            error("Error getting response from Orchestration Engine for: " + uriString +
                    " :: "+ e.getMessage());
            e.printStackTrace();
        }

        ExecutionUtils.currentContext().logInfo("Done Executing WF. Response:" +
                responseString + " using API call " + uriString);
        return responseString;
    }
}
