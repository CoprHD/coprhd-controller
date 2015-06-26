/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.remoterepotool;


import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

import javax.net.ssl.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.*;
import java.net.*;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class Main {
    private static SSLSocketFactory _sslSocketFactory;

    private static Proxy _proxy = Proxy.NO_PROXY;
    private static int _timeout = 30000;
    private static final int MAXIMUM_REDIRECT_ALLOWED = 10;

    private static final String EMC_SSO_AUTH_SERVICE_HOST= "sso.emc.com";
    private static final String EMC_SSO_AUTH_SERVICE_TESTHOST= "sso-tst.emc.com";
    private static final String EMC_SSO_AUTH_SERVICE_URLPATH = "/authRest/service/auth.json";
    private static final String EMC_SSO_DOWNLOAD_SERVICE_HOST= "download.emc.com";
    private static final String EMC_SSO_DOWNLOAD_SERVICE_TESTHOST= "download-tst.emc.com";
    private static final String EMC_SSO_AUTH_SERVICE_LOGIN_POST_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><user><password>{0}</password><username>{1}</username></user>";

    private static String _ssohost;
    private static String _username;
    private static String _password;
    private static String _ctsession;

    private static Map<String, String> _cookieMap = new HashMap<>();

    private static void usage() {
        System.out.println("Usage: ");
        System.out.println("remoterepotool --hostname <hostname> --imgpath <image path> --user <username> --pass <password>");
    }

    private static String hostname;
    private static String imgUrlPath;
    
    public static void main(String[] args) {
        if (args.length != 8) {
            usage();
            return;
        }
        int port=443;

        for (int i=0; i< args.length; i++){
           if(args[i].equals("--hostname")) {
               i++;
               hostname=args[i];

               continue;
           }
           if(args[i].equals("--imgpath")) {
               i++;
               imgUrlPath=args[i];
               continue;
           }
            if(args[i].equals("--user")) {
                i++;
                _username=args[i];
                continue;
            }
            if(args[i].equals("--pass")) {
                i++;
                _password=args[i];
                continue;
            }
        }

        try {
            initializeSslContext();
        } catch (Exception e) {
            System.out.println("error:" + e.getMessage());
            e.printStackTrace();
        }

        try {
            if ( hostname.equalsIgnoreCase(EMC_SSO_DOWNLOAD_SERVICE_TESTHOST) ) {
                _ssohost = EMC_SSO_AUTH_SERVICE_TESTHOST;
            } else if ( hostname.equalsIgnoreCase(EMC_SSO_DOWNLOAD_SERVICE_HOST) ) {
                _ssohost = EMC_SSO_AUTH_SERVICE_HOST;
            } else {
                _ssohost = null;
            }
            if (_ssohost != null) {
                URL loginUrl = new URL("https", _ssohost, EMC_SSO_AUTH_SERVICE_URLPATH);
                login(loginUrl, _username, _password);
            }

            URL imgUrl = new URL("https", hostname, imgUrlPath);
            connectImage(imgUrl);
        } catch (Exception e) {
            System.out.println("error:" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Before downloading images from download.emc.com, user needs to login
     * EMC SSO service (sso.emc.com) to get authorized session cookie.
     * The request:
     *   URI:			http://sso.emc.com/authRest/service/auth.json
     *   Format:			XML
     *   HTTP Method:		POST
     *   Request Body for customer, partner or lite users:
     *     <?xml version="1.0" encoding="UTF-8" standalone="yes"?><user><password>##########</password><username>johndoe@acme.com</username></user>
     *   Request Body for employee
     *     <?xml version="1.0" encoding="UTF-8" standalone="yes"?><user><password>pin+fob/softtoken</password><username>emp nt</username></user>
     *
     * Response body:
     * 1. Example for successful response:
     *   {
     *       "object": {
     *           "authResult": {
     *               "status":"SUCCESS",
     *               "operation":"VALID_USER",
     *               "token":"AAAAAgABAFBLtr+WcJAh+DJ1Q2GXYiH0PC5+Txuscy1+pU7TRpAcUoyfhNwB55DZwPCZlQwgVpyY+vaOYNblApcSOZ+hEWFzIxj1JtII/ozshY+33ddafg==",
     *               "userProps":[{"propName":"LAST_NAME","propValue":"[TestFour]"},{"propName":"GIVEN_NAME","propValue":"[AlphaFour]"},{"propName":"UID","propValue":"[1110000003]"},{"propName":"EMC_IDENTITY","propValue":"[C]"}]
     *           }
     *        },
     *       "serviceFault":null
     *   }
     * 2. Example for failure response:
     *   {
     *       "object": {
     *           "authResult":    {
     *               "status": "FAILED",
     *               "operation": "INVALID_USERNAME_PASSWORD",
     *               "token": null,
     *               "userProps": null
     *           }
     *       },
     *       "serviceFault":null
     *   }
     *
     * @param username
     * @param password
     */
    private static void login(URL url, String username, String password) {
        System.out.println(username + " is trying to logining at " + url.toString());
        try {
            HttpURLConnection httpCon = prepareConnection(url);
            httpCon.setInstanceFollowRedirects(false);
            String loginContent = MessageFormat.format(EMC_SSO_AUTH_SERVICE_LOGIN_POST_CONTENT, password, username);
            writePostContent(httpCon, loginContent);

            System.out.println("Response code:" + String.valueOf(httpCon.getResponseCode()));
            System.out.println("Response size:" + httpCon.getContentLength());

            InputStream in = httpCon.getInputStream();
            if(in == null) {
                throw new IllegalArgumentException("in is null");
            }

            BufferedReader rd = new BufferedReader(new InputStreamReader(in));
            String line;
            StringBuffer response = new StringBuffer(); 
            while((line = rd.readLine()) != null) {
              response.append(line);
              response.append('\r');
            }
            rd.close();
            String s = response.toString();
            System.out.println("Response body: " + s);

            JSONParser parser = new JSONParser();
            JSONObject obj = (JSONObject)parser.parse(s);
            JSONObject authObj = (JSONObject)obj.get("object");
            JSONObject authResultObj = (JSONObject)authObj.get("authResult");
            if (authResultObj.get("status").toString().equalsIgnoreCase("SUCCESS")) {
                System.out.println("Succeed to login EMC SSO service");
                if (authResultObj.get("token") != null) {
                    _ctsession = authResultObj.get("token").toString();
                    System.out.println(_ctsession);
                } else {
                    throw new IllegalArgumentException("Failed to parse ctsession token as expected");
                }
            } else if (authResultObj.get("status").toString().equalsIgnoreCase("FAILED")) {
                JSONObject serviceFaultObj = (JSONObject)obj.get("serviceFault");
                String errstr = "";
                if (serviceFaultObj != null) {
                    errstr = "Please contact with EMC customer support.  EMC SSO service failed:" + serviceFaultObj.toString();
                    System.out.println(errstr);
                } else {
                    String operation = authResultObj.get("operation").toString();
                    errstr= "EMC SSO authentication result is " + operation;
                }
                System.out.println(errstr);
                throw new IllegalArgumentException(errstr);
            }
        } catch ( Exception e){
            throw new IllegalArgumentException(
                      MessageFormat.format("User {0} failed to login {1}: {2}", username, EMC_SSO_AUTH_SERVICE_URLPATH, e));
        }
    }

    /**
     * Initialize the SSL context for connecting to a remote repository
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.security.KeyManagementException
     */
    private static void initializeSslContext() throws NoSuchAlgorithmException, KeyManagementException {

        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted( final X509Certificate[] chain, final String authType ) {
            }
            @Override
            public void checkServerTrusted( final X509Certificate[] chain, final String authType ) {
            }
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        } };
        // Install the all-trusting trust manager
        SSLContext sslContext;

        sslContext = SSLContext.getInstance( "SSL" );
        sslContext.init( null, trustAllCerts, new java.security.SecureRandom() );
        // Create an ssl socket factory with our all-trusting manager
        _sslSocketFactory = sslContext.getSocketFactory();
    }

    /**
     * Open a URL connection and set the SSL context factory if necessary
     * @param url
     * @return a connection to the URL
     * @throws java.io.IOException
     */
    private static HttpURLConnection prepareConnection( URL url ) throws IOException {
        HttpURLConnection connection;
        connection = (HttpURLConnection) url.openConnection(_proxy);
        connection.setConnectTimeout(_timeout);
        connection.setReadTimeout(_timeout);
        if( url.getProtocol().equalsIgnoreCase("https")) {
            ((HttpsURLConnection)connection).setSSLSocketFactory(_sslSocketFactory);
        }
        return connection;
    }

    /**
     * Connect with remote target url for download
     * Client needs to read the token from returned JSON object for login request.
     * Token aka CTSESSION is one of the important attribute to access any application or rest services that is protected behind RSA.
     *     For Example: if support zone rest needs to be accessed then use the auth rest service read the token and set the cookie header with the token as shown
     * For download request:
     *   URI:			image url
     *   Format:			XML
     *   HTTP Method:		GET
     *   HTTP Header:       CTSESSION="token"
     *
     * @param url
     * @return HttpURLConnection
     * @throws RemoteRepositoryException
     */
    private static void connectImage(URL url) throws Exception {
    	HttpURLConnection httpCon = prepareConnection(url);
    	try {
            System.out.println("Connecting to URL " + url.toString());
            
            httpCon.setInstanceFollowRedirects(false);
            String cookie = "CTSESSION=" + _ctsession;
            httpCon.setRequestProperty("Cookie", cookie);
            httpCon.setRequestMethod("GET");
            httpCon.connect();
            System.out.println("Cookies for current connection:" + cookie);
            if(httpCon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalArgumentException("Http error code:" + httpCon.getResponseCode());
            }
            System.out.println("Image is located successfully and its size is " + httpCon.getContentLength());

            final InputStream in = httpCon.getInputStream();
            if(in == null) {
                throw new IllegalArgumentException("in is null");
            }
            byte[] buffer = new byte[0x100000];
            int len = in.read(buffer);
            if(len <= 0) {
                throw new IllegalArgumentException("getImageInputStream failed for ");
            } else {
                System.out.println("Succeed to read some data from image.");
            }
            in.close();
        } catch ( Exception e){
            throw new IllegalArgumentException(
                          MessageFormat.format("User {0} failed to connect with remote image {1}: {2}", _username, url.toString(), e));
        }
    }

    /**
     * Send a post request with content to the specified connection
     * @param connection connection to URL
     * @param postContent content to post
     * @throws Exception
     */
    private static void writePostContent(HttpURLConnection connection, String postContent) throws Exception {
        connection.setRequestMethod("POST");
        // set the output and input to true
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setAllowUserInteraction(false);
        // set the content length
        DataOutputStream dstream = null;
        try {
            connection.connect();
            dstream = new DataOutputStream(connection.getOutputStream());
            // write the post content
            dstream.writeBytes(postContent);
            dstream.flush();
        } finally {
            // flush the stream
            if (dstream != null) {
                try {
                    dstream.close();
                }
                catch (Exception ex) {
                    System.out.println("Exception while closing the stream.");
                }
            }
        }
    }

}

