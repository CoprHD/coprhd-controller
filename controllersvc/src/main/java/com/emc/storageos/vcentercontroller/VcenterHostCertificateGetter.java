/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vcentercontroller;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
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

    private HttpResponse
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

        HttpResponse response;
        DefaultHttpClient httpclient = null;
        HttpParams params = new BasicHttpParams();
        if (timeout > 0) {
            _log.info("Socket timeout set to " + timeout + " seconds");
            params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
            params.setParameter(CoreConnectionPNames.SO_TIMEOUT, timeout);
        }
        try {
            // basic authentication credentials
            _log.info("Configure HTTP client for the basic authentication challenge");
            httpclient = new DefaultHttpClient(params);
            SSLHelper.configurePermissiveSSL(httpclient);
            httpclient.getCredentialsProvider().setCredentials(
                    new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                    new UsernamePasswordCredentials(username, password));

            // request to certificate
            HttpGet httpget = new HttpGet("https://" + host + ":" + port + certificatePath);
            // execute request
            _log.info("Executing request " + httpget.getRequestLine());
            response = httpclient.execute(httpget);
            _log.info("HTTP request executed successfully " + response);
        } finally {
            // release connection
            try {
                if (httpclient != null) {
                    if (httpclient.getConnectionManager() != null) {
                        httpclient.getConnectionManager().shutdown();
                    }
                }
            } catch (Exception ex) {
                _log.info("Ignore httpclient.getConnectionManager().shutdown() exception ", ex);
            }
        }
        if (response == null) {
            _log.error("HTTP response null");
            throw new Exception("HTTP response null");
        }
        return response;
    }

    HostConnectionStatus getConnectionStatus(String host, Integer port, String certificatePath, String username, String password,
            Integer timeout) {
        HttpResponse response = null;
        // First, make sure can execute the request successfully
        try {
            response = executeRequest(host, port, certificatePath, username, password, timeout);
        } catch (Exception ex) {
            if (ex instanceof java.net.SocketTimeoutException) {
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

        // Second, ensure that the response is valid
        if (response == null || response.getStatusLine() == null) {
            _log.error("Null response from host " + host);
            return HostConnectionStatus.UNREACHABLE;
        }

        // Third, inspect the response
        String responseStatus = response.getStatusLine().toString().toLowerCase();
        _log.info("Host " + host + " response is " + responseStatus);
        if (responseStatus.contains("401 unauthorized")) {
            _log.info("Unauthorized response");
            return HostConnectionStatus.UNAUTHORIZED;
        } else if (responseStatus.contains("200 ok")) {
            _log.info("Reachable response");
            return HostConnectionStatus.REACHABLE;
        } else {
            _log.info("Unreachable response");
            return HostConnectionStatus.UNREACHABLE;
        }
    }

    String getSSLThumbprint(String host, Integer port, String certificatePath, String username, String password, Integer timeout)
            throws Exception {
        HttpResponse response = null;
        try {
            response = executeRequest(host, port, certificatePath, username, password, timeout);
        } catch (Exception ex) {
            _log.error("Unexepected error executing request https://{}", host + ":" + port + certificatePath, ex);
            throw new Exception("Unexepected error executing request https://" + host + ":" + port + certificatePath);
        }

        String cert = null;
        try {
            // extract base64 encoded certificate from response
            _log.info("Extract base64 encoded certification from response");
            _log.info("Response " + response.getStatusLine());
            if (response.getEntity() != null) {
                cert = convertStreamToString(response.getEntity().getContent());
                _log.info("Response content length: " + response.getEntity().getContentLength());
                _log.info("Content: " + cert);
                // strip pem certificate of begin and end strings
                cert = cert.replaceAll("-----BEGIN CERTIFICATE-----", "").replaceAll("-----END CERTIFICATE-----", "");
                _log.info("Certificate extracted " + cert);
            }
        } catch (Exception ex) {
            _log.error("Unexepected error extracting content from response {} ", response.getEntity().getContent(), ex);
            throw new Exception("Unexepected error extracting content from response " + response.getEntity().getContent());
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

    public String convertStreamToString(InputStream is) throws IOException {
        if (is != null) {
            Writer writer = new StringWriter();
            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }
            return writer.toString();
        } else {
            return "";
        }
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
}
