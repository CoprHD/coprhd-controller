package com.emc.storageos.xiv.api;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

public class XIVRESTExportOperations {

    private static Logger _log = LoggerFactory.getLogger(XIVRESTExportOperations.class);
    private final RESTClient _client;
    private final URI _baseURI;

    private static String SEPARATOR = ":";
    private static String NAME = "name";
    private static String STATUS_EXECUTION_PASS = "0";
    private static String STATUS = "status";
    private static String RESPONSE = "response";
    private static String DATA = "data";
    private static String HOST = "host";
    private static String CLUSTER = "cluster";
    private static String MESSAGE = "message";
    private static String SERVER = "server";
    private static String FAILED_SYSTEMS = "failed_systems";
    
    private static String INSTANCE_URL = "/:{1}";
    private static String CLUSTER_URL = "/xiv/v2/:{0}/clusters";
    private static String HOST_URL = "/xiv/v2/:{0}/hosts";
    private static String HOST_PORT_URL = "/xiv/v2/:{0}/host_ports";
    private static String EXPORT_VOLUME_URL = "/xiv/v2/:{0}/vol_maps";
    private static String EXPORT_VOLUME_TO_CLUSTER_INSTANCE_URL = EXPORT_VOLUME_URL + "/:cluster:{1}:{2}";
    private static String CLUSTER_INSTANCE_URL = CLUSTER_URL + INSTANCE_URL;
    private static String Host_INSTANCE_URL = HOST_URL + INSTANCE_URL;
    private static String Host_PORT_INSTANCE_URL = HOST_PORT_URL + INSTANCE_URL;
    
    private static String CLUSTER_CREATE_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"name\":\"{0}\"'}}']'}'";
    private static String HOST_CREATE_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"name\":\"{1}\",\"cluster\":\"{0}\"'}}']'}'";
    private static String HOST_PORT_CREATE_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"port\":\"{1}\",\"host\":\"{0}\",\"type\":\"{2}\"'}}']'}'";
    private static String EXPORT_VOLUME_TO_CLUSTER_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"volume\":\"{1}\",\"host_cluster_name\":\"{0}\",\"map_type\":\"cluster\",\"lun\":\"{2}\"}}]}";
    
    public enum HOST_STATUS {
        CLUSTER_HOST,
        STANDALONE_HOST,
        HOST_NOT_PRESENT
    }

    public XIVRESTExportOperations(URI endpoint, RESTClient client) {
        _baseURI = endpoint;
        _client = client;
    }

    class ResponseValidator {
        boolean failed;
        String responseStatus;
        String responseMessage;
        JSONArray failedSystems;

        ResponseValidator() {
            failed = false;
        }

        ResponseValidator(JSONObject response) throws Exception {
            validate(response);
        }

        void validate(JSONObject response) throws Exception {
            if (null != response) {
                try {
                    JSONObject status = response.getJSONObject(STATUS);
                    JSONObject server = status.getJSONObject(SERVER);
                    if (null != server) {
                        responseStatus = server.getString(STATUS);
                        responseMessage = server.getString(MESSAGE);
                        if (!STATUS_EXECUTION_PASS.equals(responseStatus)) {
                            failed = true;
                        }
                    }
                    failedSystems = status.getJSONArray(FAILED_SYSTEMS);
                    if (failedSystems.length() > 0) {
                        failed = true;
                    }
                } catch (Exception e) {
                    failed = true;
                    throw XIVRestException.exceptions.jsonParserFailure(response.toString());
                }
            }
        }

        boolean isFailed() {
            return failed;
        }

        @Override
        public String toString() {
            StringBuffer message = new StringBuffer();
            if (null != responseMessage && null != responseStatus) {
                message.append("Status : ").append(responseStatus);
                message.append("Message : ").append(responseMessage);
                message.append("\n");
            }
            if (null != failedSystems) {
                try {
                    for (int i = 0; i < failedSystems.length(); i++) {
                        JSONObject failedSystem = failedSystems.getJSONObject(i);
                        message.append(failedSystem.getString(NAME)).append(SEPARATOR).append(failedSystem.getString(MESSAGE));
                        message.append("\n");
                    }
                } catch (JSONException e) {
                    _log.error("Error occured parsing failure response message : {}", failedSystems.toString(), e);
                }
            }
            return message.toString();
        }
    }

    public boolean findAvailability(final String uri) throws Exception {
        JSONObject instance = null;
        ClientResponse response = null;
        try {
            response = _client.get(_baseURI.resolve(uri));
            instance = response.getEntity(JSONObject.class);
        } catch (Exception e) {
            throw XIVRestException.exceptions.xivRestRequestFailure(uri, response.getStatus());
        }
        ResponseValidator status = new ResponseValidator(instance);
        return !status.isFailed();
    }
    
    public ResponseValidator createInstance(final String xivSystem, final String uri, final String jsonBody) throws Exception {
        ResponseValidator failureStatus = new ResponseValidator();
        ClientResponse response = null;
        try {
            response = _client.post(_baseURI.resolve(uri), jsonBody);
            JSONObject arrayClusters = response.getEntity(JSONObject.class);
            failureStatus.validate(arrayClusters);
        } catch (Exception e) {
            throw XIVRestException.exceptions.xivRestRequestFailure(uri.toString(), response.getStatus());
        }
        return failureStatus;
    }

    public void createCluster(final String xivSystem, final String clusterName) throws Exception {
        if (findAvailability(MessageFormat.format(CLUSTER_INSTANCE_URL, xivSystem, clusterName))) {
            _log.info("Cluster {} already exist on XIV {}. Skipping creation!", clusterName, xivSystem);
        } else {
            final String body = MessageFormat.format(CLUSTER_CREATE_BODY, clusterName);
            ResponseValidator failureStatus = createInstance(xivSystem, MessageFormat.format(CLUSTER_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.clusterCreationFailure(xivSystem, clusterName, failureStatus.toString());
            }
        }
    }

    public void createHost(final String xivSystem, final String clusterName, final String hostName) throws Exception {
        if (findAvailability(MessageFormat.format(Host_INSTANCE_URL, xivSystem, hostName))) {
            _log.info("Host {} already exist on XIV {}. Skipping creation!", hostName, xivSystem);
        } else {
            final String body = MessageFormat.format(HOST_CREATE_BODY, clusterName, hostName);
            ResponseValidator failureStatus = createInstance(xivSystem, MessageFormat.format(HOST_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.hostCreationFailure(xivSystem, hostName, failureStatus.toString());
            }
        }
    }

    public void createHostPort(final String xivSystem, final String hostName, final String hostPort, final String hostPortType)
            throws Exception {
        if (findAvailability(MessageFormat.format(Host_PORT_INSTANCE_URL, xivSystem, hostPort))) {
            _log.info("HostPort {} already exist on XIV {}. Skipping creation!", hostPort, xivSystem);
        } else {
            final String body = MessageFormat.format(HOST_PORT_CREATE_BODY, hostName, hostPort, hostPortType);
            ResponseValidator failureStatus = createInstance(xivSystem, MessageFormat.format(HOST_PORT_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.hostCreationFailure(xivSystem, hostName, failureStatus.toString());
            }
        }
    }
    
    public void exportVolumeToCluster(final String xivSystem, final String clusterName, final String volumeName, final String lunID)
            throws Exception {
        if (findAvailability(MessageFormat.format(EXPORT_VOLUME_TO_CLUSTER_INSTANCE_URL, xivSystem, clusterName, volumeName))) {
            _log.info("Volume {} already already exported to Cluster {} on XIV {}. Skipping Export!", volumeName, clusterName, xivSystem);
        } else {
            final String body = MessageFormat.format(EXPORT_VOLUME_TO_CLUSTER_BODY, clusterName, volumeName, lunID);
            ResponseValidator failureStatus = createInstance(xivSystem, MessageFormat.format(EXPORT_VOLUME_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.volumeExportToClusterFailure(xivSystem, clusterName, volumeName, failureStatus.toString());
            }
        }
    }
    
    public HOST_STATUS getHostStatus(final String xivSystem, final String hostName) throws Exception{
        HOST_STATUS hostStatus = HOST_STATUS.HOST_NOT_PRESENT;
        final String hostURI = MessageFormat.format(Host_INSTANCE_URL, xivSystem, hostName);
        if (findAvailability(hostURI)) {
            hostStatus = HOST_STATUS.STANDALONE_HOST;
            JSONObject hostInstance = null;
            ClientResponse response = null;
            try {
                response = _client.get(_baseURI.resolve(hostURI));
                hostInstance = response.getEntity(JSONObject.class);
            } catch (Exception e) {
                throw XIVRestException.exceptions.xivRestRequestFailure(hostURI, response.getStatus());
            }
            
            JSONObject status = hostInstance.getJSONObject(RESPONSE);
            JSONObject data = status.getJSONObject(DATA);
            JSONObject host = data.getJSONObject(HOST);
            final String hostCluster = host.getString(CLUSTER);
            if(null!=hostCluster && !hostCluster.isEmpty()){
                hostStatus = HOST_STATUS.CLUSTER_HOST;
            }
        }
        return hostStatus;
    }
    

    public static void main(String[] args) throws Exception {

        // Setup the connection parameters.
        HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        params.setMaxTotalConnections(300);
        params.setDefaultMaxConnectionsPerHost(100);
        params.setConnectionTimeout(1000 * 30);
        params.setSoTimeout(1000 * 60 * 60);
        params.setTcpNoDelay(true);

        // Create the HTTP connection manager for managing the set of HTTP
        // connections and set the configuration parameters. Also, make sure
        // idle connections are closed immediately to prevent a buildup of
        // connections in the CLOSE_WAIT state.

        MultiThreadedHttpConnectionManager mgr = new MultiThreadedHttpConnectionManager();
        mgr.setParams(params);
        mgr.closeIdleConnections(0);

        HttpClient client = new HttpClient(mgr);
        client.getParams().setConnectionManagerTimeout(1000 * 60 * 60);
        client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
                new HttpMethodRetryHandler() {
                    @Override
                    public boolean retryMethod(HttpMethod httpMethod, IOException e, int i) {
                        return false;
                    }
                });

        Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), 4443));

        ApacheHttpClientHandler _clientHandler = new ApacheHttpClientHandler(client);
        Client jerseyClient = new ApacheHttpClient(_clientHandler);
        RESTClient restClient = new RESTClient(jerseyClient, "admin", "adminadmin");

        XIVRESTExportOperations storageDevice = new XIVRESTExportOperations(URI.create("https://lglou188.lss.emc.com:8443"), restClient);

        String xivSystem = "10.247.23.203";
        String clusterName = "Vineeth_Cluster_1";
        Map<String, List<String>> hostInitiatorMap = new HashMap<String, List<String>>();
        List<String> initiators = new ArrayList<String>();
        initiators.add("1000000000000001");
        initiators.add("1000000000000002");
        hostInitiatorMap.put("Vineeth_Host_1", initiators);
        
        /*storageDevice.createCluster(xivSystem, clusterName);
                
        Iterator<Entry<String, List<String>>> set = hostInitiatorMap.entrySet().iterator();
        while(set.hasNext()){
            Entry<String, List<String>> entry = set.next();
            storageDevice.createHost(xivSystem, clusterName, entry.getKey());
            List<String> ini = entry.getValue();
            for(String initiator : ini){
                storageDevice.createHostPort(xivSystem, entry.getKey(), initiator, "fc");
            }
        }*/
        
        //storageDevice.exportVolumeToCluster(xivSystem, clusterName, "Test_Private_LUN_toCluster", "4");
        
        storageDevice.getHostStatus(xivSystem, "Vineeth_Host_1");

        restClient.close();
    }
}
