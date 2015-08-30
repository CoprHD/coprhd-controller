package com.emc.storageos.ecs.api;

import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.concurrent.Future;

public class RESTClient {
	private Client _client;

	/**
	 * Constructor
	 *
	 * @param client Jersey client to use
	 */
	public RESTClient(Client client) {
		_client = client;
	}

	/**
	 * Get resource as a ClientResponse
	 *
	 * @param   url             url for the resource to get
	 * @return  ClientResponse  response
	 */
	public ClientResponse get(URI url) {
		return _client.resource(url).get(ClientResponse.class);
	}
	/**
	 * Close the client
	 */
	public void close() {
		_client.destroy();
	}

}
