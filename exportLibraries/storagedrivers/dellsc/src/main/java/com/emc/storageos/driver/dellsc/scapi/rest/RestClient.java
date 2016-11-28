/*
 * Copyright 2016 Dell Inc.
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
package com.emc.storageos.driver.dellsc.scapi.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A basic REST client for SC API communication.
 */
public class RestClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestClient.class);
    private String baseUrl;
    private CloseableHttpClient httpClient;
    private HttpClientContext httpContext = null;

    /**
     * Instantiates a new Rest client.
     *
     * @param host Host name or IP address of the Dell Storage Manager server.
     * @param port Port the DSM data collector is listening on.
     * @param user The DSM user name to use.
     * @param password The DSM password.
     */
    public RestClient(String host, int port, String user, String password) {
        this.baseUrl = String.format("https://%s:%d/api/rest", host, port);

        try {
            // Set up auth handling
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(host, port),
                    new UsernamePasswordCredentials(user, password));
            AuthCache authCache = new BasicAuthCache();
            BasicScheme basicAuth = new BasicScheme();
            HttpHost target = new HttpHost(host, port, "https");
            authCache.put(target, basicAuth);

            // Set up our context
            httpContext = HttpClientContext.create();
            httpContext.setCookieStore(new BasicCookieStore());
            httpContext.setAuthCache(authCache);

            // Create our HTTPS client
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    return true;
                }
            }).build();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            this.httpClient = HttpClients.custom()
                    .setHostnameVerifier(new AllowAllHostnameVerifier())
                    .setDefaultCredentialsProvider(credsProvider)
                    .setSSLSocketFactory(sslSocketFactory).build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            // Hopefully default SSL handling is set up
            LOG.warn("Failed to configure HTTP handling, falling back to default handler.");
            LOG.debug("Config error: {}", e);
            this.httpClient = HttpClients.createDefault();
        }
    }

    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
        }
        httpContext = null;
    }

    /**
     * Format the REST endpoint URL.
     *
     * @param path The target path.
     * @return The full endpoint URL.
     */
    private String formatUrl(String path) {
        // Make sure URL is formatted how we expect
        String urlPath = path;
        if (!path.startsWith("/")) {
            urlPath = String.format("/%s", path);
        }

        return String.format("%s%s", this.baseUrl, urlPath);
    }

    /**
     * Execute a REST call.
     *
     * @param request The REST request.
     * @return The results from the execution.
     */
    private RestResult executeRequest(HttpRequestBase request) {
        RestResult result = null;

        request.addHeader("Accept", "application/json");
        request.addHeader("x-dell-api-version", "2.0");
        request.addHeader("Content-Type", "application/json; charset=utf-8");

        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(request, httpContext);
            HttpEntity entity = response.getEntity();
            result = new RestResult(
                    request.getURI().toString(),
                    response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase(),
                    entity != null ? EntityUtils.toString(response.getEntity()) : "");
        } catch (IOException e) {
            result = new RestResult(500, "Internal Failure", "");
            LOG.warn(String.format("Error in API request: %s", e), e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
            }
        }

        return result;
    }

    /**
     * Execute a GET REST call.
     *
     * @param path The relative path.
     * @return The execution result.
     */
    public RestResult get(String path) {
        HttpGet httpGet = new HttpGet(formatUrl(path));
        return executeRequest(httpGet);
    }

    /**
     * Execute a DELETE REST call.
     *
     * @param path The relative path.
     * @return The execution result.
     */
    public RestResult delete(String path) {
        HttpDelete httpDelete = new HttpDelete(formatUrl(path));
        return executeRequest(httpDelete);
    }

    /**
     * Execute a POST REST call.
     *
     * @param path The relative path.
     * @param payload The POST payload.
     * @return The execution result.
     */
    public RestResult post(String path, String payload) {
        HttpPost httpPost = new HttpPost(formatUrl(path));
        StringEntity entity = new StringEntity(payload, StandardCharsets.UTF_8);
        httpPost.setEntity(entity);

        return executeRequest(httpPost);
    }

    /**
     * Execute a PUT REST call.
     *
     * @param path The relative path.
     * @param payload The PUT payload.
     * @return The execution result.
     */
    public RestResult put(String path, String payload) {
        HttpPut httpPut = new HttpPut(formatUrl(path));
        httpPut.setEntity(new StringEntity(payload, StandardCharsets.UTF_8));
        return executeRequest(httpPut);
    }
}
