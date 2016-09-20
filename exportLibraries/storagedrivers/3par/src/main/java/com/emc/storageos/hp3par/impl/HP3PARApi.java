/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.codehaus.jettison.json.JSONObject;

import com.emc.storageos.hp3par.command.CPGCommandResult;
import com.emc.storageos.hp3par.command.CPGMember;
import com.emc.storageos.hp3par.command.ConsistencyGroupResult;
import com.emc.storageos.hp3par.command.ConsistencyGroupsListResult;
import com.emc.storageos.hp3par.command.FcPath;
import com.emc.storageos.hp3par.command.HostCommandResult;
import com.emc.storageos.hp3par.command.HostMember;
import com.emc.storageos.hp3par.command.HostSetDetailsCommandResult;
import com.emc.storageos.hp3par.command.ISCSIPath;
import com.emc.storageos.hp3par.command.PortCommandResult;
import com.emc.storageos.hp3par.command.PortStatisticsCommandResult;
import com.emc.storageos.hp3par.command.Privileges;
import com.emc.storageos.hp3par.command.SystemCommandResult;
import com.emc.storageos.hp3par.command.UserRoleCommandResult;
import com.emc.storageos.hp3par.command.VVSetCloneList;
import com.emc.storageos.hp3par.command.VVSetCloneList.VVSetVolumeClone;
import com.emc.storageos.hp3par.command.VirtualLunsList;
import com.emc.storageos.hp3par.command.VlunResult;
import com.emc.storageos.hp3par.command.VolumeDetailsCommandResult;
import com.emc.storageos.hp3par.connection.RESTClient;
import com.emc.storageos.hp3par.utils.CompleteError;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.emc.storageos.hp3par.utils.SanUtils;
import com.emc.storageos.storagedriver.model.VolumeClone;
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
    private static final String URI_PORT_STATISTICS_SPECIFIC = "/api/v1/systemreporter/attime/portstatistics/daily?query=%22portPos%20EQ%20{0}%22";

    // Base volume , snap and clone volume related
    private static final String URI_CREATE_VOLUME = "/api/v1/volumes";
    private static final String URI_VOLUME_DETAILS = "/api/v1/volumes/{0}";
    private static final String URI_EXPAND_VOLUME = "/api/v1/volumes/{0}";
    private static final String URI_DELETE_VOLUME = "/api/v1/volumes/{0}";
    private static final String URI_STORAGE_VOLUMES = "/api/v1/volumes";
    
    // snapshot / virtual copy
    private static final String URI_CREATE_VOLUME_SNAPSHOT = "/api/v1/volumes/{0}";
    private static final String URI_DELETE_VOLUME_SNAPSHOT = "/api/v1/volumes/{0}";
    private static final String URI_RESTORE_VOLUME_SNAPSHOT = "/api/v1/volumes/{0}";
    
    // clone / physical copy
    private static final String URI_CREATE_VOLUME_CLONE = "/api/v1/volumes/{0}";
    private static final String URI_DELETE_VOLUME_CLONE = "/api/v1/volumes/{0}";
    private static final String URI_RESTORE_VOLUME_CLONE = "/api/v1/volumes/{0}";
    
    // CG related
    private static final String URI_CREATE_CG = "/api/v1/volumesets";
    private static final String URI_DELETE_CG = "/api/v1/volumesets/{0}";
    private static final String URI_SNAPSHOT_CG = "/api/v1/volumesets/{0}";
    private static final String URI_CLONE_CG = "/api/v1/volumesets/{0}";
    private static final String URI_UPDATE_CG = "/api/v1/volumesets/{0}";
    private static final String URI_CG_DETAILS = "/api/v1/volumesets/{0}";
    private static final String URI_CG_LIST_DETAILS = "/api/v1/volumesets";
    
    // For ingestion
	private static final String URI_VLUNS_OF_VOLUME = "/api/v1/vluns?query=%22volumeWWN=={0}%22";
	private static final String URI_VLUNS_BY_VOlUME_NAME = "/api/v1/vluns?query=%22volumeName=={0}%22";
	private static final String URI_SNAPSHOTS_OF_VOLUME = "/api/v1/volumes?query=%22copyOf=={0}%22";
    
    // For export
    private static final String URI_CREATE_VLUN = "/api/v1/vluns";
    private static final String URI_HOSTS = "/api/v1/hosts";
    private static final String URI_HOSTSETS = "/api/v1/hostsets";
    private static final String URI_HOSTSET_DETAILS = "/api/v1/hostsets/{0}";
    private static final String URI_HOST_DETAILS = "/api/v1/hosts/{0}";
    private static final String URI_VLUNS = "/api/v1/vluns";
    private static final String URI_DELETE_VLUN = "/api/v1/vluns/{0},{1},{2}";
    
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
        _log.info("3PARDriver: verifyUserRole path is {}", path);

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
                for (Privileges currPriv:roleRes.getPrivileges()) {

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
            _log.info("3PARDriver:verifyUserRole leave");
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
        _log.info("3PARDriver: getCPGDetails path is {}", path);

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
            _log.info("3PARDriver:getCPGDetails leave");
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
    
    public PortStatisticsCommandResult getPortStatistics(String portId) throws Exception {

    	_log.info("3PARDriver: getPortStatistics enter");
    	ClientResponse clientResp = null;
    	final String path = MessageFormat.format(URI_PORT_STATISTICS_SPECIFIC, portId);
    	PortStatisticsCommandResult portStatResult = null;
    	_log.info("3PARDriver: getPortStatistics path is {}", path);

    	try {
    		clientResp = get(path);
    		if (clientResp == null) {
    			_log.error("3PARDriver: getPortStatistics There is no response from 3PAR");
    			throw new HP3PARException("There is no response from 3PAR");
    		} else if (clientResp.getStatus() != 200) {
    			String errResp = getResponseDetails(clientResp);
    			_log.error("3PARDriver: getPortStatistics There is error response from 3PAR = {}" , errResp);
    			throw new HP3PARException(errResp);
    		} else {
    			String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriveri:getPortStatistics 3PAR response is {}", responseString);
                portStatResult = new Gson().fromJson(sanitize(responseString),
                        PortStatisticsCommandResult.class);
                
    		}
    	} catch (Exception e) {
    		throw e;
    	} finally {
    		if (clientResp != null) {
    			clientResp.close();
    		}
    		_log.info("3PARDriver: getPortStatistics leave");
    	} //end try/catch/finally
    	
    	return portStatResult;
    }

    public HostCommandResult getAllHostDetails() throws Exception {
        _log.info("3PARDriver:getAllHostDetails enter");
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
                _log.info("3PARDriver:getAllHostDetails 3PAR response is {}", responseString);
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
            _log.info("3PARDriver:getAllHostDetails leave");
        } //end try/catch/finally
    }    

    public void createVolume(String name, String cpg, Boolean thin, Boolean dedup, Long size) throws Exception {
        _log.info("3PARDriver:createVolume enter");
        ClientResponse clientResp = null;
        String body = "{\"name\":\"" + name + "\", \"cpg\":\"" + cpg + 
                "\", \"tpvv\":" + thin.toString() + ", \"tdvv\":" + dedup.toString() + ", \"sizeMiB\":" + size.toString() + ", \"snapCPG\":\"" + cpg + "\"}";
        _log.info("3PARDriver: createVolume body is {}", body);
        
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

        final String path = MessageFormat.format(URI_CREATE_VOLUME_SNAPSHOT, baseVolumeName);
        
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

    /**
     * 
     * Vipr UI doesn't provide offline (Attached volume) / online options while creating clone, so
	 * below logic will be followed: 
	 * First, to check destination volume exists we execute create clone creation as offline volume.
	 * if error, destination clone volume doesn't exist, so create a new volume with clone name by
	 * using its base volume parameters like TPVV,CPG. Then create a offline
	 * volume clone using newly created volume clone 
	 * Note: A offline clone creates a attached clone, which actually creates 
	 * a intermediate snapshot which can be utilized for restore/update.
	 * We are not using online option, as it creates detached volume with no way to restore/update.
	 * 
     */
    public void createPhysicalCopy(String baseVolumeName, String cloneName, String cloneCPG) throws Exception {
		_log.info("3PARDriver: createPhysicalCopy enter");

        String baseVolumeSnapCPG = cloneCPG;
        String baseVolumeUserCPG = cloneCPG;
        
		ClientResponse clientResp = null;
		String payload = null;
		String secondPayload = null;

		// clone creation, check if destination volume exists as expected by this API
		payload = "{\"action\":\"createPhysicalCopy\", \"parameters\": { \"destVolume\": \"" + cloneName
				+ "\" , \"saveSnapshot\": " + true + "} }";
		
		final String path = MessageFormat.format(URI_CREATE_VOLUME_CLONE, baseVolumeName);

		_log.info(" 3PARDriver: createPhysicalCopy uri = {} payload {} secondPayload {}", path, payload, secondPayload);

		try {
			// create clone considering destination volume already created

			clientResp = post(path, payload);

			if (clientResp == null || clientResp.getStatus() != 201) {
				if (clientResp != null) {
				String errResp = getResponseDetails(clientResp);
				_log.info(" 3PARDriver: createPhysicalCopy destination clone volume absent, hence creating new volume for clone. Error Info : {}",errResp);
				}

				VolumeDetailsCommandResult volResult = null;
                
                try {
                volResult = getVolumeDetails(baseVolumeName);
                } catch (Exception e) {
                	_log.info("3PARDriver: createVolumeClone the specified volume {} for clone creation not found, its parent {}; continue with clone creation: {}.\n",
                            baseVolumeName, cloneName, e.getMessage());
                }
                
                if (volResult != null) {
                	// UserCPG will be absent for clones, hence using snapCPG here. We might need to re-look CPG selection later
                	baseVolumeUserCPG = volResult.getUserCPG();
                	baseVolumeSnapCPG = volResult.getSnapCPG();
                	Boolean tpvv = true;
                    Boolean tdvv = false;

                	if (volResult.getProvisioningType() == 6) {
                		tdvv = true;
                		tpvv = false;
                	}
                	
                	_log.info("3PARDriver: createVolumeClone base volume exists, id {}, baseVolumeSnapCPG {} , baseVolumeUserCPG {} , copyOf {}, copyType {} , name {}, volume type {} - ",
                			baseVolumeName, baseVolumeSnapCPG, baseVolumeUserCPG,volResult.getCopyOf(), volResult.getCopyType(), volResult.getName(), volResult.getProvisioningType());
                
                createVolume(cloneName, baseVolumeSnapCPG, tpvv, tdvv, volResult.getSizeMiB());
                // sleep for some milliseconds required ?
                
                try {
                    volResult = getVolumeDetails(baseVolumeName);
                    } catch (Exception e) {
                    	_log.info("3PARDriver: createVolumeClone the specified clone volume {} not created successfully yet. error {}",
                    			cloneName, e.getMessage());
                    }
                	
				if (volResult != null) {
					clientResp = post(path, payload);
				} else {
                	_log.info("3PARDriver: createVolumeClone unable to find the newly created volume, volResult is null");
                }
                } else {
                	_log.info("3PARDriver: createVolumeClone base volume not found, volResult is null");
                }
			}

			if (clientResp == null) {
				_log.error("3PARDriver:There is no response from 3PAR");
				throw new HP3PARException("There is no response from 3PAR");
			} else if (clientResp.getStatus() != 201) {
				String errResp = getResponseDetails(clientResp);
				_log.info("3PARDriver: createPhysicalCopy error resopnse : {} ", errResp);
				throw new HP3PARException(errResp);
			} else {
				_log.info("3PARDriver: createPhysicalCopy success");
			}
		} catch (Exception e) {
			throw e;
		} finally {
			if (clientResp != null) {
				clientResp.close();
			}
			_log.info("3PARDriver: createPhysicalCopy leave");
		} // end try/catch/finally
	}

    public VolumeDetailsCommandResult getVolumeDetails(String name) throws Exception {
        _log.info("3PARDriver:getVolumeDetails enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_VOLUME_DETAILS, name);
        _log.info("3PARDriver: getVolumeDetails path is {}", path);
        
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
        _log.info("3PARDriver: expandVolume path is {}, body is {}", path, body);

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
        _log.info("3PARDriver: deleteVolume path is {}", path);

        try {
            clientResp = delete(path);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                _log.info("3PARDriver:deleteVolume success ", name);
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
    
    public void deletePhysicalCopy(String name) throws Exception {
        _log.info("3PARDriver: deletePhysicalCopy enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_DELETE_VOLUME_CLONE, name);

        try {
            clientResp = delete(path);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                _log.info("3PARDriver: deletePhysicalCopy success");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver: deletePhysicalCopy leave");
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

	public void restorePhysicalCopy(String name) throws Exception {
        _log.info("3PARDriver: restorePhysicalCopy enter");
        ClientResponse clientResp = null;
        Boolean offline = false;
        String intermediateSnapshot = name;
        
        // Offline restore is performed on intermediate snapshot
        String offlinePayload = "{\"action\":4, \"online\": " + offline +" }";
        
        try {
        	
        	VolumeDetailsCommandResult volResult = null;
            
            volResult = getVolumeDetails(name);
            
            if (volResult != null && volResult.getProvisioningType() != 3) {
            	// get intermediate snapshot of clone/physical copy 
            	intermediateSnapshot = volResult.getCopyOf();
            }
            final String  path = MessageFormat.format(URI_RESTORE_VOLUME_CLONE, intermediateSnapshot);
        	
        	// trying offline restore
            clientResp = put(path,offlinePayload);
            
            if (clientResp == null) {
                _log.error("3PARDriver: restorePhysicalCopy There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                _log.info("3PARDriver: restorePhysicalCopy error {} ", errResp);
                throw new HP3PARException(errResp);
            } else {
                _log.info("3PARDriver: restorePhysicalCopy success");
            }
        } catch (Exception e) {
        	_log.info("3PARDriver: restorePhysicalCopy exception info {} ",e.getMessage());
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver: restorePhysicalCopy leave");
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
        // only matched-set export is supported
        if (portId != null) {
            String[] pos = portId.split(":");
            String portPos = String.format(", \"portPos\":{\"node\":%s, \"slot\":%s, \"cardPort\":%s}", pos[0], pos[1], pos[2]);
            body = body.concat(portPos);
        }
        
        body = body.concat("}");
        _log.info("3PARDriver: createVlun body is {}", body);

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
                _log.info("3PARDriver:createVlun 3PAR response is Location: {}", responseString);
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

    public HostSetDetailsCommandResult getHostSetDetails(String name) throws Exception {
        _log.info("3PARDriver:getHostSetDetails enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_HOSTSET_DETAILS, name);
        _log.info("3PARDriver: getHostSetDetails path is {}", path);
        
        try {
            clientResp = get(path);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                if (clientResp.getStatus() == 404 && errResp.contains("code: 102")) {
                    return null; //Host set does not exists
                } else {                        
                    throw new HP3PARException(errResp);
                }
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:getHostSetDetails 3PAR response is {}", responseString);
                HostSetDetailsCommandResult hostsetResult = new Gson().fromJson(sanitize(responseString),
                        HostSetDetailsCommandResult.class);
                return hostsetResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getHostSetDetails leave");
        } //end try/catch/finally
    }

    // Request is for creating the cluster with only one host
    public void createHostSet(String clustName, String hostName) throws Exception {
        _log.info("3PARDriver:createHostSet enter");
        ClientResponse clientResp = null;
        String body = "{\"name\": \"" + clustName + "\", \"setmembers\": [\"" + hostName + "\"]}";
        _log.info("3PARDriver: createHostSet body is {}", body);
        
        try {
            clientResp = post(URI_HOSTSETS, body);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = getHeaderFieldValue(clientResp, "Location");
                _log.info("3PARDriver:createHostSet 3PAR response is {}", responseString);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:createHostSet leave");
        } //end try/catch/finally
    }

    public HostSetDetailsCommandResult updateHostSet(String clustName, String hostName) throws Exception {
        _log.info("3PARDriver:updateHostSet enter");
        ClientResponse clientResp = null;
        String body = "{\"action\": 1, \"setmembers\": [\"" + hostName + "\"]}";
        final String path = MessageFormat.format(URI_HOSTSET_DETAILS, clustName);
        _log.info("3PARDriver: updateHostSet path is {}, body is {}", path, body);
        
        try {
            clientResp = put(path, body);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:updateHostSet 3PAR response is {}", responseString);
                HostSetDetailsCommandResult hostsetResult = new Gson().fromJson(sanitize(responseString),
                        HostSetDetailsCommandResult.class);
                return hostsetResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:updateHostSet leave");
        } //end try/catch/finally
    }
        
    public HostMember getHostDetails(String name) throws Exception {
        _log.info("3PARDriver:getHostDetails enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_HOST_DETAILS, name);
        _log.info("3PARDriver: getHostDetails path is {}", path);
        
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
                _log.info("3PARDriver:getHostDetails 3PAR response is {}", responseString);
                HostMember hostResult = new Gson().fromJson(sanitize(responseString),
                        HostMember.class);
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

    public VirtualLunsList getAllVlunDetails() throws Exception {
        _log.info("3PARDriver:getAllVlunDetails enter");
        ClientResponse clientResp = null;

        try {
            clientResp = get(URI_VLUNS);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver:getAllVlunDetails 3PAR response is {}", responseString);
                VirtualLunsList vlunResult = new Gson().fromJson(sanitize(responseString),
                        VirtualLunsList.class);
                return vlunResult;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:getAllVlunDetails leave");
        } //end try/catch/finally
    }    

    public void deleteVlun(String volName, String lun, String hostName, String pos) throws Exception {
        _log.info("3PARDriver:deleteVlun enter");
        ClientResponse clientResp = null;
        String path = MessageFormat.format(URI_DELETE_VLUN, volName, lun, hostName);
        if (pos != null) {
            path = path.concat(","+pos);
        }

        _log.info("3PARDriver: deleteVlun path is {}", path);
        try {
            clientResp = delete(path);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                _log.info("3PARDriver:deleteVlun success " + volName);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:deleteVlun leave");
        } //end try/catch/finally
    }

    public void createHost(String name, ArrayList<String> portIds, Integer persona) throws Exception {
        _log.info("3PARDriver:createHost enter");
        ClientResponse clientResp = null;
        String portIdstr = "[";
        String body = null;
        
        for (String Id:portIds) {
            if (portIdstr.length() > 1 ) {
                portIdstr = portIdstr.concat(",");
            }
            portIdstr = portIdstr.concat("\"" + Id + "\"");
        }
        portIdstr = portIdstr.concat("]");
        
        if (portIds.get(0).startsWith("iqn") == false) {
            body = "{\"name\":\"" + name + "\", \"FCWWNs\":" + portIdstr + 
                    ", \"persona\":" + persona.toString() + "}";
        } else {
            body = "{\"name\":\"" + name + "\", \"iSCSINames\":" + portIdstr + 
                    ", \"persona\":" + persona.toString() + "}";            
        }

        _log.info("3PARDriver: createHost body is {}", body);
        try {
            clientResp = post(URI_HOSTS, body);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                
                if (clientResp.getStatus() == 409 && errResp.contains("code: 16") == true) {
                    // host exists; modify host by adding new initiators
                    updateHost(name, portIds);
                } else {
                    throw new HP3PARException(errResp);
                }
            } else {
                String responseString = getHeaderFieldValue(clientResp, "Location");
                _log.info("3PARDriver:createHost 3PAR response is Location: {}", responseString);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:createHost leave");
        } //end try/catch/finally
    }
    
    public void updateHost(String name, ArrayList<String> portIdsNew) throws Exception {
        _log.info("3PARDriver:updateHost enter");
        ClientResponse clientResp = null;
        String portIdstr = "[";
        String body = null;
        final String path = MessageFormat.format(URI_HOST_DETAILS, name);
        ArrayList<String> portIdsNewFiltered = new ArrayList<>();
        ArrayList<String> existingFc = new ArrayList<>();
        
        try {
            // get existing host wwn/iqn;
            HostMember hostMemb = getHostDetails(name);

            if (portIdsNew.get(0).startsWith("iqn") == false) {
                for (FcPath fcPath:hostMemb.getFCPaths()) {
                    existingFc.add(SanUtils.cleanWWN(fcPath.getWwn()));
                }

                // remove existing wwn/iqns in the list to be added
                for (String portId:portIdsNew) {
                    if (existingFc.contains(SanUtils.cleanWWN(portId)) == false) {
                        portIdsNewFiltered.add(SanUtils.cleanWWN(portId));
                    }
                }
            } else {
                for (ISCSIPath scPath:hostMemb.getiSCSIPaths()) {
                    existingFc.add(scPath.getName());
                }

                // remove existing wwn/iqns in the list to be added
                for (String portId:portIdsNew) {
                    if (existingFc.contains(portId) == false) {
                        portIdsNewFiltered.add(portId);
                    }
                }                
            }
            
            for (String Id:portIdsNewFiltered) {
                if (portIdstr.length() > 1 ) {
                    portIdstr = portIdstr.concat(",");
                }
                portIdstr = portIdstr.concat("\"" + Id + "\"");
            }
            portIdstr = portIdstr.concat("]");

            if (portIdsNewFiltered.get(0).startsWith("iqn") == false) {
                body = "{\"FCWWNs\":" + portIdstr + ", \"pathOperation\":1}";
            } else {
                body = "{\"iSCSINames\":" + portIdstr + ", \"pathOperation\":1\"}";
            }

            _log.info("3PARDriver: updateHost path is {}, body is {}", path, body);
            clientResp = put(path, body);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = getHeaderFieldValue(clientResp, "Location");
                _log.info("3PARDriver:updateHost 3PAR response is Location: {}", responseString);
            }

        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver:updateHost leave");
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
	
	
	/**
	 * Get all vluns, which are associated with a volume, snapshot or a clone. 
	 * 
	 * @param displayName
	 * @return
	 * @throws Exception
	 */
	public VirtualLunsList getVLunsOfVolume(String volumeWWN) throws Exception {

        _log.info("3PARDriver: getVLunsOfVolume enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_VLUNS_OF_VOLUME, volumeWWN);
        _log.info("getVLunsOfVolume path is {}", path);
        
        try {
            clientResp = get(path);
            if (clientResp == null) {
                _log.error("3PARDriver: getVLunsOfVolume There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                _log.error("3PARDriver: getVLunsOfVolume There is error response from 3PAR = {}" , errResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver: getVLunsOfVolume 3PAR response is {}", responseString);
                VirtualLunsList vlunsListResult = new Gson().fromJson(sanitize(responseString),
                		VirtualLunsList.class);
                return vlunsListResult;
            }
        } catch (Exception e) {
	    _log.info("getVLunsOfVolume exception is {}", e.getMessage());
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver: getVLunsOfVolume leave");
        } //end try/catch/finally
    
	}


	/**
	 * Get all vluns, which are associated with a volume, snapshot or a clone. 
	 * 
	 * @param displayName
	 * @return
	 * @throws Exception
	 */
	public VirtualLunsList getVLunsByVolumeName(String volumeName) throws Exception {

        _log.info("3PARDriver: getVLunsOfVolume enter");
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_VLUNS_BY_VOlUME_NAME, volumeName);
        _log.info("getVLunsOfVolume path is {}", path);
        
        try {
            clientResp = get(path);
            if (clientResp == null) {
                _log.error("3PARDriver: getVLunsOfVolume There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                _log.error("3PARDriver: getVLunsOfVolume There is error response from 3PAR = {}" , errResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("3PARDriver: getVLunsOfVolume 3PAR response is {}", responseString);
                VirtualLunsList vlunsListResult = new Gson().fromJson(sanitize(responseString),
                		VirtualLunsList.class);
                return vlunsListResult;
            }
        } catch (Exception e) {
	    _log.info("getVLunsOfVolume exception is {}", e.getMessage());
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("3PARDriver: getVLunsOfVolume leave");
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

	public VVSetVolumeClone[] createVVsetPhysicalCopy(String nativeId, String vVsetNameForClone, List<VolumeClone> clones, Boolean saveSnapshot) throws Exception {

		_log.info("3PARDriver:createVVsetPhysicalCopy enter");
        _log.info(" 3PARDriver:createVVsetPhysicalCopy CG name {} for cloning , corresponding CG clone name {} ", nativeId,vVsetNameForClone);
        ClientResponse clientResp = null;
        String vvSetClones = "";
        // for snapshot creation 
        String payload = "{\"action\":\"createPhysicalCopy\", \"parameters\": { \"destVolume\": \"" + vVsetNameForClone + "\" , \"saveSnapshot\": " + saveSnapshot +"} }";

        final String path = MessageFormat.format(URI_CLONE_CG, nativeId);
        
        _log.info(" 3PARDriver: createVVsetPhysicalCopy uri = {} payload {} ",path,payload);
        try {
        	
			// get Vipr generated clone name and create corresponding volumes
		   	for (VolumeClone clone : clones) {
	            
	                _log.info("3PARDriver: createVVsetPhysicalCopy generated clone native id {}, display name {} - start",
	                		clone.getParentId(), clone.getDisplayName());  
	            
	                String generatedCloneName = clone.getDisplayName();
	                String baseVolumeName = clone.getParentId();
	                // create new volume , CG clone will fail if already exists
	                VolumeDetailsCommandResult volResult = null;
	                
	                volResult = getVolumeDetails(baseVolumeName);
	                
	                if (volResult != null) {
	                	// UserCPG will be absent for clones, hence using snapCPG here. We might need to re-look CPG selection later
	                	String baseVolumeUserCPG = volResult.getUserCPG();
	                	String baseVolumeSnapCPG = volResult.getSnapCPG();
	                	Boolean tpvv = true;
	                	Boolean tdvv = false;
	                	if (volResult.getProvisioningType() == 6) {
	                		tdvv = true;
	                		tpvv = false;
	                	}
	                	_log.info("3PARDriver: createVolumeClone base volume exists, id {}, baseVolumeSnapCPG {} , baseVolumeUserCPG {} , copyOf {}, copyType {} , name {}, volume type {} - ",
	                			baseVolumeName, baseVolumeSnapCPG, baseVolumeUserCPG,volResult.getCopyOf(), volResult.getCopyType(), volResult.getName(), volResult.getProvisioningType());
	                
	                createVolume(generatedCloneName, baseVolumeSnapCPG, tpvv,tdvv, volResult.getSizeMiB());
	                vvSetClones = vvSetClones+"\""+generatedCloneName+"\",";
	                
	                }
		   	}
		   	
		   	if (vvSetClones != "") {
		   		vvSetClones = vvSetClones.substring(0,vvSetClones.lastIndexOf(","));

	        // for VV set addition {"action":1,"setmembers":["vol-name","vol-name2"]} 
	        String vvsetPayload = "{\"name\": \"" + vVsetNameForClone +"\", \"setmembers\": [ \"" + vvSetClones + "\" ] }";
	        
	      //final String path = MessageFormat.format(URI_CREATE_CG);
	        _log.info(" 3PARDriver: createVVsetPhysicalCopy uri = {} vvsetPayload {} ",URI_CREATE_CG.toString(),vvsetPayload);
	        
	        // Create and update Clone CG object and volumes
	        try {
	            clientResp = post(URI_CREATE_CG, vvsetPayload);
	            if (clientResp == null) {
	                _log.error("3PARDriver: createVVsetPhysicalCopy There is no response from 3PAR");
	                throw new HP3PARException("There is no response from 3PAR");
	            } else if (clientResp.getStatus() != 201) {
	                String errResp = getResponseDetails(clientResp);
	                _log.error("3PARDriver: createVVsetPhysicalCopy There is error response from 3PAR = {}" , errResp);
	                throw new HP3PARException(errResp);
	            } else {
	            	_log.info("3PARDriver: createVVsetPhysicalCopy vvset created");
	            }
	        } catch (Exception e) {
	            throw e;
	        } finally {
	            if (clientResp != null) {
	                clientResp.close();
	            }
	            _log.info("3PARDriver: createVVsetPhysicalCopy execute vvset");
	        } //end try/catch/finally
	    
		// Executing CG clone 	
	        try {
            clientResp = post(path, payload);
            if (clientResp == null) {
                _log.error("3PARDriver:There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
            	String responseString = clientResp.getEntity(String.class);
            	//String customerResponseString = "{\"Altered\":"+responseString+"}";
            	_log.info("3PARDriver:createVVsetVirtualCopy success , response ",responseString);
            	
            	VVSetVolumeClone[] output =  new Gson().fromJson(sanitize(responseString),
            									VVSetVolumeClone[].class);
                return output;
            	
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
	}  catch (Exception e) {
		_log.info("3PARDriver:createVVsetVirtualCopy ERROR ");
        throw e;
    }
		return null;

	}
}

