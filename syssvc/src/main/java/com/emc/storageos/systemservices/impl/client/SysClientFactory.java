/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.client;

import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.helpers.BaseServiceClient;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.exceptions.SyssvcException;
import com.emc.storageos.services.util.Exec;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import javax.ws.rs.core.MediaType;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Factory to create SysClient instance
 */
public class SysClientFactory {
    private static final Logger _log = LoggerFactory.getLogger(SysClient.class);
    public static final URI URI_GET_IMAGE = URI.create("/upgrade/internal/image");
    public static final URI URI_WAKEUP_UPGRADE_MANAGER = URI.create("/upgrade/internal/wakeup?type=upgrade");
    public static final URI URI_WAKEUP_SECRETS_MANAGER = URI.create("/upgrade/internal/wakeup?type=secrets");
    public static final URI URI_WAKEUP_PROPERTY_MANAGER = URI.create("/upgrade/internal/wakeup?type=property");
    public static final URI URI_WAKEUP_VDC_MANAGER = URI.create("/upgrade/internal/wakeup?type=vdc");
    public static final URI URI_GET_INTERNAL_NODE_HARDWARE = URI.create("/monitor/internal/node-hardware-info");
    public static final URI URI_NODE_LOGS = URI.create("/logs/internal/node-logs");
    public static final URI URI_LOG_LEVELS = URI.create("/logs/internal/log-level");
    public static final URI URI_RESTART_SERVICE = URI.create("/control/internal/service/restart");
    public static final URI URI_POWEROFF_NODE = URI.create("/control/internal/node/poweroff");
    public static final URI URI_REBOOT_NODE = URI.create("/control/internal/node/reboot");
    public static final URI URI_SEND_POWEROFF_AGREEMENT = URI.create("/control/internal/node/poweroff-agreement");
    public static final URI URI_NODE_BACKUPS_DOWNLOAD = URI.create("/backupset/internal/node-backups/download");
    public static final URI URI_NODE_BACKUPS_PULL = URI.create("/backupset/internal/pull");
    public static final URI URI_NODE_PULL_BACKUP_FILE = URI.create("/backupset/internal/pull-file");
    public static final URI URI_GET_PROPERTIES = URI.create("/config/internal/properties");
    public static final URI URI_GET_DBREPAIR_STATUS = URI.create("/control/internal/node/dbrepair-status");
    public static final String BASE_URL_FORMAT = "http://%1$s:%2$s";
    public static final String URI_NODE_BACKUPS_RESTORE_TEMPLATE =
            "/backupset/internal/restore?backupname=%s&isLocal=%s&password=%s&isgeofromscratch=%s";

    // URI for retrieving managed capacity for provisioning in apisvc
    public static final URI _URI_PROVISIONING_MANAGED_CAPACITY = URI.create
            ("/internal/system/managed-capacity");
    // URI for retrieving managed capacity for unstructured, waiting from data service team
    // for the new url
    public static final URI _URI_UNSTRUCTURED_MANAGED_CAPACITY = URI.create
            ("/internal/object/managed-objCapacity");

    /**
     * Coordinator client
     */
    private static volatile InternalApiSignatureKeyGenerator _keyGenerator;
    private static int _timeout;    // connection timeout
    private static int _readTimeout; // read timeout

    private static final String _IPCHECKTOOL_CMD = "/etc/storageos/ipchecktool";
    private static final int _IPCHECKTOOL_IP_CONFLICT = 3;
    private static final int _IPCHECKTOOL_NO_NODE = 4;
    private static final long _IPCHECKTOOL_TIMEOUT = 60000; // 1 min

    public static void setKeyGenerator(InternalApiSignatureKeyGenerator keyGenerator) {
        _keyGenerator = keyGenerator;
    }

    public static synchronized void setTimeout(int timeout) {
        _timeout = timeout;
        _readTimeout = timeout;
    }

    public static synchronized void init() {
        // nothing to do (remove this?)
    }

    public static synchronized SysClient getSysClient(URI endpoint, int readTimeout,
            int connectionTimeout) {
        _readTimeout = readTimeout;
        _timeout = connectionTimeout;
        return getSysClient(endpoint);
    }

    public static SysClient getSysClient(URI endpoint) {
        return new SysClient(endpoint);
    }

    public static class SysClient extends BaseServiceClient {
        private URI _endpoint;

        private SysClient(URI endpoint) {
            _endpoint = endpoint;
            setServer(endpoint.toString());
            setClientReadTimeout(_readTimeout);
            setClientConnectTimeout(_timeout);
            setKeyGenerator(_keyGenerator);
        }

        @Override
        public String toString() {
            return "endpoint=" + _endpoint + " timeout=" + _timeout + " readTimeout=" + _readTimeout;
        }

        @Override
        public void setServer(String server) {
            setServiceURI(_endpoint);
        }

        /**
         * Call this method to perform GET action on any URI.
         * 
         * @param getUri URI on which GET action is invoked.
         * @param returnType Response return type
         * @return response of the type passed.
         * @throws SysClientException
         */
        public <T> T get(URI getUri, Class<T> returnType,
                String acceptType) throws SysClientException {
            final WebResource webResource = createRequest(getUri);
            if (acceptType == null) {
                acceptType = MediaType.APPLICATION_JSON;
            }
            ClientResponse response;
            try {
                WebResource.Builder builder = addSignature(webResource).accept(acceptType);
                response = builder.get(ClientResponse.class);
            } catch (ClientHandlerException e) {
                checkNodeStatus(getUri, "GET");
                throw e;
            }
            final int status = response.getStatus();
            if (isSuccess(status)) {
                return response.getEntity(returnType);
            }
            else if (status == 204) {
                _log.debug("get returns, status code: {}", status);
                return null;
            }
            throw SyssvcException.syssvcExceptions.sysClientError(
                    MessageFormatter.arrayFormat("GET " +
                            "request on URI {} to node {} failed with status {}",
                            new Object[] { getUri, _endpoint.toString(),
                                    status }).getMessage());
        }

        /**
         * Call this method to perform POST action on any URI.
         * 
         * @param postUri URI on which POST action is invoked.
         * @param returnType Response return type
         * @return response of the type passed.
         * @throws SysClientException
         */
        public <T> T post(URI postUri, Class<T> returnType,
                Object requestBody) throws
                SysClientException {
            final WebResource webResource = createRequest(postUri);
            ClientResponse response;
            final WebResource.Builder resourceBuilder = addSignature(webResource);
            _log.info("webResource=" + webResource);
            try {
                if (requestBody == null) {
                    _log.info("RequestBody is null");
                    response = resourceBuilder.post(ClientResponse.class);
                } else {
                    _log.info("requestBody=" + requestBody);
                    response = resourceBuilder.post(ClientResponse.class, requestBody);
                }
                _log.info("response=" + response);
            } catch (ClientHandlerException e) {
                _log.info("ClientHandlerException: " + e);
                checkNodeStatus(postUri, "POST");
                throw e;
            }
            final int status = response.getStatus();

            if (!isSuccess(status)) {
                _log.info("response={}", response);
                throw SyssvcException.syssvcExceptions.sysClientError(
                        MessageFormatter.arrayFormat("POST request on URI {} to node {} failed with status {}",
                                new Object[] { postUri, _endpoint.toString(), status }).getMessage());
            }

            return returnType != null ? response.getEntity(returnType) : null;
        }

        /**
         * Call this method in case GET/POST fails to find the cause
         * 
         * @param uri URI on which POST/GET action is invoked
         * @param uriCmd POST or GET to be indicated in the message
         * @throws SysClientException
         */
        private void checkNodeStatus(URI uri, String uriCmd) throws SysClientException {
            _log.info("Entering SysClientFactory.checkNodeStatus()");
            String nodeIP;
            try {
                _log.info("Before InetAddress.getByName()");
                nodeIP = InetAddress.getByName(_endpoint.getHost()).getHostAddress();
                _log.info("after InetAddress.getByName()");
            } catch (UnknownHostException e) {
                _log.info(" request on URI {} to node {} failed to get node IP address {}",
                        new Object[] { uri, _endpoint.toString(), e.getMessage() });

                throw SyssvcException.syssvcExceptions.sysClientError(
                        MessageFormatter.arrayFormat(uriCmd + " request on URI {} to node {} failed to get node IP address {}",
                                new Object[] { uri, _endpoint.toString(), e.getMessage() }).getMessage());
            }

            _log.info("out first try catch");
            final String[] cmd = { _IPCHECKTOOL_CMD, "--ip", nodeIP };
            _log.info("get cmd");
            Exec.Result result = Exec.sudo(_IPCHECKTOOL_TIMEOUT, cmd);
            _log.info("Exec.Result");
            if (result.getExitValue() == _IPCHECKTOOL_IP_CONFLICT) {
                _log.info(" request on URI {} to node {} failed due to IP conflict at {}",
                        new Object[] { uri, _endpoint.toString(), nodeIP });

                throw SyssvcException.syssvcExceptions.sysClientError(
                        MessageFormatter.arrayFormat(uriCmd +
                                " request on URI {} to node {} failed due to IP conflict at {}",
                                new Object[] { uri, _endpoint.toString(),
                                        nodeIP }).getMessage());
            } else if (result.getExitValue() == _IPCHECKTOOL_NO_NODE) {
                _log.info(" request on URI {} to node {} failed due to node with IP {}  is down.",
                        new Object[] { uri, _endpoint.toString(),
                                nodeIP });
                throw SyssvcException.syssvcExceptions.sysClientError(
                        MessageFormatter.arrayFormat(uriCmd +
                                " request on URI {} to node {} failed due to node with IP {}  is down.",
                                new Object[] { uri, _endpoint.toString(),
                                        nodeIP }).getMessage());
            }
            _log.info("out of Check status");
        }

        private boolean isSuccess(int status) {
            return (status == 200 || status == 202);
        }
    }
}
