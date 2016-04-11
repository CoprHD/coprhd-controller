package com.emc.storageos.api.service.impl.resource.utils;

import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.security.SecureRandom;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;

public class RESTClientUtil {

    private static final Logger log = LoggerFactory.getLogger(RESTClientUtil.class);

    private final static String AUTH_TOKEN_HEADER = "X-SDS-AUTH-TOKEN";
    private String _baseURL;
    // private boolean _authorizationFlag;

    public String get_baseURL() {
        return _baseURL;
    }

    public void set_baseURL(String _baseURL) {
        this._baseURL = _baseURL;
    }

    private ClientConfig _config;
    private Client _client;
    private String _username;
    private String _password;
    private static final int CALL_COUNT_FOR_AUTHENTICATION = 10;

    

    
    private static RESTClientUtil instance = null;
    
    protected RESTClientUtil() {
    }
    
    // Lazy Initialization (If required then only)
    public static RESTClientUtil getInstance() {
        if (instance == null) {
            // Thread Safe. Might be costly operation in some case
            synchronized (RESTClientUtil.class) {
                if (instance == null) {
                    instance = new RESTClientUtil();
                }
            }
        }
        return instance;
    }
    
    public <T> T queryObject(String uri, Class<T> clazz)
            throws NoSuchAlgorithmException, UniformInterfaceException {

        final String methodName = "queryObject(): ";

        log.debug(methodName + "Entry with inputs uri[" + uri + "] class["
                + clazz.getName() + "]");

        _config.getClasses().add(clazz);
        /*
         * if (this._authorizationFlag) { this.authenticate(_client); }
         */
        WebResource resource = null;

        try {
            resource = _client.resource(_baseURL + uri);
            return resource.get(clazz);
        } catch (UniformInterfaceException e) {

            if (e.getMessage().contains("401 Unauthorized")
                    || e.getMessage().contains("403 Forbidden")) {
                this.authenticate(_client, CALL_COUNT_FOR_AUTHENTICATION);
                resource = _client.resource(_baseURL + uri);
                return resource.get(clazz);
            } else {
                throw e;
            }
        }
    }
    
    private void authenticate(final Client c, final int authenticateCallCount)
            throws NoSuchAlgorithmException {

        final String methodName = "authenticate(): ";
        log.debug(methodName + "Called");

        disableCertificateValidation();
        c.setFollowRedirects(false);

        if (log.isTraceEnabled()) {
            c.addFilter(new LoggingFilter());
        }
        c.addFilter(new HTTPBasicAuthFilter(this._username, this._password));
        c.addFilter(new ClientFilter() {

            private Object authToken;

            // private int callCount = authenticateCallCount;

            @Override
            public ClientResponse handle(ClientRequest request)
                    throws ClientHandlerException {


                if (authToken != null) {
                    request.getHeaders().put(AUTH_TOKEN_HEADER,
                            Collections.singletonList(authToken));
                }
                ClientResponse response = getNext().handle(request);
                if (response.getHeaders() != null
                        && response.getHeaders().containsKey(AUTH_TOKEN_HEADER)) {
                    authToken = (response.getHeaders()
                            .getFirst(AUTH_TOKEN_HEADER));
                }
                if (response.getStatus() == 302) {
                    WebResource wb = c.resource(response.getLocation());
                    response = wb.header(AUTH_TOKEN_HEADER, authToken).get(
                            ClientResponse.class);
                }
                return response;
            }
        });
    }
    
    public static void disableCertificateValidation() {
        // this method is basically bypasses certificate validation.
        // Bourne appliance uses expired certificate!

        final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(final X509Certificate[] certs,
                    final String authType) {
            }

            public void checkServerTrusted(final X509Certificate[] certs,
                    final String authType) {
            }
        } };

        final HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(final String hostname,
                    final SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting trust manager
        try {
            final SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection
                    .setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(hv);
        } catch (final Exception e) {
            log.error("Unexpeted error occured", e);
        }
    }
    
    public void setLoginCredentials(String username, String password)
            throws NoSuchAlgorithmException {

        // this._authorizationFlag = true;
        this._username = username;
        this._password = password;

        try {
            this.authenticate(_client, CALL_COUNT_FOR_AUTHENTICATION);
        } catch (NoSuchAlgorithmException e) {
            throw e;
        }

        log.trace("username and password are set");
    }
    
}
