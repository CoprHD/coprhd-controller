/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
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

/**
 * Performs all the Export operations on XIV - Hyperscale Manager using its REST APIs
 */
public class XIVRESTExportOperations {

    private static Logger _log = LoggerFactory.getLogger(XIVRESTExportOperations.class);
    private final RESTClient _client;
    private final URI _baseURI;

    private static final String ID = "id";
    private static final String SEPARATOR = ":";
    private static final String NAME = "name";
    private static final String STATUS_EXECUTION_PASS = "0";
    private static final String STATUS = "status";
    private static final String RESPONSE = "response";
    private static final String DATA = "data";
    private static final String HOST = "host";
    private static final String VOLMAP = "vol_map";
    private static final String CLUSTER = "cluster";
    private static final String MESSAGE = "message";
    private static final String SERVER = "server";
    private static final String FAILED_SYSTEMS = "failed_systems";

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

    /**
     * Host Status on XIV
     */
    public enum HOST_STATUS {
        CLUSTER_HOST,
        STANDALONE_HOST,
        HOST_NOT_PRESENT
    }

    /*
     * Hyperscale Manger REST response validator.
     * Used to find the Error state retured after REST operation.
     */
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

    /**
     * Constructor of XIVRESTExportOperations
     * 
     * @param endpoint Base URI of Hyperscale Manager
     * @param client REST Client instance
     */
    public XIVRESTExportOperations(URI endpoint, RESTClient client) {
        _baseURI = endpoint;
        _client = client;
    }

    /**
     * Find if the instance specified in URI is available on XIV system
     * 
     * @param uri Instance URI
     * @return true if present else false
     * @throws Exception If error occurs during execution
     */
    private boolean findAvailability(final String uri) throws Exception {
        return findAvailability(getInstance(uri));
    }

    /**
     * Find if the instance specified using JSONObject is available on XIV system
     * 
     * @param instance Instance represented as JSONObject
     * @return true if present else false
     * @throws Exception If error occurs during execution
     */
    private boolean findAvailability(final JSONObject instance) throws Exception {
        ResponseValidator status = new ResponseValidator(instance);
        return !status.isFailed();
    }

    /**
     * Gets Instance as JSONObject from the URI specified
     * 
     * @param uri Instance URI
     * @return Instance JSONObject
     * @throws Exception Exception If error occurs during execution
     */
    private JSONObject getInstance(final String uri) throws Exception {
        JSONObject instance = null;
        ClientResponse response = null;
        try {
            response = _client.get(_baseURI.resolve(uri));
            instance = response.getEntity(JSONObject.class);
        } catch (Exception e) {
            throw XIVRestException.exceptions.xivRestRequestFailure(uri, response.getStatus());
        }
        return instance;
    }

    /**
     * Creates instance on XIV using the URI
     * 
     * @param xivSystem XIV System name on which the instance needs to be created
     * @param uri URI representing the Instance base path
     * @param jsonBody Create body in JSON format
     * @return ResponseValidator of the create operation
     * @throws Exception Exception Exception If error occurs during execution
     */
    private ResponseValidator createInstance(final String xivSystem, final String uri, final String jsonBody) throws Exception {
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

    /**
     * Creates Cluster on XIV system
     * 
     * @param xivSystem XIV system
     * @param clusterName Cluster name
     * @return True if already present. Else False
     * @throws Exception Exception Exception Exception If error occurs during execution
     */
    public boolean createCluster(final String xivSystem, final String clusterName) throws Exception {
        boolean isAvailable = findAvailability(MessageFormat.format(CLUSTER_INSTANCE_URL, xivSystem, clusterName));
        if (isAvailable) {
            _log.info("Cluster {} already exist on XIV {}. Skipping creation!", clusterName, xivSystem);
        } else {
            final String body = MessageFormat.format(CLUSTER_CREATE_BODY, clusterName);
            ResponseValidator failureStatus = createInstance(xivSystem, MessageFormat.format(CLUSTER_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.clusterCreationFailure(xivSystem, clusterName, failureStatus.toString());
            }
        }
        return isAvailable;
    }

    /**
     * Creates Host on XIV system. Maps it to a Cluster
     * 
     * @param xivSystem xivSystem XIV system
     * @param clusterName clusterName Cluster name
     * @param hostName Host Name
     * @return True if already present. Else False
     * @throws Exception Exception Exception Exception If error occurs during execution
     */
    public boolean createHost(final String xivSystem, final String clusterName, final String hostName) throws Exception {
        boolean isAvailable = findAvailability(MessageFormat.format(Host_INSTANCE_URL, xivSystem, hostName));
        if (isAvailable) {
            _log.info("Host {} already exist on XIV {}. Skipping creation!", hostName, xivSystem);
        } else {
            final String body = MessageFormat.format(HOST_CREATE_BODY, clusterName, hostName);
            ResponseValidator failureStatus = createInstance(xivSystem, MessageFormat.format(HOST_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.hostCreationFailure(xivSystem, hostName, failureStatus.toString());
            }
        }
        return isAvailable;
    }

    /**
     * Creates Host Port (Initiator)
     * 
     * @param xivSystem xivSystem XIV system
     * @param hostName Host Name
     * @param hostPort Host Port name
     * @param hostPortType Host Port type
     * @return True if already present. Else False
     * @throws Exception Exception Exception Exception If error occurs during execution
     */
    public boolean createHostPort(final String xivSystem, final String hostName, final String hostPort, final String hostPortType)
            throws Exception {
        boolean isAvailable = findAvailability(MessageFormat.format(Host_PORT_INSTANCE_URL, xivSystem, hostPort));
        if (isAvailable) {
            _log.info("HostPort {} already exist on XIV {}. Skipping creation!", hostPort, xivSystem);
        } else {
            final String body = MessageFormat.format(HOST_PORT_CREATE_BODY, hostName, hostPort, hostPortType);
            ResponseValidator failureStatus = createInstance(xivSystem, MessageFormat.format(HOST_PORT_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.hostCreationFailure(xivSystem, hostName, failureStatus.toString());
            }
        }
        return isAvailable;
    }

    /**
     * Exports a volume to a Cluster
     * 
     * @param xivSystem XIV System
     * @param clusterName Cluster name to where volume is exported
     * @param volumeName Name of the volume to be exported
     * @param lunID LUN number to be assigned
     * @return True if Volume is already exported to a specific Cluster. Else false.
     * @throws Exception Exception Exception Exception If error occurs during execution
     */
    public boolean exportVolumeToCluster(final String xivSystem, final String clusterName, final String volumeName, final String lunID)
            throws Exception {
        boolean isAvailable = findAvailability(
                MessageFormat.format(EXPORT_VOLUME_TO_CLUSTER_INSTANCE_URL, xivSystem, clusterName, volumeName));
        if (isAvailable) {
            _log.info("Volume {} already already exported to Cluster {} on XIV {}. Skipping Export!", volumeName, clusterName, xivSystem);
        } else {
            final String body = MessageFormat.format(EXPORT_VOLUME_TO_CLUSTER_BODY, clusterName, volumeName, lunID);
            ResponseValidator failureStatus = createInstance(xivSystem, MessageFormat.format(EXPORT_VOLUME_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.volumeExportToClusterFailure(xivSystem, clusterName, volumeName,
                        failureStatus.toString());
            }
        }
        return isAvailable;
    }

    /**
     * Gets status of Host on XIV system
     * 
     * @param xivSystem XIV system
     * @param hostName Host name to be verified
     * @return HOST_STATUS
     * @throws Exception Exception Exception Exception If error occurs during execution
     */
    public HOST_STATUS getHostStatus(final String xivSystem, final String hostName) throws Exception {
        final String hostURI = MessageFormat.format(Host_INSTANCE_URL, xivSystem, hostName);
        HOST_STATUS hostStatus = HOST_STATUS.HOST_NOT_PRESENT;
        JSONObject hostInstance = getInstance(hostURI);

        if (findAvailability(hostInstance)) {
            hostStatus = HOST_STATUS.STANDALONE_HOST;
            JSONObject response = hostInstance.getJSONObject(RESPONSE);
            JSONObject data = response.getJSONObject(DATA);
            JSONObject host = data.getJSONObject(HOST);
            final String hostCluster = host.getString(CLUSTER);
            if (null != hostCluster && !hostCluster.isEmpty()) {
                hostStatus = HOST_STATUS.CLUSTER_HOST;
            }
        }
        return hostStatus;
    }

    // TODO : For dev testing. To be removed later.
    public static void main(String[] args) throws Exception {

        // Setup the connection parameters.
        HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        params.setMaxTotalConnections(300);
        params.setDefaultMaxConnectionsPerHost(100);
        params.setConnectionTimeout(1000 * 30);
        params.setSoTimeout(1000 * 60 * 60);
        params.setTcpNoDelay(true);

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

        /*
         * storageDevice.createCluster(xivSystem, clusterName);
         * 
         * Iterator<Entry<String, List<String>>> set = hostInitiatorMap.entrySet().iterator();
         * while(set.hasNext()){
         * Entry<String, List<String>> entry = set.next();
         * storageDevice.createHost(xivSystem, clusterName, entry.getKey());
         * List<String> ini = entry.getValue();
         * for(String initiator : ini){
         * storageDevice.createHostPort(xivSystem, entry.getKey(), initiator, "fc");
         * }
         * }
         */

        // storageDevice.exportVolumeToCluster(xivSystem, clusterName, "Test_Private_LUN_toCluster", "4");

        storageDevice.getHostStatus(xivSystem, "Vineeth_Host_1");

        restClient.close();
    }
}
