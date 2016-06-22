package com.emc.storageos.driver.vmaxv3driver.utils.rest;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by gang on 6/16/16.
 */
public class HttpRestClient {

    private static Logger logger = LoggerFactory.getLogger(HttpRestClient.class);

    private String scheme;
    private String host;
    private int port;
    private String user;
    private String password;

    private static Map<HttpRequestType, Class<? extends HttpRequestBase>>
        requestHandlers = new HashMap<>();

    static {
        requestHandlers.put(HttpRequestType.GET, HttpGet.class);
        requestHandlers.put(HttpRequestType.POST, HttpPost.class);
        requestHandlers.put(HttpRequestType.PUT, HttpPut.class);
        requestHandlers.put(HttpRequestType.DELETE, HttpDelete.class);
    }

    public HttpRestClient() {
    }

    public HttpRestClient(String scheme, String host, int port, String user, String password) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
    }

    public HttpRestClient(String host, int port, String user, String password) {
        this.scheme = "https";
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
    }

    public String request(String path) {
        return this.request(path, HttpRequestType.GET, null);
    }

    public String request(String path, HttpRequestType requestType) {
        return this.request(path, requestType, null);
    }

    public String request(String path, HttpRequestType requestType, String body) {
        CloseableHttpClient client = null;
        try {
            // Authentication information setting.
            CredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(new AuthScope(this.host, this.port),
                new UsernamePasswordCredentials(this.user, this.password));
            // Bypass the SSL certificate checking.
            SSLContext sslContext = SSLContext.getInstance("SSL");
            TrustManager[] managers = {new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }};
            sslContext.init(null, managers, new SecureRandom());
            // Create the HTTP client.
            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            client = HttpClients.custom().setSSLSocketFactory(socketFactory).
                setDefaultCredentialsProvider(provider).build();
            // Send the request and parse the response.
            String uri = this.scheme + "://" + this.host + ":" + this.port + "/" + path;
            HttpRequestBase request = requestHandlers.get(requestType).getDeclaredConstructor(String.class).newInstance(
                uri);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Accept", "application/json");
            if(request instanceof HttpEntityEnclosingRequestBase) {
                StringEntity entity = new StringEntity(body, ContentType.APPLICATION_JSON);
                ((HttpEntityEnclosingRequestBase) request).setEntity(entity);
            }
            logger.info("Executing request: {}", request.getRequestLine());
            CloseableHttpResponse response = client.execute(request);
            try {
                logger.info(response.getStatusLine().toString());
                String responseBody = null;
                if(response.getEntity() == null) {
                    responseBody = "";
                } else {
                    responseBody = EntityUtils.toString(response.getEntity());
                }
                return responseBody;
            } finally {
                response.close();
            }
        } catch(Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
