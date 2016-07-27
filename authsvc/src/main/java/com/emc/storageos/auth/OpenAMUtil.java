package com.emc.storageos.auth;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.util.List;

public class OpenAMUtil {

    private static final Logger _log = LoggerFactory.getLogger(OpenAMUtil.class);

    private static SSLConnectionSocketFactory sslsf = SSLUtil.getSocketFactory(true);

    private static CloseableHttpClient httpclient = HttpClients.custom()
            .setSSLSocketFactory(sslsf)
            .disableRedirectHandling()
            .build();

    private static HttpClientContext context = HttpClientContext.create();


    public static String login(String openamIdpHost, String username, String password) {

        String openAMToken = null;
        try {
            HttpPost httpPost = new HttpPost("http://" + openamIdpHost + ":8080/openam/json/authenticate");
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("X-OpenAM-Username", username);
            httpPost.addHeader("X-OpenAM-Password", password);

            System.out.println("Executing request " + httpPost.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(httpPost, context);
            String body = EntityUtils.toString(response.getEntity());
            System.out.println(body);

            JSONObject result = new JSONObject(body);
            openAMToken = (String)result.get("tokenId");
            System.out.println("token = " + openAMToken);

        } catch (Exception ex) {
            _log.error("exception login: ", ex);
        }
        return openAMToken;
    }


    public static String authorizationCodeFlow(String openamToken, String openamIdpHost, String redirect_url, String clientId) {
        String code = null;

        try {
            HttpPost httpPost = new HttpPost("http://" + openamIdpHost + ":8080/openam/oauth2/authorize");
            httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
            httpPost.addHeader("Cookie", "iPlanetDirectoryPro=" + openamToken);

            String payload = "response_type=code&scope=openid%20memberOf&client_id="+ clientId
                    +"&redirect_uri="+ redirect_url +"&save_consent=1&decision=Allow";
            HttpEntity entity = new ByteArrayEntity(payload.getBytes("UTF-8"));
            httpPost.setEntity(entity);

            System.out.println("Executing request " + httpPost.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(httpPost, context);
            Header locationHeader = response.getHeaders("Location")[0];
            System.out.println(locationHeader.getValue());
            List<NameValuePair> params = URLEncodedUtils.parse(new URI(locationHeader.getValue()), "UTF-8");
            for (NameValuePair param : params) {
                if (param.getName().equalsIgnoreCase("code")) {
                    code = param.getValue();
                    break;
                }
            }

        } catch (Exception ex) {
            _log.error("authorization code flow: ", ex);
        }

        return code;
    }


    public static String getOpenIDAuthEndpoint(String openamIdpHost) {
        if (openamIdpHost == null) {
            openamIdpHost = "lglou242.lss.emc.com";
        }
        return "http://" + openamIdpHost + ":8080/openam/oauth2/authorize";
    }
}
