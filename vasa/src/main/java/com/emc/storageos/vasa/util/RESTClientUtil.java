/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/* 
Copyright (c) 2012-13 EMC Corporation
All Rights Reserved

This software contains the intellectual property of EMC Corporation
or is licensed to EMC Corporation from third parties.  Use of this
software and the intellectual property contained therein is expressly
imited to the terms and conditions of the License Agreement under which
it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.log4j.Logger;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;

public class RESTClientUtil {

	private static final Logger log = Logger.getLogger(RESTClientUtil.class);

	private final static String AUTH_TOKEN_HEADER = "X-SDS-AUTH-TOKEN";
	private String _baseURL;
	// private boolean _authorizationFlag;

	private ClientConfig _config;
	private Client _client;
	private String _username;
	private String _password;
	private static final int CALL_COUNT_FOR_AUTHENTICATION = 10;

	public RESTClientUtil(String baseURL) {
		this._baseURL = baseURL;
		log.trace("base URL:" + this._baseURL);
		// this._authorizationFlag = false;
		_config = new DefaultClientConfig();
		_client = Client.create(_config);
	}

	/**
	 * 
	 * @param uri
	 *            endpoint uri of REST api
	 * @param clazz
	 *            the class of data object to be binded with as response
	 * @return the response data object
	 * @throws NoSuchAlgorithmException
	 * @throws UniformInterfaceException
	 */

	public <T> T queryObject(String uri, Class<T> clazz)
			throws NoSuchAlgorithmException, UniformInterfaceException {

		final String methodName = "queryObject(): ";

		log.trace(methodName + "Entry with inputs uri[" + uri + "] class["
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

}


