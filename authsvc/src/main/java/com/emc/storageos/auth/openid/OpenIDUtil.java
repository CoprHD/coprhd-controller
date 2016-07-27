/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */

package com.emc.storageos.auth.openid;

import com.emc.storageos.auth.OpenAMUtil;
import com.emc.storageos.auth.SSLUtil;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.codec.binary.Base64;

import java.net.URLEncoder;


public class OpenIDUtil {

    private static final Logger _log = LoggerFactory.getLogger(OpenIDUtil.class);

    private static SSLConnectionSocketFactory sslsf = SSLUtil.getSocketFactory(true);

    private static CloseableHttpClient httpclient = HttpClients.custom()
            .setSSLSocketFactory(sslsf)
            .disableRedirectHandling()
            .build();

    private static HttpClientContext context = HttpClientContext.create();

    private static String CLIENT_ID = "vipr";
    private static String CLIENT_PASSOWORD = "password";
    private static String REDIRECT_URL = "https://lglw1104.lss.emc.com:4443/oiclogin";
    private static String AUTH_END_POINT = "http://lglou242.lss.emc.com:8080/openam/oauth2/authorize";
    private static String TOKEN_END_POINT = "http://lglou242.lss.emc.com:8080/openam/oauth2/access_token";
    private static String USERINFO_END_POINT = "http://lglou242.lss.emc.com:8080/openam/oauth2/userinfo";


    /**
     * resolve authorization code to ID_token, and extract username (sub) from it.
     *
     * @param code
     * @return
     */
    public static String resolveCode(String code) {

        String username = null;
        try {

            HttpPost resolveCodeRequest = new HttpPost(TOKEN_END_POINT);

            String userpass = CLIENT_ID + ":" + CLIENT_PASSOWORD;
            String basicAuth = "Basic " + new String(new Base64().encode(userpass.getBytes()));
            resolveCodeRequest.addHeader("Authorization", basicAuth);
            resolveCodeRequest.addHeader("Content-Type", "application/x-www-form-urlencoded");

            String requestBody = "grant_type=authorization_code&realm=/&code=" + code
                    +"&redirect_uri=" + URLEncoder.encode(REDIRECT_URL, "UTF-8");;
            HttpEntity entity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
            resolveCodeRequest.setEntity(entity);

            System.out.println("Executing request " + resolveCodeRequest.getRequestLine());
            _log.info("Executing request " + resolveCodeRequest.getRequestLine());

            CloseableHttpResponse response = httpclient.execute(resolveCodeRequest, context);
            try {
                String body = EntityUtils.toString(response.getEntity());
                System.out.println(body);
                _log.info(body);

                String accessToken = getAccessToken(body);

                getUserInfo(accessToken);

                return getUserName(body);
            } finally {
                response.close();
            }

        } catch (Exception ex) {
            System.out.println("exception resolve code: " + ex.getMessage());
        }
        return username;
    }

    private static String getUserName(String codeResponse) throws Exception {

        JSONObject result = new JSONObject(codeResponse);

        String token = (String)result.get("id_token");
        token = token.split("\\.")[1];
        String tokenStr = new String(new Base64().decode(token));
        System.out.println(tokenStr);
        _log.info(tokenStr);

        JSONObject id_token = new JSONObject(tokenStr);
        return (String)id_token.get("sub");
    }

    private static String getAccessToken(String codeResponse) throws Exception {
        JSONObject result = new JSONObject(codeResponse);
        return (String)result.get("access_token");
    }

    public static void passwordGrantType() {

        try {

            HttpPost resolveCodeRequest = new HttpPost(TOKEN_END_POINT);

            String userpass = CLIENT_ID + ":" + CLIENT_PASSOWORD;
            String basicAuth = "Basic " + new String(new Base64().encode(userpass.getBytes()));
            resolveCodeRequest.addHeader("Authorization", basicAuth);
            resolveCodeRequest.addHeader("Content-Type", "application/x-www-form-urlencoded");

            String requestBody = "grant_type=password&username=fred&password=Password1&scope=openid%20memberOf";
            HttpEntity entity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
            resolveCodeRequest.setEntity(entity);

            System.out.println("Executing request " + resolveCodeRequest.getRequestLine());

            CloseableHttpResponse response = httpclient.execute(resolveCodeRequest, context);
            try {
                String body = EntityUtils.toString(response.getEntity());
                System.out.println(body);

                String accessToken = getAccessToken(body);
                System.out.println("access token: " + accessToken);
                getUserInfo(accessToken);

            } finally {
                response.close();
            }

        } catch (Exception ex) {
            System.out.println("exception resolve artifact: " + ex.getMessage());
        }
    }


    public static void getUserInfo(String accessToken) {
        try {

            HttpPost resolveCodeRequest = new HttpPost(USERINFO_END_POINT);

            resolveCodeRequest.addHeader("Authorization", "Bearer " + accessToken);

//            String requestBody = "scope=profile%20email%20cn%20memberOf%20isMemberOf";
            String requestBody = "scope=email";
            HttpEntity entity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
            resolveCodeRequest.setEntity(entity);

            System.out.println("Executing request " + resolveCodeRequest.getRequestLine());

            CloseableHttpResponse response = httpclient.execute(resolveCodeRequest, context);
            try {
                String body = EntityUtils.toString(response.getEntity());
                System.out.println(body);

            } finally {
                response.close();
            }

        } catch (Exception ex) {
            System.out.println("exception resolve artifact: " + ex.getMessage());
        }
    }



    public static void main(String[] args) {

        passwordGrantType();

//        String openamToken = OpenAMUtil.login("lglou242.lss.emc.com", "scott", "Password1");
//
//        String code = OpenAMUtil.authorizationCodeFlow(openamToken, "lglou242.lss.emc.com", REDIRECT_URL, CLIENT_ID);
//        System.out.println(code);
//        String username = resolveCode(code);
//        System.out.println(username);
    }
}
