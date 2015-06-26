/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client;

import com.emc.vipr.client.exceptions.ViPRException;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.impl.SSLUtil;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.core.MediaType;
import java.net.URI;

public class ClientConfig {
    public static final int DEFAULT_LOGGING_ENTITY_LENGTH = 2048;
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final int DEFAULT_RETRY_INTERVAL = 60 * 1000;  // 60 seconds
    public static final int DEFAULT_TASK_POLLING_INTERVAL = 10 * 1000; // 10 seconds
    public static final int DEFAULT_CONNECTION_TIMEOUT = 5 * 60 * 1000; // 5 minutes
    public static final int DEFAULT_READ_TIMEOUT = 5 * 60 * 1000; // 5 minutes
    public static final String DEFAULT_PROTOCOL = "https";
    public static final int DEFAULT_API_PORT = 4443;
    public static final int DEFAULT_PORTAL_PORT = 443;
    public static final int DEFAULT_BULK_SIZE = 500;
    public static final int DEFAULT_MAX_CONCURRENT_TASK_REQUESTS = 50;
    public static final int DEFAULT_TASKS_EXECUTION_TIMEOUT_SECONDS = 30;
    public static final int SESSION_KEY_RENEW_TIMEOUT = 1000 * 60 * 60 * 7; // 7 hours

    private int maxConcurrentTaskRequests = DEFAULT_MAX_CONCURRENT_TASK_REQUESTS;
	private int tasksExecutionTimeoutSeconds = DEFAULT_TASKS_EXECUTION_TIMEOUT_SECONDS;
    private String mediaType = MediaType.APPLICATION_XML;
    private boolean requestLoggingEnabled = true;
    private int loggingEntityLength = DEFAULT_LOGGING_ENTITY_LENGTH;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private int retryInterval = DEFAULT_RETRY_INTERVAL;
    private int taskPollingInterval = DEFAULT_TASK_POLLING_INTERVAL;
    private int readTimeout = DEFAULT_READ_TIMEOUT;
    private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private String protocol = DEFAULT_PROTOCOL;
    private int port = DEFAULT_API_PORT;
    private int portalPort = DEFAULT_PORTAL_PORT;
    private int bulkSize = DEFAULT_BULK_SIZE;
    private int sessionKeyRenewTimeout = SESSION_KEY_RENEW_TIMEOUT;
    private String host;
    private SSLSocketFactory socketFactory;
    private HostnameVerifier hostnameVerifier;
    
    public boolean isRequestLoggingEnabled() {
        return requestLoggingEnabled;
    }

    /**
     * Sets if request logging should be enabled. This will log each called made
     * to the API. Defaults to true.
     *
     * @param requestLoggingEnabled True to enable logging each request, False to disable.
     */
    public void setRequestLoggingEnabled(boolean requestLoggingEnabled) {
        this.requestLoggingEnabled = requestLoggingEnabled;
    }

    public int getLoggingEntityLength() {
        return loggingEntityLength;
    }

    /**
     * Sets the maximum length of an HTTP request to be logged. If the size of the
     * request exceeds the specified length, it will be truncated. Defaults to
     * logging 2048 characters.
     *
     * @param loggingEntityLength Maximum length of HTTP request before truncation.
     */
    public void setLoggingEntityLength(int loggingEntityLength) {
        this.loggingEntityLength = loggingEntityLength;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the maximum number of retries for any retryable errors. Retry will
     * automatically happen on any request that is marked as retriable. Setting
     * this to 0 disables retries. Defaults to 3.
     *
     * @param maxRetries Maximum number of retries.
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    /**
     * Interval in milliseconds before retrying a request after recieving an error.
     * Defaults to 60 seconds.
     *
     * @param retryInterval Milliseconds between retry requests.
     */
    public void setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
    }

    public String getMediaType() {
        return mediaType;
    }

    /**
     * Sets the media type to be used for API requests. This can be either 'application/xml' or
     * 'application/json'. Defaults to 'application/xml'.
     *
     * @param mediaType Media type to use.
     */
    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public int getTaskPollingInterval() {
        return taskPollingInterval;
    }

    /**
     * Interval in milliseconds between polling requests to check task status. Defaults
     * to 10 seconds.
     *
     * @param taskPollingInterval Milliseconds between task poll requests.
     */
    public void setTaskPollingInterval(int taskPollingInterval) {
        this.taskPollingInterval = taskPollingInterval;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Sets the connection timeout in milliseconds for API requests. Defaults to 5 minutes.
     *
     * @param connectionTimeout Timeout in millliseconds.
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the read timeout in milliseconds for API requests. Defaults to 5 minutes.
     *
     * @param readTimeout Timeout in millliseconds.
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getHost() {
        return host;
    }

    /**
     * Sets the host of the target environment.
     *
     * @param host Hostname or IP address for the Virtual IP of the target environment.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Sets the target port for HTTP requests to the API. Defaults to 4443.
     *
     * @param port Target port.
     */
    public void setPort(int port) {
        if (port <=0 || port > 65535) {
            throw new ViPRException("Port specified is not a valid port");
        }
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * Sets the protocol for HTTP requests to the API. This should be either
     * 'http' or 'https'. Defaults to 'https'.
     *
     * @param protocol HTTP Protocol.
     */
    public void setProtocol(String protocol) {
        if (protocol == null) {
            throw new ViPRException("Protocol must not be null");
        }
        if (!(protocol.equals("https") || protocol.equals("http"))) {
            throw new ViPRException("Protocol must be 'http' or 'https'");
        }
        this.protocol = protocol;
    }

    public int getPortalPort() {
        return portalPort;
    }

    /**
     * Sets the target port for HTTP requests to the portal API. Defaults to 443.
     *
     * @param portalPort Target port for the portal.
     */
    public void setPortalPort(int portalPort) {
        this.portalPort = portalPort;
    }

    public int getBulkSize() {
        return bulkSize;
    }

    /**
     * Sets the number of items to retrieve per bulk request. When doing large queries it will query the
     * bulk API in chucks of the size set by this option. Defaults to 500.
     *
     * @param bulkSize Number of items to retrieve per bulk request. Maximum is 4000.
     */
    public void setBulkSize(int bulkSize) {
        if (bulkSize < 1 || bulkSize > 4000) {
            throw new ViPRException("BulkSize must be between 1 and 4000 inclusive");
        }
        this.bulkSize = bulkSize;
    }

    /**
     * Provide an alternate socket factory for the clients
     *
     * @param socketFactory custom socket factory
     */
    public void setSocketFactory(SSLSocketFactory socketFactory) {
        this.socketFactory = socketFactory;
    }

    /**
     * Returns the provided SSLSocketFactory, or null
     *
     * @return The custom SSLSocketFactory
     */
    public SSLSocketFactory getSocketFactory() {
        return socketFactory;
    }

    /**
     * Returns the provided HostnameVerifier, or null
     *
     * @return The custom HostnameVerifier
     */
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    /**
     * Provide and alternate Hostname Verifier for the clients
     *
     * @param hostnameVerifier custom hostname verifier
     */
    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }
    
    /**
     * Returns the maximum concurrent task threads that this client can
     * spawn
     * 
     * @return Maximum concurrent task threads
     */
    public int getMaxConcurrentTaskRequests() {
		return maxConcurrentTaskRequests;
	}

	/**
	 * Sets the Maximum concurrent task threads that this client can spawn
	 * 
	 * @param maxConcurrentTaskRequests
	 */
	public void setMaxConcurrentTaskRequests(int maxConcurrentTaskRequests) {
		this.maxConcurrentTaskRequests = maxConcurrentTaskRequests;
	}
	
	/**
	 * Adds maximum concurrent task request thread count
	 * 
	 * @param maxConcurrentTaskRequests
	 * @return this Config
	 */
	public ClientConfig withMaxConcurrentTaskRequests(int maxConcurrentTaskRequests) {
		this.setMaxConcurrentTaskRequests(maxConcurrentTaskRequests);
		return this;
	}

	/**
	 * Returns task execution timeout in seconds
	 * 
	 * @return
	 */
	public int getTasksExecutionTimeoutSeconds() {
		return tasksExecutionTimeoutSeconds;
	}

	/**
	 * Sets the total task execution timeout in seconds
	 * 
	 * @param tasksExecutionTimeoutSeconds
	 */
	public void setTasksExecutionTimeoutSeconds(int tasksExecutionTimeoutSeconds) {
		this.tasksExecutionTimeoutSeconds = tasksExecutionTimeoutSeconds;
	}
	
	/**
     * Returns session key renew timeout
     * 
     * @return
     */
    public int getSessionKeyRenewTimeout() {
        return sessionKeyRenewTimeout;
    }

    /**
     * Sets the session key renew timeout
     * 
     * @param tasksExecutionTimeoutSeconds
     */
    public void setSessionKeyRenewTimeout(int sessionKeyRenewTimeout) {
        this.sessionKeyRenewTimeout = sessionKeyRenewTimeout;
    }
	
	/**
	 * Adds the total tasks execution timeout in seconds
	 * 
	 * @param tasksExecutionTimeoutSeconds
	 * @return This configuration
	 */
	public ClientConfig withTasksExecutionTimeoutSeconds(int tasksExecutionTimeoutSeconds) {
		this.setTasksExecutionTimeoutSeconds(tasksExecutionTimeoutSeconds);
		return this;
	}

    /**
     * Sets the SSLSocketFactory and HostnameVerifier to ignore all SSL certificates. This is suitable for a default
     * installation using self-signed certificates. This is <b>not</b> intended for production use as it bypasses
     * important SSL security.
     *
     * @param ignoreCertificates True if SSL trust should be disabled
     * @see #setSocketFactory(javax.net.ssl.SSLSocketFactory)
     * @see #setHostnameVerifier(javax.net.ssl.HostnameVerifier)
     */
    public void setIgnoreCertificates(boolean ignoreCertificates) {
        if (ignoreCertificates) {
            setSocketFactory(SSLUtil.getTrustAllSslSocketFactory());
            setHostnameVerifier(SSLUtil.getNullHostnameVerifier());
        }
    }

    /**
     * Sets the bulk size and returns the updated configuration.
     *
     * @param bulkSize Number of items to retrieve per bulk request. Maximum is 4000.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withBulkSize(int bulkSize) {
        setBulkSize(bulkSize);
        return this;
    }

    /**
     * Sets the host and returns the updated configuration.
     *
     * @see #setHost(String)
     * @param host Hostname or IP address for the Virtual IP of the target environment.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withHost(String host) {
        setHost(host);
        return this;
    }

    /**
     * Sets the port and returns the updated configuration.
     *
     * @see #setPort(int)
     * @param port Target port to set.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withPort(int port) {
        setPort(port);
        return this;
    }

    /**
     * Sets the protocol and returns the updated configuration.
     *
     * @see #setProtocol(String)
     * @param protocol HTTP Protocol to use.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withProtocol(String protocol) {
        setProtocol(protocol);
        return this;
    }

    /**
     * Sets the connection timeout and returns the updated configuration.
     *
     * @see #setConnectionTimeout(int)
     * @param connectionTimeout Connection timeout to set.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withConnectionTimeout(int connectionTimeout) {
        setConnectionTimeout(connectionTimeout);
        return this;
    }

    /**
     * Sets the read timeout and returns the updated configuration.
     *
     * @see #setReadTimeout(int)
     * @param readTimeout Read timeout to set.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withReadTimeout(int readTimeout) {
        setReadTimeout(readTimeout);
        return this;
    }

    /**
     * Sets the media type and returns the updated configuration.
     *
     * @see #setMediaType(String)
     * @param mediaType Media type to set.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withMediaType(String mediaType) {
        setMediaType(mediaType);
        return this;
    }

    /**
     * Sets the request logging enabled and returns the updated configuration.
     *
     * @see #setRequestLoggingEnabled(boolean)
     * @return The updated ClientConfig object.
     */
    public ClientConfig withRequestLoggingEnabled() {
        setRequestLoggingEnabled(true);
        return this;
    }

    /**
     * Sets the request logging disabled and returns the updated configuration.
     *
     * @see #setRequestLoggingEnabled(boolean)
     * @return The updated ClientConfig object.
     */
    public ClientConfig withRequestLoggingDisabled() {
        setRequestLoggingEnabled(false);
        return this;
    }

    /**
     * Sets the logging entity length and returns the updated configuration.
     *
     * @see #setLoggingEntityLength(int)
     * @param loggingEntityLength Logging entity length to set.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withLoggingEntityLength(int loggingEntityLength) {
        setLoggingEntityLength(loggingEntityLength);
        return this;
    }

    /**
     * Sets the max retries and returns the updated configuration.
     *
     * @see #setMaxRetries(int)
     * @param maxRetries Max retries to set.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withMaxRetries(int maxRetries) {
        setMaxRetries(maxRetries);
        return this;
    }

    /**
     * Sets the retry interval and returns the updated configuration.
     *
     * @see #withRetryInterval(int)
     * @param retryInterval Retry interval to set.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withRetryInterval(int retryInterval) {
        setRetryInterval(retryInterval);
        return this;
    }

    /**
     * Sets the task polling interval and returns the updated configuration.
     *
     * @see #setTaskPollingInterval(int)
     * @param taskPollingInterval Task polling interval to set
     * @return The updated ClientConfig object.
     */
    public ClientConfig withTaskPollingInterval(int taskPollingInterval) {
        setTaskPollingInterval(taskPollingInterval);
        return this;
    }

    /**
     * Sets the portal port and returns the updated configuration.
     *
     * @see #setPortalPort(int)
     * @param portalPort Target portal port to set.
     * @return The updated ClientConfig object.
     */
    public ClientConfig withPortalPort(int portalPort) {
        setPortalPort(port);
        return this;
    }

    /**
     * Sets the SSLSocketFactory and returns the updated configuration.
     *
     * @see #setSocketFactory(javax.net.ssl.SSLSocketFactory)
     * @param factory The SSLSocketFactory to use
     * @return the updated ClientConfig object
     */
    public ClientConfig withSocketFactory(SSLSocketFactory factory) {
        setSocketFactory(factory);
        return this;
    }

    /**
     * Sets the HostnameVerifier and returns the updated configuration.
     *
     * @see #setHostnameVerifier(javax.net.ssl.HostnameVerifier)
     * @param verifier The HostnameVerifier to use
     * @return the updated ClientConfig object
     */
    public ClientConfig withHostnameVerifier(HostnameVerifier verifier) {
        setHostnameVerifier(verifier);
        return this;
    }

    /**
     * Sets the SSLSocketFactory and HostnameVerifier to ignore all SSL certificates and returns the updated
     * configuration. This is suitable for a default installation using self-signed certificates. This
     * is <b>not</b> intended for production use as it bypasses important SSL security.
     *
     * @see #setIgnoreCertificates(boolean)
     * @return the updated ClientConfig object
     */
    public ClientConfig withIgnoringCertificates(boolean ignoringCertificates) {
        setIgnoreCertificates(ignoringCertificates);
        return this;
    }
    
    /**
     * Sets the session key renew timeout.
     *
     * @see #setSessionKeyRenewTimeout(int)
     * @return the updated ClientConfig object
     */
    public ClientConfig withSessionKeyRenewTimeout(int sessionKeyRenewTimeout) {
        setSessionKeyRenewTimeout(sessionKeyRenewTimeout);
        return this;
    }

    /**
     * Creates the RestClient. This is provided here so Testcases are able to override the base implementation.
     */
    protected RestClient newClient() {
        URI baseUri = URI.create(String.format("%s://%s:%s", protocol, host, port));
        return new RestClient(baseUri, this);
    }

    /**
     * Creates the RestClient for the portal. This is provided here so Testcases are able to override the base implementation.
     */
    protected RestClient newPortalClient() {
        URI baseUri = URI.create(String.format("%s://%s:%s", protocol, host, portalPort));
        return new RestClient(baseUri, this);
    }
}
