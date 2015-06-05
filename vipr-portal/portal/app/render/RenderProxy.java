/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package render;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import com.emc.vipr.client.impl.SSLUtil;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SchemeRegistryFactory;

import play.Logger;
import play.exceptions.UnexpectedException;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.results.Result;

import com.emc.vipr.client.impl.Constants;
import com.google.common.collect.Maps;

import plugin.StorageOsPlugin;
import util.BourneUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

/**
 * Renders a proxied HTTP request, streaming back the content directly to the client.
 * <p>
 * NOTE: Any requests that use this result will appear to hang in the browser if they go directly to the play server
 * (instead through NGINX).  If the client requests a keep-alive connection (which seems to be default) then play doesn't
 * ever close the channel when all the content is written out.  When the connections go through NGINX it changes those
 * Connection: keep-alive to Connection: close (in our configuration) and everything works fine.
 */
public class RenderProxy extends Result {
    private String url;
    private Map<String, String> headers;

    public static void renderProxy(String url, Map<String, String> headers) {
        throw new RenderProxy(url, headers);
    }

    public static void renderViprProxy(String url, String authToken, String accept) {
        Map<String, String> headers = Maps.newHashMap();
        headers.put(Constants.AUTH_TOKEN_KEY, authToken);
        headers.put("X-EMC-REST-CLIENT", "TRUE");
        if (StringUtils.isNotBlank(accept)) {
            headers.put("Accept", accept);
        }
        renderProxy(url, headers);
    }

    public RenderProxy(String url) {
        this(url, new HashMap<String, String>());
    }

    public RenderProxy(String url, Map<String, String> headers) {
        this.url = url;
        this.headers = headers;
    }

    @Override
    public void apply(Request request, Response response) {
        HttpGet httpGet = new HttpGet(url);

        for (Map.Entry<String, String> header : headers.entrySet()) {
            httpGet.setHeader(header.getKey(), header.getValue());
        }

        HttpClient client = new DefaultHttpClient(createClientConnectionManager());
        try {
            HttpResponse httpResponse = client.execute(httpGet);
            HttpEntity entity = httpResponse.getEntity();

            if (entity.getContentType() != null) {
                setContentTypeIfNotSet(response, entity.getContentType().getValue());
            }
            long length = entity.getContentLength();
            if (length > -1) {
                response.setHeader("Content-Length", String.valueOf(length));
            }

            response.status = httpResponse.getStatusLine().getStatusCode();
            response.direct = new HttpClientInputStream(client, entity.getContent());
        }
        catch (Exception e) {
            Logger.error(e, "Failed to execute Proxy URL [%s]", url);
            client.getConnectionManager().shutdown();
            throw new UnexpectedException(e);
        }
    }

    private static ClientConnectionManager createClientConnectionManager() {
        SchemeRegistry schemeRegistry = SchemeRegistryFactory.createDefault();

        SSLSocketFactory sf;
        if (StorageOsPlugin.isEnabled()) {
            try {
                //initialize an SSLContext with the vipr keystore and trustmanager.
                //This is basically a dup of most of the ViPRSSLSocketFactory constructor,
                //and could be extracted
                X509TrustManager[] trustManagers = { BourneUtil.getTrustManager() };
                KeyManagerFactory kmf =  KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(BourneUtil.getKeyStore(), "".toCharArray());

                SSLContext context = SSLContext.getInstance("TLS");
                context.init(kmf.getKeyManagers(), trustManagers, new SecureRandom());
                sf = new SSLSocketFactory(context, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            } catch (Exception e) {
                throw new RuntimeException("Unable to initialize the ViPRX509TrustManager for RenderProxy", e);
            }
        } else {
            sf = new SSLSocketFactory(SSLUtil.getTrustAllContext(), SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        }

        Scheme httpsScheme = new Scheme("https", 443, sf);
        schemeRegistry.register(httpsScheme);

        ClientConnectionManager connectionManager = new PoolingClientConnectionManager(schemeRegistry);
        return connectionManager;
    }

    private static class HttpClientInputStream extends FilterInputStream {
        private HttpClient client;

        protected HttpClientInputStream(HttpClient client, InputStream stream) {
            super(stream);
            this.client = client;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            }
            finally {
                client.getConnectionManager().shutdown();
            }
        }
    }
}
