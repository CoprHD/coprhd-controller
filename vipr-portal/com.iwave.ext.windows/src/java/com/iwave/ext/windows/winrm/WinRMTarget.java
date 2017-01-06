/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPFault;
import javax.xml.xpath.XPathExpression;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.iwave.ext.windows.winrm.encryption.EncryptedHttpRequestExecutor;
import com.iwave.ext.xml.XmlUtils;

public class WinRMTarget {
    private static final Logger logger = LoggerFactory.getLogger(WinRMTarget.class);

    protected static final ContentType SOAP = ContentType.create("application/soap+xml", "UTF-8");
    public static final int DEFAULT_HTTP_PORT = 5985;
    public static final int DEFAULT_HTTPS_PORT = 5986;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 60 * 60 * 1000; // 1 hour

    private String host;
    private int port;
    private boolean secure;
    private String username;
    private String password;

    private CloseableHttpClient client;
    private HttpContext context;

    public WinRMTarget(String host, String username, String password) {
        this(host, DEFAULT_HTTP_PORT, false, username, password);
    }

    public WinRMTarget(String host, int port, boolean secure, String username, String password) {
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.username = username;
        this.password = password;
    }

    
    //TODO: Remove this method, probably no longer needed.
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
            CloseableHttpClient client = getClient();
            HttpContext context = getContext();
            HttpResponse response = client.execute(targetHost, post, context);
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

    protected CloseableHttpClient getClient() throws HttpException {
        if (client == null) {
            try {
				client = createHttpClient();
			} catch (HttpException e) {
				throw e;
			}
        }
        return client;
    }

    protected HttpContext getContext() {
        if (context == null) {
            context = createHttpClientContext();
        }
        return context;
    }

    /**
     * HttpClient builder
     * @return HttpClient
     * @throws HttpException
     */
    protected CloseableHttpClient createHttpClient() throws HttpException {
    	
    	HttpClientBuilder httpClient = HttpClientBuilder.create();
    	
    	//Build the request config identifying the target preferred authentication schemes and other socket connection parameters.
    	RequestConfig.Builder requestConfig = RequestConfig.custom()
                .setTargetPreferredAuthSchemes(
                        Arrays.asList(AuthSchemes.SPNEGO, AuthSchemes.NTLM, AuthSchemes.DIGEST, AuthSchemes.BASIC));
    	requestConfig.setConnectTimeout(DEFAULT_CONNECTION_TIMEOUT);
    	requestConfig.setSocketTimeout(DEFAULT_CONNECTION_TIMEOUT);
    	httpClient.setDefaultRequestConfig(requestConfig.build());
    	
    	//Set the request executor. The EncryptedHttpRequestExecutor is a custom request executor that is capable of encryption and works
    	//using the Windows NTLM authentication scheme.
    	httpClient.setRequestExecutor(new EncryptedHttpRequestExecutor());
    	
    	//Build a list of the authentication schemes
    	Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider> create()
				.register(AuthSchemes.NTLM, new NTLMSchemeFactory())
				.register(AuthSchemes.BASIC, new BasicSchemeFactory())
				.register(AuthSchemes.DIGEST, new DigestSchemeFactory())
				.register(AuthSchemes.KERBEROS, new KerberosSchemeFactory())
				.register(AuthSchemes.SPNEGO, new CustomSPNegoSchemeFactory()).build();
    
    	try {
			httpClient.setConnectionManager(createClientConnectionManager());
		} catch (Exception e) {
			throw new HttpException(e.getMessage());
		}
    	httpClient.setDefaultAuthSchemeRegistry(authSchemeRegistry);   	
    	return httpClient.build();
    }

    private HttpClientConnectionManager createClientConnectionManager() throws Exception {
    	SSLContextBuilder contextBuilder = SSLContexts.custom();
        try {
            contextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(SSLContexts.custom()
                    .loadTrustMaterial(null, new TrustSelfSignedStrategy()).build(),
                    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create()
            		.register("http", PlainConnectionSocketFactory.INSTANCE)
                    .register("https", socketFactory)
            		.build();
            
        	return (new PoolingHttpClientConnectionManager(registry));

        } catch (Exception e) {
        	throw new HttpException(e.getMessage());
        }
    }

    protected  HttpClientContext createHttpClientContext() {
        HttpClientContext httpClientContext = HttpClientContext.create();
        
        //Build the credential provider. Note that the credentials are using NTCredentials class which is a derived class of UserPasswordCredentials
    	//This is specifically needed for NTLM authentication.
    	//NTCredentials requires user name in the format "user" and NOT "domain\\user"
        String[] tokens = StringUtils.split(getUsername(), "\\", 2);
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                AuthScope.ANY,
                new NTCredentials(tokens.length > 1 ? tokens[1] : getUsername(), 
                getPassword(), 
                System.getProperty("hostname"), tokens.length > 1 ? tokens[0] : null)
               );

        httpClientContext.setCredentialsProvider(credsProvider);
        httpClientContext.setTargetHost(new HttpHost(getHost()));
        return httpClientContext;
    }
}
