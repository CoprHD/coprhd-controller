/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vcentercontroller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.cloud.http.ssl.SSLHelper;

/**
 * Created with IntelliJ IDEA.
 * User: alaplante
 * Date: 9/23/14
 * Time: 2:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class VcenterHostCertificateGetter {
    private static VcenterHostCertificateGetter instance = new VcenterHostCertificateGetter();

    private final static Logger _log = LoggerFactory.getLogger(VcenterHostCertificateGetter.class);

    public synchronized static VcenterHostCertificateGetter getInstance() {
        return instance;
    }

    public enum HostConnectionStatus {
        REACHABLE, UNREACHABLE, UNAUTHORIZED, UNKNOWN
    }

    private String
            executeRequest(String host, Integer port, String certificatePath, String username, String password, Integer timeout)
                    throws Exception {
        if (host == null || host.equals("")) {
            throw new IllegalArgumentException("Invalid host " + host);
        }
        if (port == null) {
            throw new IllegalArgumentException("Invalid port " + port);
        }
        if (username == null) {
            throw new IllegalArgumentException("Invalid username " + username);
        }
        if (password == null) {
            throw new IllegalArgumentException("Invalid password " + password);
        }
        if (timeout == null) {
            throw new IllegalArgumentException("Invalid timeout " + timeout);
        }
        _log.info("Get SSL thumbprint host " + host + ", port " + port + ", certificatePath " + certificatePath + ", timeout " + timeout);

        CloseableHttpResponse response = null;
        CloseableHttpClient httpClient = null;
        String responseString = null;
        try {
            _log.info("Configure HTTP client for the basic authentication challenge");
            HttpClientBuilder httpClientBuilder = SSLHelper.getPermissiveSSLHttpClientBuilder(false, -1, -1, timeout,
                    timeout);
            // basic authentication credentials
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(AuthScope.ANY),
                    new UsernamePasswordCredentials(username, password));
            httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
            httpClient = httpClientBuilder.build();

            // request to certificate
            HttpGet httpget = new HttpGet("https://" + host + ":" + port + certificatePath);
            // execute request
            _log.info("Executing request " + httpget.getRequestLine());
            response = httpClient.execute(httpget);
            _log.info("HTTP request executed successfully " + response);
            responseString = parseResponse(response);
        } finally {
            IOUtils.closeQuietly(response);
            IOUtils.closeQuietly(httpClient);
        }
        return responseString;
    }

    HostConnectionStatus getConnectionStatus(String host, Integer port, String certificatePath, String username, String password,
            Integer timeout) {
        String responseString = null;
        // First, make sure can execute the request successfully
        try {
            responseString = executeRequest(host, port, certificatePath, username, password, timeout);
        } catch (RuntimeException ex){
            String exMsg = ex.getMessage();
            if (exMsg.equalsIgnoreCase("Null response from host")) {
                return HostConnectionStatus.UNREACHABLE;
            } else if (exMsg.toLowerCase().contains("401 unauthorized")) {
                _log.info("Unauthorized response");
                return HostConnectionStatus.UNAUTHORIZED;
            } else {
                _log.info("Unreachable response");
                return HostConnectionStatus.UNREACHABLE;
            }
        } catch (Exception ex) {
            if (ex instanceof java.net.SocketTimeoutException || ex instanceof ConnectTimeoutException) {
                // This exception is usually the case when we're waiting for an ESXi to boot for the first time.
                // It makes a mess in the logs if we print the stack and ERROR for each attempt we make.
                // Assumption is if this code is used for other cases, errors further up the chain will indicate
                // a bigger problem (like an ESXi host being down)
                _log.warn("Error trying to connect to " + host + ":" + port + ".  Connection timed out");
            } else {
                _log.error("Error in executeRequest ", ex);
            }
            if (ex instanceof java.net.UnknownHostException) {
                _log.info("Host " + host + " is unknown");
                return HostConnectionStatus.UNKNOWN;
            } else {
                _log.info("Return unreachable due to exception");
                return HostConnectionStatus.UNREACHABLE;
            }
        }
        if (StringUtils.isNotEmpty(responseString)) {
            return HostConnectionStatus.REACHABLE;
        } else {
            return HostConnectionStatus.UNREACHABLE;
        }
    }

    String getSSLThumbprint(String host, Integer port, String certificatePath, String username, String password, Integer timeout)
            throws Exception {
        String cert = null;
        try {
            cert = executeRequest(host, port, certificatePath, username, password, timeout);
            if (StringUtils.isEmpty(cert)) {
                _log.error("Empty or null response string from host - {} ", host);
                throw new RuntimeException("Empty or null response string from host - " + host);
            }
        } catch (Exception ex) {
            _log.error("Unexepected error executing request https://{}", host + ":" + port + certificatePath, ex);
            throw new Exception("Unexepected error executing request https://" + host + ":" + port + certificatePath, ex);
        }

        try {
            // extracted base64 encoded certificate from response
            _log.info("Extracted base64 encoded certification from response");
            _log.info("Content: {}", cert);
            // strip pem certificate of begin and end strings
            cert = cert.replaceAll("-----BEGIN CERTIFICATE-----", "").replaceAll("-----END CERTIFICATE-----", "");
            _log.info("Certificate extracted {}", cert);

        } catch (Exception ex) {
            _log.error("Unexepected error extracting content from response {} ", ex.getMessage(), ex);
            throw new Exception("Unexepected error extracting content from response " + ex.getMessage(), ex);
        }
        if (cert == null || cert.equals("")) {
            _log.error("Certificate base 64 encoded string " + cert);
            throw new Exception("Certificate base 64 encoded string " + cert);
        }

        /**
         * THEN SHA-1 HASH THE CERT
         */
        ByteArrayInputStream byteArrayInputStream = null;
        try {
            // decode
            _log.info("Decode base64 encoded certificate string");
            Base64 base64 = new Base64();
            byteArrayInputStream = new ByteArrayInputStream(base64.decode(cert));
            _log.info("Base64 decoded"); // DO NOT PRINT THE byte[] since it breaks the next step - Could not parse certificate:
                                         // java.io.IOException: DerInputStream.getLength(): lengthTag=127, too big.
        } catch (Exception ex) {
            _log.error("Unexepected error decode base64 encoded certificate ", ex);
            throw new Exception("Unexepected error decode base64 encoded certificate");
        }
        if (byteArrayInputStream == null) {
            _log.error("Decoded base 64 byte[] null");
            throw new Exception("Decoded base 64 byte[] null");
        }

        X509Certificate certificate = null;
        try {
            // convert to x509 cert
            _log.info("Convert decoded base64 byte[] to X509 certificate");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            certificate = (X509Certificate) cf.generateCertificate(byteArrayInputStream);
            _log.info("X509 certificate created " + certificate);
        } catch (Exception ex) {
            _log.error("Unexepected error creating x509 certificate ", ex);
            throw new Exception("Unexepected error creating x509 certificate");
        }
        if (certificate == null) {
            _log.error("X509 Certificate null");
            throw new Exception("X509 Certificate null");
        }

        byte[] digest;
        try {
            // hash to SHA1
            _log.info("Hash X509 certificate to SHA-1");
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] der = certificate.getEncoded();
            md.update(der);
            digest = md.digest();
            _log.info("SHA-1 hash created " + digest);
        } catch (Exception ex) {
            _log.error("Unexepected error SHA-1 hashing certificate ", ex);
            throw new Exception("Unexepected error SHA-1 hashing certificate");
        }
        if (digest == null) {
            _log.error("SHA-1 hash null");
            throw new Exception("SHA-1 hash null");
        }

        String thumbprint;
        try {
            // convert to hex
            _log.info("Convert hash to hex");
            // BigInteger bigInt = new BigInteger(1,digest)
            // thumbprint = bigInt.toString(16)
            thumbprint = getHex(digest);
            _log.info("Hex created " + thumbprint);
        } catch (Exception ex) {
            _log.error("Unexepected error converting SSL thumbprint to hex ", ex);
            throw new Exception("Unexepected error converting SSL thumbprint to hex");
        }
        if (thumbprint == null || thumbprint.equals("")) {
            _log.error("thumbprint " + thumbprint);
            throw new Exception("thumbprint " + thumbprint);
        }

        String hash;
        try {
            // splice in colons and bring to upper
            _log.info("Splice colons to hash and bring to upper case");
            thumbprint = thumbprint.toUpperCase();
            StringBuffer buf = new StringBuffer(thumbprint);
            int index = 2;
            while (index < buf.toString().length()) {
                buf.insert(index, ':');
                index += 3; // 2 (for chars) + 1 (for spliced colon)
            }
            hash = buf.toString();
            _log.info("Hash string formatted " + hash);
        } catch (Exception ex) {
            _log.error("Unexepected error splicing colons into SSL thumbprint ", ex);
            throw new Exception("Unexepected error splicing colons into SSL thumbprint");
        }
        if (hash == null || hash.equals("")) {
            _log.error("SSL thumbprint is empty");
            throw new Exception("SSL thumbprint is empty");
        }
        if (hash.length() != 59) {
            _log.error("SSL thumbprint does not appear to be a valid");
            throw new Exception("SSL thumbprint does not appear to be a valid");
        }

        return hash;
    }

    public static String getHex(byte[] raw) {
        if (raw == null) {
            return null;
        }
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw) {
            hex.append("0123456789ABCDEF".charAt((b & 0xF0) >> 4))
                    .append("0123456789ABCDEF".charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    /**
     * Parse the response of a call to host/server.
     *
     * @param response
     *            response from the host/server
     * @return Response entity as a string
     * @throws IOException
     *             bad response data
     * @throws RuntimeException
     *             Parse Failed
     */
    private String parseResponse(CloseableHttpResponse response) throws IOException, RuntimeException {

        if (response == null || response.getStatusLine() == null) {
            _log.error("Null response from host");
            throw new RuntimeException("Null response from host");
        }
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            _log.debug("Invalid Response: {}", response.getStatusLine());
            throw new RuntimeException("Invalid response from host: " + response.getStatusLine());
        }
        _log.info("Response " + response.getStatusLine());
        HttpEntity entity = response.getEntity();
        _log.info("Response content length: {}",  entity.getContentLength());
        return EntityUtils.toString(entity);
    }
}
