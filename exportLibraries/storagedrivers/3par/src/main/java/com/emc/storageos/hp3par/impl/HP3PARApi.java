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
import com.emc.storageos.hp3par.command.ConsistencyGroupResult;
import com.emc.storageos.hp3par.command.ConsistencyGroupsListResult;
import com.emc.storageos.hp3par.command.HostCommandResult;
import com.emc.storageos.hp3par.command.PortCommandResult;
import com.emc.storageos.hp3par.command.PortStatisticsCommandResult;
import com.emc.storageos.hp3par.command.Privileges;
import com.emc.storageos.hp3par.command.SystemCommandResult;
import com.emc.storageos.hp3par.command.UserRoleCommandResult;
import com.emc.storageos.hp3par.command.VlunResult;
import com.emc.storageos.hp3par.command.VolumeDetailsCommandResult;
import com.emc.storageos.hp3par.connection.RESTClient;
import com.emc.storageos.hp3par.utils.CompleteError;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.google.gson.Gson;
import com.google.json.JsonSanitizer;
import com.sun.jersey.api.client.ClientResponse;
import com.emc.storageos.hp3par.command.VolumesCommandResult;

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

    // Discovery related
    private static final URI URI_LOGIN = URI.create("/api/v1/credentials");
    private static final String URI_SYSTEM = "/api/v1/system";
    private static final String URI_USER_ROLE = "/api/v1/users/{0}";
    private static final String URI_CPGS = "/api/v1/cpgs";
    private static final String URI_CPG_DETAILS = "/api/v1/cpgs/{0}";
    private static final String URI_PORTS = "/api/v1/ports";
    private static final String URI_PORT_STATISTICS = "/api/v1/systemreporter/attime/portstatistics/daily";

    // Base, snap and clone volume related
    private static final String URI_CREATE_VOLUME = "/api/v1/volumes";
    private static final String URI_VOLUME_DETAILS = "/api/v1/volumes/{0}";
    private static final String URI_EXPAND_VOLUME = "/api/v1/volumes/{0}";
    private static final String URI_DELETE_VOLUME = "/api/v1/volumes/{0}";
    private static final String URI_VOLUME_SNAPSHOT = "/api/v1/volumes/{0}";
    private static final String URI_DELETE_VOLUME_SNAPSHOT = "/api/v1/volumes/{0}";
    private static final String URI_RESTORE_VOLUME_SNAPSHOT = "/api/v1/volumes/{0}";
    private static final String URI_STORAGE_VOLUMES = "/api/v1/volumes";
    
    // CG related
    private static final String URI_CREATE_CG = "/api/v1/volumesets";
    private static final String URI_DELETE_CG = "/api/v1/volumesets/{0}";
    private static final String URI_SNAPSHOT_CG = "/api/v1/volumesets/{0}";
    private static final String URI_CLONE_CG = "/api/v1/volumesets/{0}";
    private static final String URI_UPDATE_CG = "/api/v1/volumesets/{0}";
    private static final String URI_CG_DETAILS = "/api/v1/volumesets/{0}";
    private static final String URI_CG_LIST_DETAILS = "/api/v1/volumesets";
    
    // Export related
    private static final String URI_CREATE_VLUN = "/api/v1/vluns";
    private static final String URI_HOSTS = "/api/v1/hosts";

    
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

    public void verifyUserRole(String name) throws Exception {
        _log.info("3PARDriver:verifyUserRole enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_USER_ROLE, name);

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
                _log.info("3PARDriver:getSystemDetails 3PAR response is {}", responseString);
                UserRoleCommandResult roleRes = new Gson().fromJson(sanitize(responseString),
                        UserRoleCommandResult.class);

                boolean superUser = false;
                for (int i = 0; i < roleRes.getPrivileges().size(); i++) {
                    Privileges currPriv = roleRes.getPrivileges().get(i);

                    if ( (currPriv.getDomain().compareToIgnoreCase("all") == 0) && 
                            (currPriv.getRole().compareToIgnoreCase("super") == 0)) {
                        superUser = true;
                    }
                }
                
                if (superUser == false) {
                    _log.error("3PARDriver:User does not have sufficient privilege to discover");
                    throw new HP3PARException("User does not have sufficient privilege");
                } else {
                    _log.info("3PARDriver:User is super user");
                }
                
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:verifyUserRole enter");
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

    public HostCommandResult getHostDetails() throws Exception {
        _log.info("3PARDriver:getHostDetails enter");
        ClientResponse clientResp = null;

        try {
            clientResp = get(URI_HOSTS);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:getHostDetails 3PAR response is {}", responseString);
                HostCommandResult hostResult = new Gson().fromJson(sanitize(responseString),
                        HostCommandResult.class);
                return hostResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getHostDetails leave");
        } //end try/catch/finally
    }    

    public void createVolume(String name, String cpg, Boolean thin, Long size) throws Exception {
        _log.info("3PARDriver:createVolume enter");
        ClientResponse clientResp = null;
        String body = "{\"name\":\"" + name + "\", \"cpg\":\"" + cpg + 
                "\", \"tpvv\":" + thin.toString() + ", \"sizeMiB\":" + size.toString() + ", \"snapCPG\":\"" + cpg + "\"}";
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

    public void createVirtualCopy(String baseVolumeName, String snapName, Boolean readOnly) throws Exception {
        _log.info("3PARDriver:createVolumeVirtualCopy enter");
        ClientResponse clientResp = null;
        
        // for snapshot creation 
        String payload = "{\"action\":\"createSnapshot\", \"parameters\": { \"name\": \"" + snapName +"\" , \"readOnly\": " + readOnly +"} }";

        final String path = MessageFormat.format(URI_VOLUME_SNAPSHOT, baseVolumeName);
        
        _log.info(" 3PARDriver:createVolumeVirtualCopy uri = {} payload {} ",path,payload);
        try {
            clientResp = post(path, payload);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
            	
            	_log.info("3PARDriver:createVolumeSnapshot success");
            	// below needed for multiple volume snashot creation
                /*
            	String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:getVolumeDetails 3PAR response is {}", responseString);
                VolumeDetailsCommandResult volResult = new Gson().fromJson(sanitize(responseString),
                        VolumeDetailsCommandResult.class);
                return volResult;
                */
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:createVolumeVirtualCopy leave");
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

    public VolumesCommandResult getStorageVolumes() throws Exception {
        _log.info("3PARDriver:getVolumeDetails enter");
        ClientResponse clientResp = null;
        final String path = URI_STORAGE_VOLUMES;
        
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
                VolumesCommandResult storageVolsResult = new Gson().fromJson(sanitize(responseString),
                        VolumesCommandResult.class);
                return storageVolsResult;
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

    public void deleteVirtualCopy(String name) throws Exception {
        _log.info("3PARDriver: deleteVolumeVirtualCOpy enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_DELETE_VOLUME_SNAPSHOT, name);

        try {
            clientResp = delete(path);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                _log.info("3PARDriver: deleteVolumeVirtualCOpy success");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver: deleteVolumeVirtualCOpy leave");
        } //end try/catch/finally
    }

	public void restoreVirtualCopy(String name) throws Exception {
        _log.info("3PARDriver: restoreVirtualCopy enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_RESTORE_VOLUME_SNAPSHOT, name);
        Boolean online = true;
        Boolean offline = false;
        
        // for snapshot restoration  {"action":4, "online":true}
        String onlinePayload = "{\"action\":4, \"online\": " + online +" }";
        
        // Offline restore is faster
        String offlinePayload = "{\"action\":4, \"online\": " + offline +" }";
        
        try {
        	// trying offline restore
            clientResp = put(path,offlinePayload);
            
            // if related volume is exported, we will get error here. Trying online restore
            if (clientResp.getStatus() != 200) {
            	String errResp = getResponseDetails(clientResp);
            	_log.error("3PARDriver: restoreVirtualCopy trying online restore option, offline restore failed with error " + errResp);
            	clientResp = put(path,onlinePayload);
            }
            
            if (clientResp == null) {
                _log.error("3PARDriver: restoreVirtualCopy There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                _log.info("3PARDriver: restoreVirtualCopy success");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver: restoreVirtualCopy leave");
        } //end try/catch/finally
    }

    public VlunResult createVlun(String volumeName, int hlu, String hostName, String portId) throws Exception {
        _log.info("3PARDriver:createVlun enter");
        ClientResponse clientResp = null;
        Integer lun = (hlu == -1) ? 0 : hlu;
        boolean autoLun = (lun == 0) ? true : false;

        String body = "{\"volumeName\":\"" + volumeName + "\", \"lun\":" + lun.toString() + ", " + 
                "\"hostname\":\"" + hostName + "\"" + ", \"autoLun\":" + autoLun + ", \"maxAutoLun\": 0";

        // port is specified for matched set; not for host set
        if (portId != null) {
            String[] pos = portId.split(":");
            String portPos = String.format(", \"portPos\":{\"node\":%s, \"slot\":%s, \"cardPort\":%s}", pos[0], pos[1], pos[2]);
            body = body.concat(portPos);
        }
        
        body = body.concat("}");

        try {
            clientResp = post(URI_CREATE_VLUN, body);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = getHeaderFieldValue(clientResp, "Location");
                _log.info("3PARDriver:createVolume 3PAR response is Location: {}", responseString);
                String[] resp = responseString.split(",");
                VlunResult result = new VlunResult();
                result.setAssignedLun(resp[1]);
                result.setStatus(true);
                return result;
            }
        } catch (Exception e) {
            _log.error(e.getMessage());
            return null;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:createVlun leave");
        } //end try/catch/finally
    }

    
    private CompleteError getCompleteResponseDetails(ClientResponse clientResp) {
        String detailedResponse = null, ref=null;
        CompleteError compError = null;
        
        try {
            JSONObject jObj = clientResp.getEntity(JSONObject.class);
            String errorCode = jObj.getString("code");
            
            detailedResponse = String.format("3PAR error code: %s, Description: %s",
                    errorCode, jObj.getString("desc"));
            if (jObj.has("ref")) {
                ref = String.format(", refer:%s", jObj.getString("ref"));
                detailedResponse = detailedResponse + ref;
            }
            
            int httpCode = clientResp.getStatus();
            _log.error(String.format("3PARDriver:HTTP error code: %d, Complete 3PAR error response: %s", httpCode,
                    jObj.toString()));
            
            compError = new CompleteError();
            compError.setHp3parCode(errorCode);
            compError.setHttpCode(httpCode);
            compError.setErrorResp(detailedResponse);
        } catch (Exception e) {
            _log.error("3PARDriver:Unable to get 3PAR error details");
            detailedResponse = String.format("%1$s", (clientResp == null) ? "" : clientResp);
        }
        return compError;
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
        field = "privileges";
        try {
            JSONObject jObj = clientResp.getEntity(JSONObject.class);
            _log.info("3PARDriver:getResponseFieldValue 3PAR response is : {}", jObj.toString());
            value = jObj.getString(field);
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

    /**
     * VV Set is a HP3PAR term for Consistency Group. 
     * This deals with creation of CG, volumes will be added to the CG in create volume
     *       
     * @param displayName
     * @throws Exception
     */
	public void createVVset(String displayName) throws Exception {

        _log.info("createVVset enter");
        ClientResponse clientResp = null;
        
        // for VV set creation 
        String payload = "{\"name\": \"" + displayName +"\" }";

        //final String path = MessageFormat.format(URI_CREATE_CG);
        _log.info(" 3PARDriver: createVVset uri = {} payload {} ",URI_CREATE_CG.toString(),payload);
        
        try {
        	
            clientResp = post(URI_CREATE_CG, payload);
            if (clientResp == null) {
                _log.error("3PARDriver: createVVset There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                _log.error("3PARDriver: createVVset There is error response from 3PAR = {}" , errResp);
                throw new HP3PARException(errResp);
            } else {
            	_log.info("3PARDriver: createVVset success");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver: createVVset leave");
        } //end try/catch/finally
    
		
	}

	/**
	 * Get Consistency Group details
	 * 
	 * @param displayName
	 * @return
	 * @throws Exception
	 */
	public ConsistencyGroupResult getVVsetDetails(String displayName) throws Exception {

        _log.info("3PARDriver: getVVsetDetails enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_CG_DETAILS, displayName);
        
        try {
            clientResp = get(path);
            if (clientResp == null) {
                _log.error("3PARDriver: getVVsetDetails There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                _log.error("3PARDriver: getVVsetDetails There is error response from 3PAR = {}" , errResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver: getVVsetDetails 3PAR response is {}", responseString);
                ConsistencyGroupResult cgResult = new Gson().fromJson(sanitize(responseString),
                		ConsistencyGroupResult.class);
                return cgResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver: getVVsetDetails leave");
        } //end try/catch/finally
    
	}

	/**
	 * delete a VV Set  or Consistency Group
	 * 
	 * @param nativeId
	 * @throws Exception
	 */
	public void deleteVVset(String nativeId) throws Exception {

        _log.info("3PARDriver: deleteVVset enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_DELETE_CG, nativeId);

        _log.info("3PARDriver:deleteVVset running delete VV Set " + path);
        
        try {
            clientResp = delete(path);
            if (clientResp == null) {
                _log.error("3PARDriver:deleteVVset There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                _log.info("3PARDriver: deleteVVset success");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:deleteVVset leave");
        } //end try/catch/finally
    
	}

	/**
	 * Add or remove volume from an existing consistency group or VV Set
	 *  
	 * @param volumeCGName
	 * @param volName
	 * @param actionValue
	 * @throws Exception
	 */
	public void updateVVset(String volumeCGName, String volName, int actionValue) throws Exception {

        _log.info("3PARDriver: updateVVset enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_UPDATE_CG, volumeCGName);

        // for VV set addition {"action":1,"setmembers":["vol-name","vol-name2"]} 
        String payload = "{\"action\": " + actionValue +", \"setmembers\": [ \"" + volName + "\" ] }";
        
        _log.info("3PARDriver:updateVVset running update VV Set with URI {} and payload {} ", path, payload);
        
        try {
            clientResp = put(path,payload);
            if (clientResp == null) {
                _log.error("3PARDriver:updateVVset There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                _log.info("3PARDriver: updateVVset success");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:updateVVset leave");
        } //end try/catch/finally

		
		
	}
	
	
	/**
	 * Get Consistency Groups List 
	 * 
	 * @param displayName
	 * @return
	 * @throws Exception
	 */
	public ConsistencyGroupsListResult getVVsetsList() throws Exception {

        _log.info("3PARDriver: getVVsetsList enter");
        ClientResponse clientResp = null;
        final String path = URI_CG_LIST_DETAILS;
        
        try {
            clientResp = get(path);
            if (clientResp == null) {
                _log.error("3PARDriver: getVVsetsList There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                _log.error("3PARDriver: getVVsetsList There is error response from 3PAR = {}" , errResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver: getVVsetsList 3PAR response is {}", responseString);
                ConsistencyGroupsListResult cgListResult = new Gson().fromJson(sanitize(responseString),
                		ConsistencyGroupsListResult.class);
                return cgListResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver: getVVsetsList leave");
        } //end try/catch/finally
    
	}
	

	public void createVVsetVirtualCopy(String nativeId, String snapshotName, Boolean readOnly) throws Exception {

	        _log.info("3PARDriver:createVVsetVirtualCopy enter");
	        _log.info(" 3PARDriver:createVVsetVirtualCopy CG name {} , CG snapshot name given {} ", nativeId,snapshotName);
	        ClientResponse clientResp = null;
	        
	        String cgSnapshotString = snapshotName + "@count@";
	        // for snapshot creation 
	        String payload = "{\"action\":\"createSnapshot\", \"parameters\": { \"name\": \"" + cgSnapshotString + "\" , \"readOnly\": " + readOnly +"} }";

	        final String path = MessageFormat.format(URI_SNAPSHOT_CG, nativeId);
	        
	        _log.info(" 3PARDriver:createVVsetVirtualCopy uri = {} payload {} ",path,payload);
	        try {
	            clientResp = post(path, payload);
	            if (clientResp == null) {
	                _log.error("3PARDriver:There is no response from 3PAR");
	                throw new HP3PARException("There is no response from 3PAR");
	            } else if (clientResp.getStatus() != 201) {
	                String errResp = getResponseDetails(clientResp);
	                throw new HP3PARException(errResp);
	            } else {
	            	
	            	_log.info("3PARDriver:createVVsetVirtualCopy success");
	            	
	            }
	        } catch (Exception e) {
	            throw e;
	        } finally {
	            if (clientResp != null) {
	                clientResp.close();
	            }
	            _log.info("3PARDriver:createVVsetVirtualCopy leave");
	        } //end try/catch/finally
	    }


}

