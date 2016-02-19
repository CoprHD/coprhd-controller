/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.vnxfile;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for actually querying the JServer using httpclient
 * library to get response.
 */
public class DataRetriever {
    /**
     * Logger instance to log messages.
     */
    public static Logger _logger = LoggerFactory.getLogger(DataRetriever.class);

    /**
     * HttpClient instance.
     */
    private HttpClient _client = null;

    /**
     * Protocol instance to set in httpclient to use for communication.
     */
    private VNXFileProtocol _protocol = null;
    /**
     * set the default timeout for httpclient communications.
     */
    private String _timeout;

    /**
     * doLogin is responsible to connect the XML API Server which is running on
     * ControlStation and gets the session information.
     * 
     * @param authuri
     *            : server uri to post request.
     * @param username
     *            : username to connect to server.
     * @param password
     *            : password to connect to server.
     * @param portNumber
     *            : portNumber to connect to server.
     * @return
     * @throws VNXFilePluginException
     */
    public Object doLogin(final String authuri, final String username,
            final String password, final int portNumber) throws VNXFilePluginException {
        PostMethod postMethod = null;
        try {
            // Get Protocol
            _logger.debug("doLogin " + authuri + ":" + username + ":" + portNumber);
            Protocol protocol = _protocol.getProtocol(portNumber);
            Protocol.registerProtocol("https", protocol);
            _logger.info("Querying the url {}", authuri);
            postMethod = new PostMethod(authuri);
            postMethod.addParameter("user", username);
            postMethod.addParameter("password", password);
            postMethod.addParameter("Login", "Login");
            postMethod.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            postMethod.getParams().setVersion(HttpVersion.HTTP_1_1);
            setTimeoutValues();

            Level origLevel = LogManager.getRootLogger().getLevel();
            LogManager.getRootLogger().setLevel(Level.INFO);
            final int response = _client.executeMethod(postMethod);
            LogManager.getRootLogger().setLevel(origLevel);

            _logger.debug("connection timeout set {}", _client.getParams()
                    .getParameter("http.connection.timeout"));
            if (response != HttpStatus.SC_OK) {
                _logger.error(
                        "Invalid response received from XML API Server while getting cookie information. " +
                                "HTTP Error code: {}", response);
                throw new VNXFilePluginException(
                        "Invalid response recieved from XML API Server while getting cookie information.",
                        VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
            }
        } catch (final IOException ioEx) {
            _logger.error(
                    "IOException occurred while sending the Login request due to {}",
                    ioEx.getMessage());
            throw new VNXFilePluginException(
                    "IOException occurred while sending Login the request.",
                    ioEx.getCause());
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while sending the Login request due to {}",
                    ex.getMessage());
            throw new VNXFilePluginException(
                    "Exception occurred while sending the Login request.",
                    ex.getCause());
        }
        return postMethod;
    }

    /**
     * Client which sends the request streams and gets the response.
     * 
     * @return : response stream
     * @throws VNXFilePluginException
     */
    public Object execute(final String uri, final String cookie,
            final String session, final InputStream requestStream)
            throws VNXFilePluginException {
        _logger.debug("Invoking execute method to send request.");
        PostMethod postMethod = null;
        try {
            postMethod = new PostMethod(uri);
            _logger.debug("Querying the url {}", uri);
            postMethod.setRequestHeader(VNXFileConstants.COOKIE, cookie);
            if (null != session) {
                postMethod.setRequestHeader(VNXFileConstants.CELERRA_SESSION,
                        session);
            }
            final RequestEntity requestEntity = new InputStreamRequestEntity(
                    requestStream);
            postMethod.setRequestEntity(requestEntity);
            setTimeoutValues();
            final int response = _client.executeMethod(postMethod);
            _logger.debug("connection timeout set {}", _client.getParams()
                    .getParameter("http.connection.timeout"));
            if (response != HttpStatus.SC_OK) {
                _logger.error(
                        "Invalid response received from XML API Server while executing query. " +
                                "HTTP Error code: {}",
                        response);
                if (response == 503) {
                    throw new VNXFilePluginException(
                            String.format(
                                    "The server is currently unable to handle the request due to a temporary overloading: HTTP code: %s:",
                                    response),
                            VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
                } else {
                    throw new VNXFilePluginException(
                            String.format("Invalid response received from server: HTTP code %s:", response),
                            VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
                }

            }
        } catch (final IOException ioEx) {
            _logger.error(
                    "IOException occurred while sending the API request due to {}",
                    ioEx.getMessage());
            throw new VNXFilePluginException(
                    "IOException occurred while sending the API request.",
                    ioEx.getCause());
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while sending the API request due to {}",
                    ex.getMessage());
            throw new VNXFilePluginException(
                    "Exception occurred while sending the API request.",
                    ex.getCause());
        } finally {
            try {
                requestStream.close();
            } catch (final IOException ioEx) {
                _logger.error(
                        "IOException occurred while closing API request stream due to {}",
                        ioEx.getMessage());
            }
        }
        return postMethod;
    }

    /**
     * Client which sends the request to disconnect session with XML API Server.
     * 
     * @throws VNXFilePluginException
     */
    public void disconnect(final String uri, final String cookie,
            final String session) throws VNXFilePluginException {
        _logger.debug("Invoking disconnect method to send request.");
        PostMethod postMethod = null;
        try {
            postMethod = new PostMethod(uri);
            _logger.debug("Querying the url {} to disconnect session.", uri);
            postMethod.setRequestHeader(VNXFileConstants.COOKIE, cookie);
            if (null != session) {
                postMethod.setRequestHeader(VNXFileConstants.CELERRA_SESSION,
                        session);
                postMethod.setRequestHeader("CelerraConnector-Ctl",
                        "DISCONNECT");
                postMethod.setRequestHeader("Content-Length", "0");
            }
            setTimeoutValues();
            final int response = _client.executeMethod(postMethod);
            if (response != HttpStatus.SC_OK) {
                _logger.error(
                        "Invalid response received from XML API Server while disconnecting session." +
                                "HTTP Error code: {}",
                        response);
                throw new VNXFilePluginException(
                        "Invalid response received from XML API Server while disconnecting session",
                        VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
            }
            _logger.debug("Session disconnected with XML API Server.");
        } catch (final IOException ioEx) {
            _logger.error(
                    "IOException occurred while sending the disconnect request due to {}",
                    ioEx.getMessage());
            throw new VNXFilePluginException(
                    "IOException occurred while sending the disconnect request.",
                    ioEx.getCause());
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while sending the disconnect request due to {}",
                    ex.getMessage());
            throw new VNXFilePluginException(
                    "Exception occurred while sending the disconnect request.",
                    ex);
        }
    }

    /**
     * set the timeout values in client.
     */
    private void setTimeoutValues() {
        // set the socket timeout waiting for data & connection timeout value in milli seconds.
        _client.getParams().setParameter("http.socket.timeout",
                Integer.valueOf(_timeout));
        _client.getParams().setParameter("http.connection.timeout",
                Integer.valueOf(_timeout));

    }

    /**
     * @return the client
     */
    public HttpClient getClient() {
        return _client;
    }

    /**
     * @param client
     *            the client to set
     */
    public void setClient(final HttpClient client) {
        _client = client;
    }

    /**
     * @return the protocol
     */
    public VNXFileProtocol getProtocol() {
        return _protocol;
    }

    /**
     * @param protocol the protocol to set
     */
    public void setProtocol(final VNXFileProtocol protocol) {
        _protocol = protocol;
    }

    /**
     * @return the _timeout
     */
    public String getTimeout() {
        return _timeout;
    }

    /**
     * @param _timeout the _timeout to set
     */
    public void setTimeout(final String timeoutStr) {
        _timeout = timeoutStr;
    }

}
