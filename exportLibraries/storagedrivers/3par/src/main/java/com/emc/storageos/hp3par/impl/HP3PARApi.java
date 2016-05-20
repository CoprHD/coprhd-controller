/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.net.URI;
import java.text.MessageFormat;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.codehaus.jettison.json.JSONObject;

import com.emc.storageos.hp3par.command.CPGCommandResult;
import com.emc.storageos.hp3par.command.CPGMember;
import com.emc.storageos.hp3par.command.PortCommandResult;
import com.emc.storageos.hp3par.command.PortStatisticsCommandResult;
import com.emc.storageos.hp3par.command.SystemCommandResult;
import com.emc.storageos.hp3par.command.VolumeDetailsCommandResult;
import com.emc.storageos.hp3par.connection.RESTClient;
import com.google.gson.Gson;
import com.google.json.JsonSanitizer;
import com.sun.jersey.api.client.ClientResponse;

import static com.google.json.JsonSanitizer.*;

/**
 * Implements communication with 3PAR storage  
 */

public class HP3PARApi {
    private final URI _baseUrl;
    private final RESTClient _client;
    private Logger _log = LoggerFactory.getLogger(HP3PARApi.class);
    private String _authToken;
    private String _user; 
    private String _password;

    private static final URI URI_LOGIN = URI.create("/api/v1/credentials");
    private static final String URI_SYSTEM = "/api/v1/system";
    private static final String URI_CPGS = "/api/v1/cpgs";
    private static final String URI_CPG_DETAILS = "/api/v1/cpgs/{0}";
    private static final String URI_PORTS = "/api/v1/ports";
    private static final String URI_PORT_STATISTICS = "/api/v1/systemreporter/attime/portstatistics/daily";
    private static final String URI_CREATE_VOLUME = "/api/v1/volumes";
    private static final String URI_VOLUME_DETAILS = "/api/v1/volumes/{0}";
    private static final String URI_EXPAND_VOLUME = "/api/v1/volumes/{0}";
    private static final String URI_DELETE_VOLUME = "/api/v1/volumes/{0}";
    

    public HP3PARApi(URI endpoint, RESTClient client, String userName, String pass) {
        _baseUrl = endpoint;
        _client = client;
        _user = userName;
        _password = pass;
    }

    /**
     * Close client resources
     */
    public void close() {
        _client.close();
    }
    
    /**
     * Get authentication token from the storage
     * @param user user name 
     * @param password password
     * @return authentication token
     * @throws Exception
     */
    public String getAuthToken(String user, String password) throws Exception {
        _log.info("3PARDriver:getAuthToken enter");
        String authToken = null;
        ClientResponse clientResp = null;
        String body= "{\"user\":\"" + user + "\", \"password\":\"" + password + "\"}";

        try {
            clientResp = _client.post_json(_baseUrl.resolve(URI_LOGIN), body);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                JSONObject jObj = clientResp.getEntity(JSONObject.class);
                authToken = jObj.getString("key");
                this._authToken = authToken;
                this._user = user;
                this._password = password;
                _log.info("3PARDriver:getAuthToken set");
            }
            return authToken;
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getAuthToken leave");
        } //end try/catch/finally
    }

    /**
     * Get authentication token from the storage
     * @return authentication token
     * @throws Exception
     */
    public String getAuthToken() throws Exception {
        _log.info("3PARDriver:getAuthToken enter, after expiry");
        String authToken = null;
        ClientResponse clientResp = null;
        String body= "{\"user\":\"" + _user + "\", \"password\":\"" + _password + "\"}";

        try {
            clientResp = _client.post_json(_baseUrl.resolve(URI_LOGIN), body);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                JSONObject jObj = clientResp.getEntity(JSONObject.class);
                authToken = jObj.getString("key");
            }
            this._authToken = authToken;
            return authToken;
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getAuthToken leave, after expiry");
        } //end try/catch/finally
    }

    /**
     * Gets the storage array information
     * @return array details
     * @throws Exception
     */
    public SystemCommandResult getSystemDetails() throws Exception {
        _log.info("3PARDriver:getSystemDetails enter");
        ClientResponse clientResp = null;

        try {
            clientResp = get(URI_SYSTEM);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:getSystemDetails 3PAR response is {}", responseString);
                SystemCommandResult systemRes = new Gson().fromJson(sanitize(responseString),
                        SystemCommandResult.class);
                return systemRes;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getSystemDetails leave");
        } //end try/catch/finally
    }
    
    /**
     * Gets 3PAR CPG attributes
     * @return CPG details
     * @throws Exception
     */
    public CPGCommandResult getAllCPGDetails() throws Exception {
        _log.info("3PARDriver:getAllCPGDetails enter");
        ClientResponse clientResp = null;

        try {
            clientResp = get(URI_CPGS);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:getAllCPGDetails 3PAR response is {}", responseString);
                CPGCommandResult cpgResult = new Gson().fromJson(sanitize(responseString),
                        CPGCommandResult.class);
                return cpgResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getSystemDetails leave");
        } //end try/catch/finally
    }    
    
    public CPGMember getCPGDetails(String name) throws Exception {
        _log.info("3PARDriver:getCPGDetails enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_CPG_DETAILS, name);

        try {
            clientResp = get(path);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:getCPGDetails 3PAR response is {}", responseString);
                CPGMember cpgResult = new Gson().fromJson(sanitize(responseString),
                        CPGMember.class);
                return cpgResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getSystemDetails leave");
        } //end try/catch/finally
    }    

    /**
     * Gets host port information
     * @return port details
     * @throws Exception
     */
    public PortCommandResult getPortDetails() throws Exception {
        _log.info("3PARDriver:getPortDetails enter");
        ClientResponse clientResp = null;

        try {
            clientResp = get(URI_PORTS);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:getPortDetails 3PAR response is {}", responseString);
                PortCommandResult portResult = new Gson().fromJson(sanitize(responseString),
                        PortCommandResult.class);
                return portResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getPortDetails leave");
        } //end try/catch/finally
    }    
    
    /**
     * Get port statistics information
     * @return port details
     * @throws Exception
     */
    public PortStatisticsCommandResult getPortStatisticsDetail() throws Exception {
        _log.info("3PARDriver:getPortStatisticsDetail enter");
        ClientResponse clientResp = null;

        try {
            clientResp = get(URI_PORT_STATISTICS);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriveri:getPortStatisticsDetail 3PAR response is {}", responseString);
                PortStatisticsCommandResult portStatResult = new Gson().fromJson(sanitize(responseString),
                        PortStatisticsCommandResult.class);
                return portStatResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getPortStatisticsDetail leave");
        } //end try/catch/finally
    }

    public void createVolume(String name, String cpg, Boolean thin, Long size) throws Exception {
        _log.info("3PARDriver:createVolume enter");
        ClientResponse clientResp = null;
        String body = "{\"name\":\"" + name + "\", \"cpg\":\"" + cpg + 
                "\", \"tpvv\":" + thin.toString() + ", \"sizeMiB\":" + size.toString() + "}";

        try {
            clientResp = post(URI_CREATE_VOLUME, body);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = getHeaderFieldValue(clientResp, "Location");
                _log.info("3PARDriver:createVolume 3PAR response is Location: {}", responseString);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:createVolume leave");
        } //end try/catch/finally
    }

    public VolumeDetailsCommandResult getVolumeDetails(String name) throws Exception {
        _log.info("3PARDriver:getVolumeDetails enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_VOLUME_DETAILS, name);
        
        try {
            clientResp = get(path);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:getVolumeDetails 3PAR response is {}", responseString);
                VolumeDetailsCommandResult volResult = new Gson().fromJson(sanitize(responseString),
                        VolumeDetailsCommandResult.class);
                return volResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getVolumeDetails leave");
        } //end try/catch/finally
    }

    public void expandVolume(String name, Long additionalSize) throws Exception {
        _log.info("3PARDriver:expandVolume enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_EXPAND_VOLUME, name);
        String body = "{\"action\":3, \"sizeMiB\":" + additionalSize.toString() + "}";

        try {
            clientResp = put(path, body);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = getHeaderFieldValue(clientResp, "Location");
                _log.info("3PARDriver:createVolume 3PAR response is Location: {}", responseString);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:expandVolume leave");
        } //end try/catch/finally
    }

    public void deleteVolume(String name) throws Exception {
        _log.info("3PARDriver:deleteVolume enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_DELETE_VOLUME, name);

        try {
            clientResp = delete(path);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                _log.info("3PARDriver:deleteVolume success");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:deleteVolume leave");
        } //end try/catch/finally
    }


    private String getResponseDetails(ClientResponse clientResp) {
        String detailedResponse = null, ref=null;;
        try {
            JSONObject jObj = clientResp.getEntity(JSONObject.class);
            detailedResponse = String.format("3PAR error code: %s, Description: %s",
                    jObj.getString("code"), jObj.getString("desc"));
            if (jObj.has("ref")) {
                ref = String.format(", refer:%s", jObj.getString("ref"));
                detailedResponse = detailedResponse + ref;
            }
            _log.error(String.format("3PARDriver:HTTP error code: %d, Complete 3PAR error response: %s", clientResp.getStatus(),
                    jObj.toString()));
        } catch (Exception e) {
            _log.error("3PARDriver:Unable to get 3PAR error details");
            detailedResponse = String.format("%1$s", (clientResp == null) ? "" : clientResp);
        }
        return detailedResponse;
    }

    private String getHeaderFieldValue(ClientResponse clientResp, String field) {
        List<String> valueList = null;
        String value = null;
        try {
            MultivaluedMap<String, String> headers = clientResp.getHeaders();
            valueList = headers.get(field);
            value = valueList.get(0);
        } catch (Exception e) {
            _log.error("3PARDriver:Unable to get value for field in header: %s", field);
        }
        return value;
    }
    
    private String getResponseFieldValue(ClientResponse clientResp, String field) {
        String value = null;
        try {
            JSONObject jObj = clientResp.getEntity(JSONObject.class);
            value = jObj.getString(field);
            _log.debug("3PARDriver:getResponseFieldValue 3PAR response is : {}", jObj.toString());
        } catch (Exception e) {
            _log.error("3PARDriver:Unable to get field value in response: %s", field);
        }
        return value;
    }
    
    private ClientResponse get(final String uri) throws Exception {
        ClientResponse clientResp = _client.get_json(_baseUrl.resolve(uri), _authToken);
        if (clientResp.getStatus() == 403) {
            getAuthToken();
            clientResp = _client.get_json(_baseUrl.resolve(uri), _authToken);
        }
        return clientResp;
    }
    
    private ClientResponse post(final String uri, String body) throws Exception {
        ClientResponse clientResp = _client.post_json(_baseUrl.resolve(uri), _authToken, body);
        if (clientResp.getStatus() == 403) {
            getAuthToken();
            clientResp = _client.post_json(_baseUrl.resolve(uri), _authToken, body);
        }
        return clientResp;
    }
    
    private ClientResponse put(final String uri, String body) throws Exception {
        ClientResponse clientResp = _client.put_json(_baseUrl.resolve(uri), _authToken, body);
        if (clientResp.getStatus() == 403) {
            getAuthToken();
            clientResp = _client.put_json(_baseUrl.resolve(uri), _authToken, body);
        }
        return clientResp;
    }
    
    private ClientResponse delete(final String uri) throws Exception {
        ClientResponse clientResp = _client.delete_json(_baseUrl.resolve(uri), _authToken);
        if (clientResp.getStatus() == 403) {
            getAuthToken();
            clientResp = _client.delete_json(_baseUrl.resolve(uri), _authToken);
        }
        return clientResp;
    }
}

