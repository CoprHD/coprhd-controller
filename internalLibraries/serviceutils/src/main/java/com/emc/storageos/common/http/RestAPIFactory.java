package com.emc.storageos.common.http;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.httpclient.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public abstract class RestAPIFactory<T> {

    private Logger _log = LoggerFactory.getLogger(getClass());

    @Autowired
    private RESTClient restClient;

    private boolean needCertificateManager;

    /**
     * Initialize HTTP client
     */
    public void init() {
        _log.info(" RestApiFactory:init " + getClass());
        
        if (needCertificateManager) {
            // TMP CODE to create dummy security certificate manager
            try {
                final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                    }

                    @Override
                    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                } };
                // Install the all-trusting trust manager
                SSLContext sslContext;
                sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                javax.net.ssl.HostnameVerifier hostVerifier = new javax.net.ssl.HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
                        return true;
                    }
                };
                ClientConfig clientConfig = restClient.getClient().getClientHandler().getConfig();
                clientConfig.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                        new HTTPSProperties(hostVerifier, sslContext));
            } catch (GeneralSecurityException ex) {
                throw new RuntimeException("Failed to obtain ApacheHTTPClient Config");
            }
            
        }
        
        Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), 443));
    }

    public abstract T getRESTClient(URI endpoint);

    public abstract T getRESTClient(URI endpoint, String username, String password);

    public RESTClient getRestClient() {
        return restClient;
    }

    public void setRestClient(RESTClient restClient) {
        this.restClient = restClient;
    }

    public boolean isNeedCertificateManager() {
        return needCertificateManager;
    }

    public void setNeedCertificateManager(boolean needCertificateManager) {
        this.needCertificateManager = needCertificateManager;
    }

}
