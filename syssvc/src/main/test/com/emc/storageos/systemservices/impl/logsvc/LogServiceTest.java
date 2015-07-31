/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.systemservices.impl.resource.LogService;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

/**
 * JUnit test class for {@link com.emc.storageos.systemservices.impl.resource.LogService}.
 */
public class LogServiceTest {

    private static final String GET_LOGS_URI = "https://localhost:4443/logs";
    private static final String LOGIN_URI = "https://localhost:4443/login";
    private static final String SYSADMIN = EnvConfig.get("sanity", "syssvc.LogServiceTest.sysAdmin");
    private static final String SYSADMIN_PASSWORD = EnvConfig.get("sanity", "syssvc.LogServiceTest.sysAdminPassword");
    private static final String AUTH_TOKEN_HEADER = "X-SDS-AUTH-TOKEN";
    private static volatile String authToken;

    private static final String INVALID_NODE_ID = "2";
    private static final String INVALID_SEVERITY = "11";
    private static final String INVALID_TIMESTAMP = "invalidTimestamp";

    @BeforeClass
    public static void init() throws Exception {
        disableCertificateValidation();

        initToken();
    }

    @Test
    /**
     * Tests the dry run of getLogs method when no args are passed.
     */
    public void testGetLogsNoArgsDryRun() {
        Client client = Client.create();
        StringBuilder resourceBuilder = new StringBuilder(GET_LOGS_URI);
        resourceBuilder.append("?");
        resourceBuilder.append(LogRequestParam.DRY_RUN);
        resourceBuilder.append("=");
        resourceBuilder.append("true");

        WebResource webResource = client.resource(resourceBuilder.toString());
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response.getStatus() == Response.Status.OK.getStatusCode());
    }

    @Test
    /**
     * Tests the getLogs method when an invalid node id is passed in the 
     * request parameters.
     */
    public void testGetLogsInvalidNodeDryRun() {
        Client client = Client.create();

        StringBuilder resourceBuilder = new StringBuilder(GET_LOGS_URI);
        resourceBuilder.append("?");
        resourceBuilder.append(LogRequestParam.NODE_ID);
        resourceBuilder.append("=");
        resourceBuilder.append(INVALID_NODE_ID);
        resourceBuilder.append("&");
        resourceBuilder.append(LogRequestParam.DRY_RUN);
        resourceBuilder.append("=");
        resourceBuilder.append("true");

        WebResource webResource = client.resource(resourceBuilder.toString());
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response.getStatus() == Response.Status.BAD_REQUEST
                .getStatusCode());
        String responseStr = response.getEntity(String.class);
        final String errMsg = MessageFormat.format("Parameter {0} is not valid", "node id");
        Assert.assertTrue(responseStr.indexOf(errMsg) != -1);
    }

    @Test
    /**
     * Tests the getLogs method when an invalid severity is passed in the 
     * request parameters.
     */
    public void testGetLogsInvalidSeverityDryRun() {
        Client client = Client.create();

        StringBuilder resourceBuilder = new StringBuilder(GET_LOGS_URI);
        resourceBuilder.append("?");
        resourceBuilder.append(LogRequestParam.SEVERITY);
        resourceBuilder.append("=");
        resourceBuilder.append(INVALID_SEVERITY);
        resourceBuilder.append("&");
        resourceBuilder.append(LogRequestParam.DRY_RUN);
        resourceBuilder.append("=");
        resourceBuilder.append("true");

        WebResource webResource = client.resource(resourceBuilder.toString());
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response.getStatus() == Response.Status.BAD_REQUEST
                .getStatusCode());
        String responseStr = response.getEntity(String.class);
        final String errMsg = MessageFormat.format("Parameter {0} is not valid", "severity");
        Assert.assertTrue(responseStr.indexOf(errMsg) != -1);
    }

    @Test
    /**
     * Tests the getLogs method when an invalid timestamp is passed in the 
     * request parameters.
     */
    public void testGetLogsInvalidTimestampDryRun() {
        Client client = Client.create();

        StringBuilder resourceBuilder = new StringBuilder(GET_LOGS_URI);
        resourceBuilder.append("?");
        resourceBuilder.append(LogRequestParam.START_TIME);
        resourceBuilder.append("=");
        resourceBuilder.append(INVALID_TIMESTAMP);
        resourceBuilder.append("&");
        resourceBuilder.append(LogRequestParam.DRY_RUN);
        resourceBuilder.append("=");
        resourceBuilder.append("true");

        WebResource webResource = client.resource(resourceBuilder.toString());
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response.getStatus() == Response.Status.BAD_REQUEST
                .getStatusCode());
        String responseStr = response.getEntity(String.class);
        final String errMsg = MessageFormat.format("Invalid date {0}. Cannot be parsed", INVALID_TIMESTAMP);
        Assert.assertTrue(responseStr.indexOf(errMsg) != -1);
    }

    @Test
    /**
     * Tests the getLogs method when the passed end date is before the passed 
     * start date.
     */
    public void testGetLogsInvalidTimeWindowDryRun() {
        // Create start and end dates for the time window such that the end date
        // is before the start date.
        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime() - 1000);
        Assert.assertTrue(endDate.before(startDate));

        // Build the request URL.
        DateFormat dateFormat = new SimpleDateFormat(LogService.DATE_TIME_FORMAT);
        StringBuilder resourceBuilder = new StringBuilder(GET_LOGS_URI);
        resourceBuilder.append("?");
        resourceBuilder.append(LogRequestParam.START_TIME);
        resourceBuilder.append("=");
        resourceBuilder.append(dateFormat.format(startDate));
        resourceBuilder.append("&");
        resourceBuilder.append(LogRequestParam.END_TIME);
        resourceBuilder.append("=");
        resourceBuilder.append(dateFormat.format(endDate));
        resourceBuilder.append("&");
        resourceBuilder.append(LogRequestParam.DRY_RUN);
        resourceBuilder.append("=");
        resourceBuilder.append("true");

        // Make the request.
        Client client = Client.create();
        WebResource webResource = client.resource(resourceBuilder.toString());
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response.getStatus() == Response.Status.BAD_REQUEST
                .getStatusCode());
        String responseStr = response.getEntity(String.class);
        Object[] args = new String[2];
        args[0] = startDate.toString();
        args[1] = endDate.toString();
        final String errMsg = MessageFormat.format("Specified end time {1} is before specified start time {0}", args);
        Assert.assertTrue(responseStr.indexOf(errMsg) != -1);
    }

    @Test
    /**
     * Tests the getLogs method when an invalid node id is passed in the 
     * request parameters.
     */
    public void testGetLogsInvalidNode() {
        Client client = Client.create();

        StringBuilder resourceBuilder = new StringBuilder(GET_LOGS_URI);
        resourceBuilder.append("?");
        resourceBuilder.append(LogRequestParam.NODE_ID);
        resourceBuilder.append("=");
        resourceBuilder.append(INVALID_NODE_ID);

        WebResource webResource = client.resource(resourceBuilder.toString());
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response.getStatus() == Response.Status.BAD_REQUEST
                .getStatusCode());
        String responseStr = response.getEntity(String.class);
        final String errMsg = MessageFormat.format("Parameter {0} is not valid", "node id");
        Assert.assertTrue(responseStr.indexOf(errMsg) != -1);
    }

    @Test
    /**
     * Tests the getLogs method when an invalid severity is passed in the 
     * request parameters.
     */
    public void testGetLogsInvalidSeverity() {
        Client client = Client.create();

        StringBuilder resourceBuilder = new StringBuilder(GET_LOGS_URI);
        resourceBuilder.append("?");
        resourceBuilder.append(LogRequestParam.SEVERITY);
        resourceBuilder.append("=");
        resourceBuilder.append(INVALID_SEVERITY);

        WebResource webResource = client.resource(resourceBuilder.toString());
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response.getStatus() == Response.Status.BAD_REQUEST
                .getStatusCode());
        String responseStr = response.getEntity(String.class);
        final String errMsg = MessageFormat.format("Parameter {0} is not valid", "severity");
        Assert.assertTrue(responseStr.indexOf(errMsg) != -1);
    }

    @Test
    /**
     * Tests the getLogs method when an invalid timestamp is passed in the 
     * request parameters.
     */
    public void testGetLogsInvalidTimestamp() {
        Client client = Client.create();

        StringBuilder resourceBuilder = new StringBuilder(GET_LOGS_URI);
        resourceBuilder.append("?");
        resourceBuilder.append(LogRequestParam.START_TIME);
        resourceBuilder.append("=");
        resourceBuilder.append(INVALID_TIMESTAMP);

        WebResource webResource = client.resource(resourceBuilder.toString());
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);

        Assert.assertTrue(response.getStatus() == Response.Status.BAD_REQUEST
                .getStatusCode());
        String responseStr = response.getEntity(String.class);
        final String errMsg = MessageFormat.format("Invalid date {0}. Cannot be parsed", INVALID_TIMESTAMP);
        Assert.assertTrue(responseStr.indexOf(errMsg) != -1);
    }

    @Test
    /**
     * Tests the getLogs method when the passed end date is before the passed 
     * start date.
     */
    public void testGetLogsInvalidTimeWindow() {
        // Create start and end dates for the time window such that the end date
        // is before the start date.
        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime() - 1000);
        Assert.assertTrue(endDate.before(startDate));

        // Build the request URL.
        DateFormat dateFormat = new SimpleDateFormat(LogService.DATE_TIME_FORMAT);
        StringBuilder resourceBuilder = new StringBuilder(GET_LOGS_URI);
        resourceBuilder.append("?");
        resourceBuilder.append(LogRequestParam.START_TIME);
        resourceBuilder.append("=");
        resourceBuilder.append(dateFormat.format(startDate));
        resourceBuilder.append("&");
        resourceBuilder.append(LogRequestParam.END_TIME);
        resourceBuilder.append("=");
        resourceBuilder.append(dateFormat.format(endDate));

        // Make the request.
        Client client = Client.create();
        WebResource webResource = client.resource(resourceBuilder.toString());
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response.getStatus() == Response.Status.BAD_REQUEST
                .getStatusCode());
        String responseStr = response.getEntity(String.class);
        Object[] args = new String[2];
        args[0] = startDate.toString();
        args[1] = endDate.toString();
        final String errMsg = MessageFormat.format("Specified end time {1} is before specified start time {0}", args);
        Assert.assertTrue(responseStr.indexOf(errMsg) != -1);
    }

    @Test
    /**
     * Tests the getLogs method when no args are passed.
     */
    public void testGetLogsNoArgs() throws Exception {

        // Make the request.
        Client client = Client.create();
        WebResource webResource = client.resource(GET_LOGS_URI);
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON)
                .header(AUTH_TOKEN_HEADER, authToken)
                .get(ClientResponse.class);
        Assert.assertTrue(response.getStatus() == Response.Status.OK.getStatusCode());
        InputStream iStream = response.getEntityInputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(iStream))) {
            @SuppressWarnings("unused")
            String line = null;
            while ((line = reader.readLine()) != null) {
                // NOSONAR ("squid:S00108 suppress sonar warning on empty block. Nothing to be done here")
            }
        }
    }

    /**
     * Create https client after invoking the login api to get security token
     * 
     * @return
     */
    private static void initToken() {
        Client client = Client.create();
        client.setFollowRedirects(false);
        client.addFilter(new HTTPBasicAuthFilter(SYSADMIN, SYSADMIN_PASSWORD));
        ClientResponse loginResp = client.resource(LOGIN_URI).get(ClientResponse.class);
        Assert.assertEquals(200, loginResp.getStatus());
        authToken = loginResp.getHeaders().getFirst(AUTH_TOKEN_HEADER);
        Assert.assertNotNull(authToken);
    }

    /**
     * disable validation of ssl certificate
     */
    private static void disableCertificateValidation() throws Exception {
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(final X509Certificate[] certs, final String authType) {
                    }

                    @Override
                    public void checkServerTrusted(final X509Certificate[] certs, final String authType) {
                    }
                }
        };

        // Ignore differences between given hostname and certificate hostname
        final HostnameVerifier hv = new HostnameVerifier() {
            @Override
            public boolean verify(final String hostname, final SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting trust manager
        final SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(hv);
    }

}
