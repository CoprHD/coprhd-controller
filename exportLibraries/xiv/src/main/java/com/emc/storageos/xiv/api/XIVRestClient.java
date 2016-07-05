/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xiv.api;

import java.net.URI;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.services.restutil.StandardRestClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;

/**
 * Performs all the operations on XIV - Hyperscale Manager using its REST APIs
 */
public class XIVRestClient extends StandardRestClient {

    private static Logger _log = LoggerFactory.getLogger(XIVRestClient.class);
    private static final String ERROR_CODE = "httpStatusCode";

    private static final String SEPARATOR = ":";
    private static final String NAME = "name";
    private static final String VOLUME = "volume";
    private static final String LUN = "lun";
    private static final String STATUS_EXECUTION_PASS = "0";
    private static final String STATUS = "status";
    private static final String RESPONSE = "response";
    private static final String COUNTS = "counts";
    private static final String DATACOUNT = "data_count";
    private static final String TOTALCOUNT = "total_count";
    private static final String DATA = "data";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String HOSTPORT = "host_port";
    private static final String VOLMAP = "vol_map";
    private static final String CLUSTER = "cluster";
    private static final String WWN = "wwn";
    private static final String MESSAGE = "message";
    private static final String SERVER = "server";
    private static final String FAILED_SYSTEMS = "failed_systems";

    private static String INSTANCE_URL = "/:{1}";
    private static String SEARCH_URL = "?{1}={2}";
    private static String CLUSTER_URL = "/xiv/v2/:{0}/clusters";
    private static String HOST_URL = "/xiv/v2/:{0}/hosts";
    private static String HOST_PORT_URL = "/xiv/v2/:{0}/host_ports";
    private static String VOLUME_URL = "/xiv/v2/:{0}/volumes";
    private static String EXPORT_VOLUME_URL = "/xiv/v2/:{0}/vol_maps";
    private static String EXPORT_VOLUME_INSTANCE_URL = EXPORT_VOLUME_URL + "/:{1}:{2}:{3}";
    private static String CLUSTER_INSTANCE_URL = CLUSTER_URL + INSTANCE_URL;
    private static String HOST_INSTANCE_URL = HOST_URL + INSTANCE_URL;
    private static String HOST_PORT_INSTANCE_URL = HOST_PORT_URL + "/:{1}:{2}:{3}";
    private static String VOLUME_INSTANCE_URL = VOLUME_URL + INSTANCE_URL;

    private static String DELETE_BODY = "{\"request\":[{\"action\":\"delete\"}]}";
    private static String CLUSTER_CREATE_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"name\":\"{0}\"'}}']'}'";
    private static String HOST_CREATE_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"name\":\"{0}\"'}}']'}'";
    private static String HOST_CREATE_ON_CLUSTER_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"name\":\"{0}\",\"cluster\":\"{1}\"'}}']'}'";
    private static String HOST_PORT_CREATE_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"port\":\"{1}\",\"host\":\"{0}\",\"type\":\"{2}\"'}}']'}'";
    private static String EXPORT_VOLUME_TO_CLUSTER_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"volume\":\"{1}\",\"host_cluster_name\":\"{0}\",\"map_type\":\"cluster\",\"lun\":\"{2}\"}}]}";
    private static String EXPORT_VOLUME_TO_HOST_BODY = "'{'\"request\":['{'\"action\":\"create\",\"params\":'{'\"volume\":\"{1}\",\"host_cluster_name\":\"{0}\",\"map_type\":\"host\",\"lun\":\"{2}\"}}]}";

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
                    if (null != response) {
                        JSONObject status = response.optJSONObject(STATUS);
                        if (null != status) {
                            JSONObject server = status.optJSONObject(SERVER);
                            if (null != server) {
                                responseStatus = server.optString(STATUS);
                                responseMessage = server.optString(MESSAGE);
                                if (!STATUS_EXECUTION_PASS.equals(responseStatus)) {
                                    failed = true;
                                }
                            }
                            if (!failed) {
                                failedSystems = status.optJSONArray(FAILED_SYSTEMS);
                                if (null != failedSystems && failedSystems.length() > 0) {
                                    failed = true;
                                }
                            }
                        }
                        JSONObject responseObj = response.optJSONObject(RESPONSE);
                        if (!failed && null != responseObj) {
                            JSONObject counts = responseObj.optJSONObject(COUNTS);
                            if (null != counts) {
                                String datacount = counts.optString(DATACOUNT);
                                String totalcount = counts.optString(TOTALCOUNT);
                                if (STATUS_EXECUTION_PASS.equals(datacount) || STATUS_EXECUTION_PASS.equals(totalcount)) {
                                    failed = true;
                                }
                            }
                        }
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
     * Constructor of XIVRESTOperations
     * 
     * @param endpoint Base URI of Hyperscale Manager
     * @param client REST Client instance
     */

    /**
     * 
     * @param baseURI Base URI of Hyperscale Manager
     * @param username user name of XIV
     * @param password password of XIV
     * @param client REST Client instance
     */
    public XIVRestClient(URI baseURI, String username, String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _client.addFilter(new HTTPBasicAuthFilter(username, password));
    }

    /**
     * Sets User Name
     * 
     * @param username User Name of XIV
     */
    public void setUsername(String username) {
        _username = username;
    }

    /**
     * Sets password
     * 
     * @param password Password of XIV
     */
    public void setPassword(String password) {
        _password = password;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.services.restutil.StandardRestClient#setResourceHeaders(com.sun.jersey.api.client.WebResource)
     */
    @Override
    protected Builder setResourceHeaders(WebResource resource) {
        return resource.getRequestBuilder();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.services.restutil.StandardRestClient#authenticate()
     */
    @Override
    protected void authenticate() {
        _client.addFilter(new HTTPBasicAuthFilter(_username, _password));
        if (_log.isDebugEnabled()) {
            _client.addFilter(new LoggingFilter(System.out));
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.services.restutil.StandardRestClient#checkResponse(java.net.URI, com.sun.jersey.api.client.ClientResponse)
     */
    @Override
    protected int checkResponse(URI uri, ClientResponse response) {
        ClientResponse.Status status = response.getClientResponseStatus();
        return status.getStatusCode();
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
            response = get(_base.resolve(uri));
            instance = response.getEntity(JSONObject.class);
        } catch (Exception e) {
            throw XIVRestException.exceptions.xivRestRequestFailure(uri, response.getStatus());
        }
        return instance;
    }

    /**
     * Executes POST request. Can be used to Create/Delete/Update instance on XIV using the URI
     * 
     * @param xivSystem XIV System name on which the instance needs to be created
     * @param uri URI representing the Instance base path
     * @param jsonBody Create body in JSON format
     * @return ResponseValidator of the create operation
     * @throws Exception Exception Exception If error occurs during execution
     */
    private ResponseValidator executePOSTRequest(final String xivSystem, final String uri, final String jsonBody) throws Exception {
        ResponseValidator failureStatus = new ResponseValidator();
        ClientResponse response = null;
        try {
            response = post(_base.resolve(uri), jsonBody);
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
     * @throws Exception Throws Exception If error occurs during execution
     */
    public boolean createCluster(final String xivSystem, final String clusterName) throws Exception {
        boolean isAvailable = findAvailability(MessageFormat.format(CLUSTER_INSTANCE_URL, xivSystem, clusterName));
        if (isAvailable) {
            _log.info("Cluster {} already exist on XIV {}. Skipping creation!", clusterName, xivSystem);
        } else {
            final String body = MessageFormat.format(CLUSTER_CREATE_BODY, clusterName);
            ResponseValidator failureStatus = executePOSTRequest(xivSystem, MessageFormat.format(CLUSTER_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.clusterCreationFailure(xivSystem, clusterName, failureStatus.toString());
            }
        }
        return isAvailable;
    }

    /**
     * Deletes cluster if its is present only if the underlying structure is deleted
     * 
     * @param xivSystem XIV system that hosts the cluster
     * @param clusterName Cluster name
     * @return True if deleted. Else false.
     * @throws Exception Throws Exception If error occurs during execution
     */
    public boolean deleteCluster(final String xivSystem, final String clusterName) throws Exception {
        final String instanceURL = MessageFormat.format(CLUSTER_INSTANCE_URL, xivSystem, clusterName);
        boolean deleteSuccessful = false;
        if (findAvailability(instanceURL)) {
            // Validate if there are any Hosts associated with the cluster.
            boolean isHostsAvailable = findAvailability(MessageFormat.format(HOST_URL + SEARCH_URL, xivSystem, CLUSTER, clusterName));

            if (!isHostsAvailable) {
                // Now go ahead and delete Cluster.
                ResponseValidator failureStatus = executePOSTRequest(xivSystem, instanceURL, DELETE_BODY);
                deleteSuccessful = true;
                if (failureStatus.isFailed()) {
                    throw XIVRestException.exceptions.clusterDeleteFailure(xivSystem, clusterName, failureStatus.toString());
                }
            } else {
                _log.warn("There are some more Hosts associated with the Cluster {}. Skipping deletion.", clusterName);
            }
        } else {
            throw XIVRestException.exceptions.instanceUnavailableForDelete(xivSystem, CLUSTER, clusterName);
        }
        return deleteSuccessful;
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
        boolean isAvailable = findAvailability(MessageFormat.format(HOST_INSTANCE_URL, xivSystem, hostName));
        if (isAvailable) {
            _log.info("Host {} already exist on XIV {}. Skipping creation!", hostName, xivSystem);
        } else {
            String body = null;
            if (null != clusterName) {
                body = MessageFormat.format(HOST_CREATE_ON_CLUSTER_BODY, hostName, clusterName);
            } else {
                body = MessageFormat.format(HOST_CREATE_BODY, hostName);
            }
            ResponseValidator failureStatus = executePOSTRequest(xivSystem, MessageFormat.format(HOST_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.hostCreationFailure(xivSystem, hostName, failureStatus.toString());
            }
        }
        return isAvailable;
    }

    /**
     * Deletes Host on the XIV if underlying instances are deleted
     * 
     * @param xivSystem XIV system
     * @param hostName Host name to be deleted
     * @return True if deleted. Else false.
     * @throws Exception Throws Exception If error occurs during execution
     */
    public boolean deleteHost(final String xivSystem, final String hostName) throws Exception {
        final String instanceURL = MessageFormat.format(HOST_INSTANCE_URL, xivSystem, hostName);
        boolean deleteSuccessful = false;
        if (findAvailability(instanceURL)) {
            // Validate if there are any volumes mapped before deleting.
            boolean isVolumeExportAvailable = findAvailability(
                    MessageFormat.format(EXPORT_VOLUME_URL + SEARCH_URL, xivSystem, HOST, hostName));

            if (!isVolumeExportAvailable) {
                // Validate if there are any Initiators associated with Host before deleting.
                boolean isInitiatorAvailable = findAvailability(
                        MessageFormat.format(HOST_PORT_URL + SEARCH_URL, xivSystem, HOST, hostName));

                if (!isInitiatorAvailable) {
                    // Now go ahead and delete Host.
                    ResponseValidator failureStatus = executePOSTRequest(xivSystem, instanceURL, DELETE_BODY);
                    deleteSuccessful = true;
                    if (failureStatus.isFailed()) {
                        throw XIVRestException.exceptions.hostDeleteFailure(xivSystem, hostName, failureStatus.toString());
                    }
                } else {
                    _log.warn("There are Initiators available on Host {}. Skipping deletion.", hostName);
                }
            } else {
                _log.warn("There are some more Volume exported to Host {}. Skipping deletion.", hostName);
            }
        } else {
            throw XIVRestException.exceptions.instanceUnavailableForDelete(xivSystem, HOST, hostName);
        }
        return deleteSuccessful;
    }

    /**
     * Creates Host Port (Initiator)
     * 
     * @param xivSystem XIV system
     * @param hostName Host Name
     * @param hostPort Host Port name
     * @param hostPortType Host Port type
     * @return True if already present. Else False
     * @throws Exception Exception Exception Exception If error occurs during execution
     */
    public boolean createHostPort(final String xivSystem, final String hostName, final String hostPort, final String hostPortType)
            throws Exception {
        boolean isAvailable = findAvailability(MessageFormat.format(HOST_PORT_INSTANCE_URL, xivSystem, hostPortType, hostName, hostPort));
        if (isAvailable) {
            _log.info("HostPort {} already exist on XIV {}. Skipping creation!", hostPort, xivSystem);
        } else {
            final String body = MessageFormat.format(HOST_PORT_CREATE_BODY, hostName, hostPort, hostPortType);
            ResponseValidator failureStatus = executePOSTRequest(xivSystem, MessageFormat.format(HOST_PORT_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.hostPortCreationFailure(xivSystem, hostName, hostPort, failureStatus.toString());
            }
        }
        return isAvailable;
    }

    /**
     * Deletes Host Port on the Host if all the underlying instances are deleted.
     * 
     * @param xivSystem XIV System
     * @param hostName Host name where the port exists
     * @param hostPort Host port name
     * @param hostPortType Host port type
     * @param forceDelete Force delete a Host Port
     * @return True if it is deleted. Else false.
     * @throws Exception Throws Exception If error occurs during execution
     */
    public boolean deleteHostPort(final String xivSystem, final String hostName, final String hostPort, final String hostPortType, final Boolean forceDelete)
            throws Exception {
        final String instanceURL = MessageFormat.format(HOST_PORT_INSTANCE_URL, xivSystem, hostPortType, hostName, hostPort);
        boolean deleteSuccessful = false;
        if (findAvailability(instanceURL)) {
            // Validate if there are any volumes mapped before deleting.
            if (forceDelete || !findAvailability(MessageFormat.format(EXPORT_VOLUME_URL + SEARCH_URL, xivSystem, HOST, hostName))) {
                // Now go ahead and delete HostPort.
                ResponseValidator failureStatus = executePOSTRequest(xivSystem, instanceURL, DELETE_BODY);
                deleteSuccessful = true;
                if (failureStatus.isFailed()) {
                    throw XIVRestException.exceptions.hostPortDeleteFailure(xivSystem, hostName, hostPort, failureStatus.toString());
                }
            } else {
                _log.warn("There are some more Volume exported to Host {}. Skipping Host Port deletion.", hostName);
            }
        } else {
            throw XIVRestException.exceptions.instanceUnavailableForDelete(xivSystem, HOSTPORT, hostName + SEPARATOR + hostPort);
        }
        return deleteSuccessful;
    }

    /**
     * Exports a volume to a Cluster
     * 
     * @param xivSystem XIV System
     * @param exportType Cluster or Host
     * @param exportName Cluster name/Host name to where volume is exported
     * @param volumeName Name of the volume to be exported
     * @param lunID LUN number to be assigned
     * @return True if Volume is already exported to a specific Cluster. Else false.
     * @throws Exception Exception Exception Exception If error occurs during execution
     */
    public boolean exportVolume(final String xivSystem, final String exportType, final String exportName, final String volumeName,
            final String lunID)
            throws Exception {
        boolean isAvailable = findAvailability(
                MessageFormat.format(EXPORT_VOLUME_INSTANCE_URL, xivSystem, exportType.toLowerCase(), exportName, volumeName));
        if (isAvailable) {
            _log.info("Volume {} already already exported to {} {} on XIV {}. Skipping Export!", volumeName, exportType, exportName,
                    xivSystem);
        } else {
            String body = null;
            if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType)) {
                body = MessageFormat.format(EXPORT_VOLUME_TO_CLUSTER_BODY, exportName, volumeName, lunID);
            } else {
                body = MessageFormat.format(EXPORT_VOLUME_TO_HOST_BODY, exportName, volumeName, lunID);
            }
            ResponseValidator failureStatus = executePOSTRequest(xivSystem, MessageFormat.format(EXPORT_VOLUME_URL, xivSystem), body);

            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.volumeExportToClusterFailure(xivSystem, exportName, volumeName,
                        failureStatus.toString());
            }
        }
        return isAvailable;
    }

    /**
     * Un exports the volume masked to Host/Cluster
     * 
     * @param xivSystem XIV system
     * @param exportType Export type [Cluster/Host]
     * @param exportName Export name
     * @param volumeName Volume name
     * @return True if unexport is successful. Else false.
     * @throws Exception Throws Exception If error occurs during execution
     */
    public boolean unExportVolume(final String xivSystem, final String exportType, final String exportName, final String volumeName)
            throws Exception {
        final String instanceURL = MessageFormat.format(EXPORT_VOLUME_INSTANCE_URL, xivSystem, exportType.toLowerCase(), exportName,
                volumeName);
        boolean deleteSuccessful = false;
        if (findAvailability(instanceURL)) {
            ResponseValidator failureStatus = executePOSTRequest(xivSystem, instanceURL, DELETE_BODY);
            deleteSuccessful = true;
            if (failureStatus.isFailed()) {
                throw XIVRestException.exceptions.volumeExportToClusterFailure(xivSystem, exportName, volumeName, failureStatus.toString());
            }
        } else {
            _log.info("Volume not available to do unexport on XIV {} : {}", exportType + SEPARATOR + exportName + SEPARATOR + volumeName,
                    xivSystem);
        }
        return deleteSuccessful;
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
        final String hostURI = MessageFormat.format(HOST_INSTANCE_URL, xivSystem, hostName);
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

    /**
     * Gets Host port for a Host on XIV
     * 
     * @param xivSystem XIV system
     * @param hostName Host name
     * @return Collection of Host Port names
     * @throws Exception Throws Exception If error occurs during execution
     */
    public Set<String> getHostPorts(final String xivSystem, final String hostName) throws Exception {
        String hostPortSearchURL = MessageFormat.format(HOST_PORT_URL + SEARCH_URL, xivSystem, HOST, hostName);
        JSONObject hostPortsInstance = getInstance(hostPortSearchURL);
        Set<String> discHostPorts = new HashSet<String>();

        if (findAvailability(hostPortsInstance)) {
            JSONObject response = hostPortsInstance.getJSONObject(RESPONSE);
            JSONObject data = response.getJSONObject(DATA);
            JSONArray hostPorts = data.getJSONArray(HOSTPORT);
            int portsize = hostPorts.length();
            for (int i = 0; i < portsize; i++) {
                JSONObject hostPort = hostPorts.getJSONObject(i);
                discHostPorts.add(hostPort.getString(NAME));
            }
        }
        return discHostPorts;
    }

    /**
     * Gets Volumes mapped on the XIV
     * 
     * @param xivSystem XIV system
     * @param clusterName Cluster name
     * @param hostName Host name
     * @return Map of Volume and LUN id
     * @throws Exception Throws Exception If error occurs during execution
     */
    public Map<String, Integer> getVolumesMappedToHost(final String xivSystem, final String clusterName, final String hostName)
            throws Exception {
        Map<String, Integer> discVolsMappedToCluster = new HashMap<String, Integer>();
        Map<String, Integer> discVolsMappedToHost = new HashMap<String, Integer>();
        Map<String, Integer> discVolWWNMappedToHost = new HashMap<String, Integer>();

        if (null != clusterName && !clusterName.isEmpty()) {
            String clusterVolsSearchURL = MessageFormat.format(EXPORT_VOLUME_URL + SEARCH_URL, xivSystem, CLUSTER, clusterName);
            JSONObject volsMappedToClusterInstance = getInstance(clusterVolsSearchURL);
            if (findAvailability(volsMappedToClusterInstance)) {
                JSONObject cluResponse = volsMappedToClusterInstance.getJSONObject(RESPONSE);
                JSONObject cluData = cluResponse.getJSONObject(DATA);
                JSONArray mappedVolumes = cluData.getJSONArray(VOLMAP);
                int mappedVolumessize = mappedVolumes.length();
                for (int i = 0; i < mappedVolumessize; i++) {
                    JSONObject mappedVolume = mappedVolumes.getJSONObject(i);
                    discVolsMappedToHost.put(mappedVolume.getString(VOLUME), new Integer(mappedVolume.getString(LUN)));
                }
            }
        } else if (null != hostName && !hostName.isEmpty()) {
            final String hostURL = MessageFormat.format(HOST_INSTANCE_URL, xivSystem, hostName);
            JSONObject hostInstance = getInstance(hostURL);
            if (findAvailability(hostInstance)) {
                JSONObject response = hostInstance.getJSONObject(RESPONSE);
                JSONObject data = response.getJSONObject(DATA);
                JSONObject host = data.getJSONObject(HOST);
                final String hostCluster = host.getString(CLUSTER);
                if (null != hostCluster && !hostCluster.isEmpty()) {
                    String clusterVolsSearchURL = MessageFormat.format(EXPORT_VOLUME_URL + SEARCH_URL, xivSystem, CLUSTER, hostCluster);
                    JSONObject volsMappedToClusterInstance = getInstance(clusterVolsSearchURL);
                    if (findAvailability(volsMappedToClusterInstance)) {
                        JSONObject cluResponse = volsMappedToClusterInstance.getJSONObject(RESPONSE);
                        JSONObject cluData = cluResponse.getJSONObject(DATA);
                        JSONArray mappedVolumes = cluData.getJSONArray(VOLMAP);
                        int mappedVolumessize = mappedVolumes.length();
                        for (int i = 0; i < mappedVolumessize; i++) {
                            JSONObject mappedVolume = mappedVolumes.getJSONObject(i);
                            discVolsMappedToCluster.put(mappedVolume.getString(VOLUME), new Integer(mappedVolume.getString(LUN)));
                        }
                    }
                }
            }

            String hostPortSearchURL = MessageFormat.format(EXPORT_VOLUME_URL + SEARCH_URL, xivSystem, HOST, hostName);
            JSONObject volsMappedToHostInstance = getInstance(hostPortSearchURL);
            if (findAvailability(volsMappedToHostInstance)) {
                JSONObject response = volsMappedToHostInstance.getJSONObject(RESPONSE);
                JSONObject data = response.getJSONObject(DATA);
                JSONArray mappedVolumes = data.getJSONArray(VOLMAP);
                int mappedVolumessize = mappedVolumes.length();
                for (int i = 0; i < mappedVolumessize; i++) {
                    JSONObject mappedVolume = mappedVolumes.getJSONObject(i);
                    discVolsMappedToHost.put(mappedVolume.getString(VOLUME), new Integer(mappedVolume.getString(LUN)));
                }
            }

            // Remove the Cluster Volumes as it belongs to Cluster Mask.
            if (!discVolsMappedToHost.isEmpty() && !discVolsMappedToCluster.isEmpty()) {
                for (String key : discVolsMappedToCluster.keySet()) {
                    discVolsMappedToHost.remove(key);
                }
            }
        }

        if (!discVolsMappedToHost.isEmpty()) {
            Set<Entry<String, Integer>> discVolsMappedToHostSet = discVolsMappedToHost.entrySet();
            for (Entry<String, Integer> volMapping : discVolsMappedToHostSet) {
                String volName = volMapping.getKey();
                String volInstanceURL = MessageFormat.format(VOLUME_INSTANCE_URL, xivSystem, volName);
                JSONObject volInstance = getInstance(volInstanceURL);
                JSONObject response = volInstance.getJSONObject(RESPONSE);
                JSONObject data = response.getJSONObject(DATA);
                JSONObject volumeData = data.getJSONObject(VOLUME);
                discVolWWNMappedToHost.put(volumeData.getString(WWN), volMapping.getValue());
            }
        }

        return discVolWWNMappedToHost;
    }

    /**
     * Returns Port details for a port name specified if exist on Array
     * 
     * @param xivSystem XIV Storage System name
     * @param portName Port name for which the details to be found
     * @return Container (HOST) name.
     * @throws Exception If error occurs during execution.
     */
    public String getHostPortContainer(final String xivSystem, final String portName) throws Exception {
        String containerName = null;
        String hostPortSearchURL = MessageFormat.format(HOST_PORT_URL + SEARCH_URL, xivSystem, PORT, portName);
        JSONObject hostPortInstances = getInstance(hostPortSearchURL);

        if (findAvailability(hostPortInstances)) {
            JSONObject response = hostPortInstances.optJSONObject(RESPONSE);
            if (null != response) {
                JSONObject data = response.optJSONObject(DATA);
                if (null != data) {
                    JSONArray portResult = data.optJSONArray(HOSTPORT);
                    if (null != portResult) {
                        for (int i = 0; i < portResult.length(); i++) {
                            JSONObject resultObj = portResult.getJSONObject(i);
                            final String hostName = resultObj.getString("host");
                            if (null != hostName && !hostName.isEmpty()) {
                                containerName = hostName;
                            }
                        }
                    }
                }
            }
        }
        return containerName;
    }

    /**
     * Returns Host details for a name specified if exist on Array
     * 
     * @param xivSystem XIV Storage System name
     * @param hostName Host name for which the details to be found
     * @return Container (CLUSTER) name.
     * @throws Exception If error occurs during execution.
     */

    public String getHostContainer(final String xivSystem, final String hostName) throws Exception {
        String containerName = null;
        final String hostURI = MessageFormat.format(HOST_INSTANCE_URL, xivSystem, hostName);
        JSONObject hostInstance = getInstance(hostURI);

        if (findAvailability(hostInstance)) {
            JSONObject response = hostInstance.getJSONObject(RESPONSE);
            JSONObject data = response.getJSONObject(DATA);
            JSONObject host = data.getJSONObject(HOST);
            final String hostCluster = host.getString(CLUSTER);
            if (null != hostCluster && !hostCluster.isEmpty()) {
                containerName = hostCluster;
            }
        }
        return containerName;
    }

}
