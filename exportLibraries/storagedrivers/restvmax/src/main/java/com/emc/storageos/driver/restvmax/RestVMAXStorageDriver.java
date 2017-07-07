/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;

public class RestVMAXStorageDriver extends DefaultStorageDriver {

    private static final Logger log = LoggerFactory.getLogger(RestVMAXStorageDriver.class);

    // demo used constants
    // NOTE: these constants should be stored in db and obtained via hooks provided by SBSDK
    private static final String HOST = "";
    private static final String PORT = "";
    private static final String USERNAME = "";
    private static final String PASSWORD = "";
    private static final String CREATE_VOL_URL = "";

    // This is for demo
    public static void main(String[] args) {
        RestVMAXStorageDriver driver = new RestVMAXStorageDriver();
        List<StorageVolume> vols = new ArrayList<>();
        
        DriverTask task = driver.createVolumes(vols, null); // set to null since capabilities will not be used in this demo
        if (task.getStatus() == TaskStatus.READY) {
            System.out.println("Task Complete successfully");
        } else {
            System.out.println("Task failed, details:");
            System.out.println(task.getMessage());
        }
    }

    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {

        DriverTask task = new DefaultDriverTask("createVolumes");

        StringBuilder errors = new StringBuilder();
        int volumesCreated = 0;
        for (StorageVolume volume : volumes) {
            log.info("Creating volume {} on system {}", volume.getDisplayName(), volume.getStorageSystemId());

            try {

                String parameters = convertToPostParamString(volume);

                // Invoking REST API,get returned JSON string
                ClientHandler handler = new URLConnectionClientHandler();
                ClientConfig config = configureClient(false);
                if (config == null) {
                    log.error("failed to configure rest client");
                    errors.append("failed to configure rest client");
                    break;
                }
                Client client = new Client(handler, config);
                WebResource r = client.resource(CREATE_VOL_URL);
                ClientResponse response = r.header("Content-Type", "application/json")
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes()))
                        .post(ClientResponse.class, parameters);

                if (response == null) {
                    log.error("No response received");
                    errors.append(
                            String.format("No response received when creating volume %s; ", volume.getDisplayName()));
                    continue;
                }

                // Parse return JSON, and set back to volume instance, so that SBSDK could check result and operate accordingly
                if (!parseResponse(response, volume)) {
                    String errorMsg = String.format("Create volume %s failed, error message returned: %s",
                            volume.getDisplayName(), response.toString());
                    log.error(errorMsg);
                    errors.append(errorMsg);
                    continue;
                }

                volumesCreated ++;
                log.info("Created volume '{}'", volume.getNativeId());
                // Log this create operation

            } catch (Exception e) {
                log.error("Create volume failed", e);
                errors.append(e.getMessage());
            }
        }

        task.setMessage(errors.toString());

        if (volumesCreated == volumes.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (volumesCreated == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }
        return task;
    }

    // TODO parse return reponse and fill fields back to the volume instance
    // return false if the response it's failed;
    private boolean parseResponse(ClientResponse response, StorageVolume vol) {

        return true;
    }

    // TODO
    private String convertToPostParamString(StorageVolume volume) {

        return null;

    }

    // Copied from RestAPI.java
    private  ClientConfig configureClient(boolean verify) {
        TrustManager[] certs = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        } };

        SSLContext context = null;
        try {
            context = SSLContext.getInstance("TLS");
            context.init(null, certs, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("Failed to initialize TLS.");
            return null;
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

        ClientConfig config = new DefaultClientConfig();
        try {
            // Used old-fashioned code style, in case people aren't familiar with new style grammar
            config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                    new HTTPSProperties(new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return verify;
                        }
                    }, context));
        } catch (Exception e) {
            log.error("Failed to set HTTPS properties.");
            return null;
        }
        config.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        return config;
    }
}
