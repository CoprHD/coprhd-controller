/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPFault;
import javax.xml.xpath.XPathExpression;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import com.iwave.ext.xml.XmlUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class WinRMTarget {
    protected static final ContentType SOAP = ContentType.create("application/soap+xml", "UTF-8");
    public static final int DEFAULT_HTTP_PORT = 5985;
    public static final int DEFAULT_HTTPS_PORT = 5986;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 60 * 60 * 1000; // 1 hour

    private String host;
    private int port;
    private boolean secure;
    private String username;
    private String password;

    private HttpClient client;
    private HttpContext context;

    public WinRMTarget(String host, String username, String password) {
        this(host, DEFAULT_HTTP_PORT, false, username, password);
    }

    public WinRMTarget(String host, int port, boolean secure, String username, String password) {
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.username = normalizeUsername(username);
        this.password = password;
    }

    /**
     * This will normalize a windows style username into the appropriate format for Kerberos. Windows usernames are
     * specified as <i>DOMAIN\\username</i>, but kerberos requires the names to be <i>username@DOMAIN</i>.
     * 
     * @param username
     *            the username to normalize.
     * @return the normalized username.
     */
    public static String normalizeUsername(String username) {
        boolean isDomainUser = StringUtils.contains(username, '\\');
        if (isDomainUser) {
            // Uppercase the domain name
            String domain = StringUtils.upperCase(StringUtils.substringBefore(username, "\\"));
            String name = StringUtils.substringAfter(username, "\\");
            return String.format("%s@%s", name, domain);
        }
        else {
            // Not a domain username, leave as is
            return username;
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isSecure() {
        return secure;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    protected String getProtocol() {
        return secure ? "https" : "http";
    }

    public URL getUrl() {
        try {
            return new URL(getProtocol(), host, port, "/wsman");
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public String sendMessage(String request) throws WinRMException {
        try {
            HttpPost post = new HttpPost(getUrl().toExternalForm());
            post.setEntity(new StringEntity(request, SOAP));

            HttpHost targetHost = new HttpHost(getHost(), getPort(), getProtocol());
            HttpResponse response = getClient().execute(targetHost, post, getContext());
            String text = EntityUtils.toString(response.getEntity());

            if (response.getStatusLine().getStatusCode() != 200) {
                handleError(response, text);
            }
            return text;
        } catch (WinRMException e) {
            throw e;
        } catch (Exception e) {
            throw new WinRMException(e);
        }
    }

    protected void handleError(HttpResponse response, String content) throws WinRMException {
        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            throw new WinRMException("Authentication Failed");
        }
        else if (statusLine.getStatusCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            // Check to see if the response is a SOAP fault
            SOAPFault fault = getSOAPFault(content);
            if (fault != null) {
                String wMIErrorMsg = getWMIError(fault);

                if (wMIErrorMsg != null) {
                    throw new WinRMSoapException(wMIErrorMsg, fault);
                } else {
                    throw new WinRMSoapException(fault);
                }
            }
        }

        throw new WinRMException(String.format("HTTP response: %d, %s", statusLine.getStatusCode(),
                statusLine.getReasonPhrase()));
    }

    protected SOAPFault getSOAPFault(String content) {
        try {
            Document doc = XmlUtils.parseXml(content);
            SOAPEnvelope e = (SOAPEnvelope) SOAPFactory.newInstance().createElement(doc.getDocumentElement());
            SOAPFault fault = e.getBody().getFault();
            return fault;
        } catch (Exception e) {
            return null;
        }
    }

    protected String getWMIError(SOAPFault soapFault) {
        XPathExpression xpath = XmlUtils.compileXPath(WinRMConstants.XPATH,
                "s:Detail/f:WSManFault/f:Message/f:ProviderFault/f:ExtendedError");
        Element extendedError = XmlUtils.selectElement(xpath, soapFault);

        if (extendedError != null) {
            NodeList descriptionElements = extendedError.getElementsByTagNameNS("*", "Description");

            if (descriptionElements.getLength() != 0) {
                if (!StringUtils.isBlank(descriptionElements.item(0).getTextContent())) {
                    return descriptionElements.item(0).getTextContent();
                }
            }
        }

        return null; // No Description, or it's empty
    }

    protected HttpClient getClient() {
        if (client == null) {
            client = createHttpClient();
        }
        return client;
    }

    protected HttpContext getContext() {
        if (context == null) {
            context = createHttpContext();
        }
        return context;
    }

    protected HttpClient createHttpClient() {
        DefaultHttpClient client = new DefaultHttpClient(createClientConnectionManager());
        client.getAuthSchemes().register("Negotiate", new CustomSPNegoSchemeFactory());
        client.getCredentialsProvider().setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));
        final HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, DEFAULT_CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(httpParams, DEFAULT_CONNECTION_TIMEOUT);
        client.setParams(httpParams);
        return client;
    }

    private ClientConnectionManager createClientConnectionManager() {
        SchemeRegistry schemeRegistry = SchemeRegistryFactory.createDefault();

        // SSL Trust all for HTTP Client 4.x
        SSLSocketFactory sf = new SSLSocketFactory(SSLUtil.getTrustAllContext(),
                SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        Scheme httpsScheme = new Scheme("https", port, sf);
        schemeRegistry.register(httpsScheme);

        ClientConnectionManager connectionManager = new PoolingClientConnectionManager(schemeRegistry);
        return connectionManager;
    }

    protected HttpContext createHttpContext() {
        return new BasicHttpContext();
    }
}
