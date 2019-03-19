/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.isilon.restapi.IsilonOneFS8Event.Events;
import com.emc.storageos.services.util.SecurityUtils;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/*
 * Isilon REST Api
 *
 * NOTE: We call close() on every IsilonAPI response to make sure that http connections are released in all cases.
 * Jersey releases http connections automatically in two cases: if response does not have entity and when entity was read from response.
 * In case when response has entity and entity was not read , Jersey does not release connection
 * (see JavaDoc for Jersey's ApacheHttpClientHandler.java) and connection is not returned back to connection pool to be available for
 * new request. We do call close on response every time (sometimes unnecessary) to make sure we do not miss any case.
 */
public class IsilonApi {
    private final URI _baseUrl;

    private final RESTClient _client;

    private static final int directorySoftQuotaDefaultSize = 95;

    private static final long directorySoftQuotaDefaultGracePeriod = 7L;

    private static final int directoryAdvisoryQuotaSize = 85;

    private static final URI URI_IFS = URI.create("/namespace/");
    private static final URI URI_ALIAS = URI.create("/platform/1/protocols/nfs/aliases/");
    private static final URI URI_NFS_EXPORTS = URI.create("/platform/1/protocols/nfs/exports/");
    private static final URI URI_SMB_SHARES = URI.create("/platform/1/protocols/smb/shares/");
    private static final URI URI_SNAPSHOTS = URI.create("/platform/1/snapshot/snapshots/");
    private static final URI URI_QUOTAS = URI.create("/platform/1/quota/quotas/");
    private static final URI URI_CLUSTER = URI.create("/platform/1/cluster/identity");
    private static final URI URI_CLUSTER_CONFIG = URI.create("/platform/1/cluster/config");
    private static final URI URI_STATS = URI.create("/platform/1/statistics/");
    private static final URI URI_STORAGE_POOLS = URI.create("/platform/1/storagepool/storagepools");
    private static final URI URI_DISK_POOLS = URI.create("/platform/1/diskpool/diskpools");
    private static final URI URI_ARRAY_GLOBAL_STATUS = URI.create("/platform/1/protocols/nfs/settings/global");
    private static final URI URI_ARRAY_GLOBAL_STATUS_ONEFS8 = URI.create("/platform/3/protocols/nfs/settings/global");
    private static final URI URI_STORAGE_PORTS = URI
            .create("/platform/1/cluster/smartconnect_zones");
    // private static final URI URI_EVENTS = URI.create("/platform/1/events/");
    private static final URI URI_EVENTS = URI.create("/platform/2/event/events/");
    private static final URI URI_ONEFS8_EVENTS = URI.create("/platform/3/event/eventlists/");

    private static final URI URI_AUTH_USERS = URI.create("/platform/1/auth/users");
    private static final URI URI_AUTH_GROUPS = URI.create("/platform/1/auth/groups");

    private static final URI URI_ACCESS_ZONES = URI.create("/platform/1/zones");
    private static final URI URI_NETWORK_POOLS = URI.create("/platform/3/network/pools");
    private static final URI URI_SYNCIQ_SERVICE_STATUS = URI.create("/platform/1/sync/settings");
    private static final URI URI_REPLICATION_LICENSE_INFO = URI.create("/platform/1/sync/license");
    private static final URI URI_REPLICATION_POLICIES = URI.create("/platform/1/sync/policies/");
    private static final URI URI_REPLICATION_POLICIES_8 = URI.create("/platform/3/sync/policies/");
    private static final URI URI_REPLICATION_JOBS = URI.create("/platform/1/sync/jobs");
    private static final URI URI_REPLICATION_JOB = URI.create("/platform/1/sync/jobs/");
    private static final URI URI_TARGET_REPLICATION_POLICIES = URI.create("platform/1/sync/target/policies/");
    private static final URI URI_REPLICATION_POLICY_REPORTS = URI.create("/platform/1/sync/reports?policy_name=");
    private static final URI URI_TARGET_REPLICATION_POLICY_REPORTS = URI.create("/platform/1/sync/target/reports?policy_name=");
    private static final URI URI_SNAPSHOTIQ_LICENSE_INFO = URI.create("/platform/1/snapshot/license");
    private static final URI URI_SNAPSHOT_SCHEDULES = URI.create("/platform/1/snapshot/schedules/");
    private static final URI URI_SESSION = URI.create("/session/1/session");

    private static final int sessionTimeoutthreshold = 600;

    private static Logger sLogger = LoggerFactory.getLogger(IsilonApi.class);

    private static final URI URI_SMARTQUOTA_LICENSE_INFO = URI.create("/platform/1/quota/license");

    public enum IsilonLicenseType {
        SMARTQUOTA, SNAPSHOT
    }

    private static final Map<IsilonLicenseType, URI> licenseMap;

    static {
        Map<IsilonLicenseType, URI> result = new HashMap<IsilonLicenseType, URI>();
        result.put(IsilonLicenseType.SMARTQUOTA, URI_SMARTQUOTA_LICENSE_INFO);
        result.put(IsilonLicenseType.SMARTQUOTA, URI_SNAPSHOTIQ_LICENSE_INFO);
        licenseMap = Collections.unmodifiableMap(result);
    }

    /**
     * Class representing Isilon list API return value
     * 
     * @param <T>
     *            type of object in the list
     */
    public static class IsilonList<T> {
        // list of objects returned
        private final ArrayList<T> _list;
        // resume token for longer lists
        private String _token;

        public IsilonList() {
            _list = new ArrayList<T>();
            _token = null;
        }

        public void add(T obj) {
            _list.add(obj);
        }

        public void addList(List<T> objList) {
            _list.addAll(objList);
        }

        public int size() {
            return _list.size();
        }

        public ArrayList<T> getList() {
            return _list;
        }

        public void setToken(String token) {
            _token = token;
        }

        public String getToken() {
            return _token;
        }
    }

    public IsilonApi(URI endpoint, RESTClient client) {
        _baseUrl = endpoint;
        _client = client;
    }

    /**
     * Close client resources
     */
    public void close() {
        _client.close();
    }

    /**
     * Validates the session and checks absolute timeout is below the threshold
     * 
     * @param isilonApi
     * @return
     */
    public boolean validateSession() {
    	if (IsilonApiConstants.AuthType.CSRF.name().equals(_client.get_authType())) {
    		ClientResponse clientResp = _client.get(_baseUrl.resolve(IsilonApiConstants.URI_SESSION));
    		try {
		        if (clientResp.getStatus() == 401) {				//Indicates the session has expired due to inactivity
		        	sLogger.info("Isilon Rest client session is invalid for the management server {}", _baseUrl);
		        	return false;
		        } else if (clientResp.getStatus() == 200) {			//Indicates the session is still valid
		        	String strObj = clientResp.getEntity(String.class);
		    	    JsonParser parser = new JsonParser();
		    	    JsonElement parsed = parser.parse(strObj);
		    	    JsonObject asJsonObject = parsed.getAsJsonObject();
		    	    int sessionTimeout = asJsonObject.get("timeout_absolute").getAsInt();
		    	    if (sessionTimeout <= sessionTimeoutthreshold) {	//Check if session timeout <= threshold which is 10 minutes
		    	    	sLogger.info("Isilon Rest client session is about to expire in {} minutes for the management server {}", 
		    	    			sessionTimeout/60, _baseUrl);
		    	    	return false;
		    	    }
		        }
            } catch (Exception e) {
            	sLogger.error("Unable to validate the Isilon session for the management server {}", _baseUrl);
                return false;
            } finally {
                if (clientResp != null) {
                    clientResp.close();
                }
            }
    	}
		return true;
	}

    /**
     * Get cluster info from the isilon array
     * 
     * @return IsilonClusterInfo object
     * @throws IsilonException
     */
    public IsilonClusterInfo getClusterInfo() throws IsilonException {
        ClientResponse clientResp = null;
        clientResp = _client.get(_baseUrl.resolve(URI_CLUSTER));
        if (clientResp.getStatus() != 200) {
            throw IsilonException.exceptions.unableToConnect(_baseUrl);
        }

        try {
            JSONObject resp = clientResp.getEntity(JSONObject.class);
            IsilonClusterInfo info = new Gson().fromJson(SecurityUtils.sanitizeJsonString(resp.toString()), IsilonClusterInfo.class);
            // explicitly parse onefs-version, since the key name has "-", we
            // can not do it directly.
            // TODO: new PAPI does not have ""onefs-version-info" element in the
            // response...
            // TODO: version data is part of ../cluster/config response as
            // "onefs_version", see new cluster API
            // info.setVersion(resp.getString("onefs-version-info"));

            return info;
        } catch (Exception e) {
            throw IsilonException.exceptions.unableToGetIsilonClusterInfo(e.getMessage(), e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
        }
    }

    /**
     * Get cluster configuration from the isilon array
     * 
     * @return IsilonClusterConfig object
     * @throws IsilonException
     */
    public IsilonClusterConfig getClusterConfig() throws IsilonException {
        ClientResponse clientResp = null;
        try {
            clientResp = _client.get(_baseUrl.resolve(URI_CLUSTER_CONFIG));
            if (clientResp.getStatus() != 200) {
                throw IsilonException.exceptions.unableToGetIsilonClusterConfig(clientResp.getStatus());
            }

            JSONObject resp = clientResp.getEntity(JSONObject.class);
            IsilonClusterConfig config = new Gson().fromJson(SecurityUtils.sanitizeJsonString(resp.toString()),
                    IsilonClusterConfig.class);

            return config;
        } catch (Exception e) {
            String msg = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            throw IsilonException.exceptions.unableToGetIsilonClusterConfig(msg, e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
        }
    }

    /**
     * 
     * /**
     * Get list of all sub directories of fspath
     * 
     * @param fspath
     *            directory path to lookup
     * @return ArrayList<String> list of names of sub directories
     * @throws IsilonException
     */
    public IsilonList<String> listDir(String fspath, String resumeToken) throws IsilonException {
        fspath = scrubPath(fspath);
        ClientResponse clientResp = null;

        try {
            fspath = URLEncoder.encode(fspath, "UTF-8");
            IsilonList<String> ret = new IsilonList<String>();
            String query = (resumeToken == null) ? "?type=container" : "?type=container&resume=" + resumeToken;
            clientResp = _client.get(_baseUrl.resolve(URI_IFS.resolve(fspath + query)));

            if (clientResp.getStatus() != 200) {
                processErrorResponse("list", "directories", clientResp.getStatus(),
                        clientResp.getEntity(JSONObject.class));
            } else {
                JSONObject resp = clientResp.getEntity(JSONObject.class);
                sLogger.debug("listDir: Output from Server {}", resp.get("children"));

                JSONArray ar = (JSONArray) resp.get("children");
                for (int i = 0; i < ar.length(); i++) {
                    JSONObject ind = ar.getJSONObject(i);
                    ret.add(ind.get("name").toString());
                }
                if (resp.has("resume") && !resp.getString("resume").equals("null")) {
                    // we have more records to fetch -- save the resume token
                    ret.setToken(resp.getString("resume"));
                }
            }
            return ret;
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String msg = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            throw IsilonException.exceptions.unableToGetSubDirectoryList(msg, e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
        }
    }

    /**
     * Checks to see if the dir with the given path exists on the isilon device
     * 
     * @param fspath
     *            directory path to chek
     * @return boolean true if exists, false otherwise
     */
    public boolean existsDir(String fspath) throws IsilonException {
        fspath = scrubPath(fspath);
        ClientResponse resp = null;
        try {
            fspath = URLEncoder.encode(fspath, "UTF-8");
            sLogger.debug("IsilonApi existsDir {} - start", fspath);
            resp = _client.head(_baseUrl.resolve(URI_IFS.resolve(fspath)));
            sLogger.debug("IsilonApi existsDir {} - complete", fspath);
            if (resp.getStatus() != 200) {
                return false;
            }
            return true;
        } catch (Exception e) {
            if (e.getCause() instanceof ConnectException) {
                throw IsilonException.exceptions.unableToConnect(_baseUrl, e);
            }
            final Status status = resp != null ? resp.getClientResponseStatus() : Status.NOT_FOUND;
            throw IsilonException.exceptions.existsDirFailed(fspath, status, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /**
     * Checks if the file system directory has some files or directories
     * 
     * @param fspath
     *            directory path to check
     * @return true - if there are some files or folders, false otherwise
     */
    public boolean fsDirHasData(String fspath) throws IsilonException {
        fspath = scrubPath(fspath);
        ClientResponse clientResp = null;
        try {
            clientResp = _client.get(_baseUrl.resolve(URI_IFS.resolve(fspath)));

            if (clientResp.getStatus() != 200) {
                processErrorResponse("list", "files", clientResp.getStatus(),
                        clientResp.getEntity(JSONObject.class));
            } else {
                JSONObject resp = clientResp.getEntity(JSONObject.class);
                sLogger.debug("fsDirHasData: Output from Server {}", resp.get("children"));

                JSONArray ar = (JSONArray) resp.get("children");
                if (ar != null && ar.length() > 0) {
                    sLogger.info("file system {} has content", fspath);
                    return true;
                }
                sLogger.info("file system {} does not have content", fspath);
                return false;
            }
        } catch (Exception e) {
            sLogger.warn("fsDirHasData - Unable to get the content from path {} due to {}", fspath, e.getMessage());
            return true;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
        }
        return true;
    }

    /**
     * Create a directory with the path specified, will fail if parent does not
     * exist
     * 
     * @param fspath
     *            Dir path to be created
     * @throws IsilonException
     */
    public void createDir(String fspath) throws IsilonException {
        createDir(fspath, false);
    }

    /**
     * Create a directory with the path specified
     * 
     * @param fspath
     *            Dir path to be created
     * @param recursive
     *            if true, will create parent recursively if it doesn't
     *            exist
     * @throws IsilonException
     */
    public void createDir(String fspath, boolean recursive) throws IsilonException {
        fspath = scrubPath(fspath);

        ClientResponse resp = null;
        try {
            // check if already exists
            if (existsDir(fspath)) {
                return;
            }
            fspath = URLEncoder.encode(fspath, "UTF-8");
            MultivaluedMap<String, String> queryParams = null;
            if (recursive) {
                queryParams = new MultivaluedMapImpl();
                queryParams.add("recursive", "1");
            }
            sLogger.debug("IsilonApi createDir {} - start", fspath);
            resp = _client.put(_baseUrl.resolve(URI_IFS.resolve(fspath)), queryParams, "");
            sLogger.debug("IsilonApi createDir {} - complete", fspath);
            if (resp.getStatus() != 200) {
                processErrorResponse("create directory", fspath, resp.getStatus(),
                        resp.getEntity(JSONObject.class));
            }
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            if (e.getCause() instanceof ConnectException) {
                throw IsilonException.exceptions.unableToConnect(_baseUrl, e);
            }
            final Status status = resp != null ? resp.getClientResponseStatus() : Status.NOT_FOUND;
            throw IsilonException.exceptions.createDirFailed(fspath, status, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /**
     * Delete directory on isilon
     * No recursive flag to Isilon API, So the delete directory fails
     * if there are any files and folders in it.
     * 
     * @param fspath
     *            directory path
     * 
     * @throws IsilonException
     */
    public void deleteDir(String fspath) throws IsilonException {
        fspath = scrubPath(fspath);
        ClientResponse resp = null;
        try {
            fspath = URLEncoder.encode(fspath, "UTF-8");
            resp = _client.delete(_baseUrl.resolve(URI_IFS.resolve(fspath)));
            if (resp.getStatus() != 200 && resp.getStatus() != 204 && resp.getStatus() != 404) {
                processErrorResponse("delete", "directory: " + fspath, resp.getStatus(),
                        resp.hasEntity() ? resp.getEntity(JSONObject.class) : null);
            }
        } catch (Exception e) {
            throw IsilonException.exceptions.deleteDirFailedOnIsilonArray(e.getMessage(), e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /**
     * Generic list resources implementation
     * 
     * @param url
     *            url to get from
     * @param key
     *            key representing the array in the response, also represents
     *            the type of object to be listed
     * @param c
     *            Class of the object to parse from the list
     * @return IsilonList<T> ArrayList of objects parsed
     * @throws IsilonException
     */
    private <T> IsilonList<T> list(URI url, String key, Class<T> c, String resumeToken)
            throws IsilonException {

        ClientResponse resp = null;
        try {
            URI getUrl = url;
            if (resumeToken != null && !resumeToken.isEmpty()) {
                // we have a resume token, add it to the url
                getUrl = getUrl.resolve("?resume=" + resumeToken);
            }
            resp = _client.get(getUrl);
            JSONObject obj = resp.getEntity(JSONObject.class);
            IsilonList<T> ret = new IsilonList<T>();
            if (resp.getStatus() == 200) {
                sLogger.debug("list {} : Output from Server: {} ", key, obj.get(key).toString());
                // TODO: "total" is not supported in all lists in Isilon API
                // build 354. List of events and quotas do not have "total".
                // TODO: Need to clarify with Isilon why "total" was droped from
                // some lists and put this code back when fixed if this is
                // Isilon bug.
                // String count = obj.getString("total");
                JSONArray array = obj.getJSONArray(key);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject exp = array.getJSONObject(i);
                    ret.add(new Gson().fromJson(SecurityUtils.sanitizeJsonString(exp.toString()), c));
                }
                // Isilon PAPI sets "total" to "null" string when there are more
                // entries than default page size (1000 entries). Saw this for
                // "events".
                // TODO: count is not support in all lists in b. 354, see note
                // above
                // if (count.equals("null") || Integer.parseInt(count) !=
                // ret.size()) {
                if (obj.has("resume") && !obj.getString("resume").equals("null")) {
                    // we have more records to fetch -- save the resume token
                    ret.setToken(obj.getString("resume"));
                }
                // }
            } else {
                processErrorResponse("list", key, resp.getStatus(), obj);
            }
            return ret;
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String response = String.format("%1$s", (resp == null) ? "" : resp);
            throw IsilonException.exceptions.listResourcesFailedOnIsilonArray(key, response, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }

    }

    /**
     * Generic create resource implementation
     * 
     * @param url
     *            url to post the create to
     * @param key
     *            reference string used in error reporting, representing the
     *            object type
     * @param obj
     *            Object to post for the create
     * @return String identifier returns from the server
     * @throws IsilonException
     */
    private <T> String create(URI url, String key, T obj) throws IsilonException {

        ClientResponse resp = null;
        try {
            String body = new Gson().toJson(obj);
            String id = null;
            resp = _client.post(url, body);
            if (resp.hasEntity()) {
                JSONObject jObj = resp.getEntity(JSONObject.class);
                // JSONObject jObj = checkStatusAndParseObject(resp, "create " +
                // key + ": failed: ");
                sLogger.debug("create {} : Output from Server : ", key, jObj.toString());

                if (jObj.has("id")) {
                    id = jObj.getString("id");
                } else {
                    processErrorResponse("create", key, resp.getStatus(), jObj);
                }
            } else {
                // no entity
                processErrorResponse("create", key, resp.getStatus(), null);
            }
            return id;
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String response = String.format("%1$s", (resp == null) ? "" : resp);
            throw IsilonException.exceptions.createResourceFailedOnIsilonArray(key, response, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /**
     * Generic delete resource
     * 
     * @param url
     *            url to delete
     * @param id
     *            identifier to be deleted
     * @param key
     *            reference string representing the object type being deleted
     * @throws IsilonException
     */
    private void delete(URI url, String id, String key) throws IsilonException {
        ClientResponse resp = null;
        try {
            resp = _client.delete(url.resolve(id));
            if (resp.getStatus() != 200 && resp.getStatus() != 204 && resp.getStatus() != 404) {
                processErrorResponse("delete", key + ": " + id, resp.getStatus(),
                        resp.hasEntity() ? resp.getEntity(JSONObject.class) : null);
                // checkStatusAndParseObject(resp,
                // String.format("delete %1$s failed for id: %2$s", key , id));
            }
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String response = String.format("%1$s", (resp == null) ? "" : resp);
            throw IsilonException.exceptions.deleteResourceFailedOnIsilonArray(key, id, response, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /**
     * Create snapshot Schedule implementation
     * 
     * @param url
     *            url to post the create to
     * @param key
     *            reference string used in error reporting, representing the
     *            object type
     * @param obj
     *            Object to post for the create
     * @return String identifier returns from the server
     * @throws IsilonException
     */
    private <T> String createSnapshotSchedule(URI url, String key, T obj) throws IsilonException {

        ClientResponse resp = null;
        try {
            String body = new Gson().toJson(obj);
            String id = null;
            resp = _client.post(url, body);
            if (resp.hasEntity()) {
                JSONObject jObj = resp.getEntity(JSONObject.class);
                sLogger.debug("Create Snapshot Scedule {} : Output from Server : ", key, jObj.toString());

                if (jObj.has("id")) {
                    id = jObj.getString("id");
                } else {
                    throw IsilonException.exceptions.createSnapshotScheduleError(key, jObj.toString());
                }
            } else {
                // no entity
                throw IsilonException.exceptions.createSnapshotScheduleError(key, String.valueOf(resp.getStatus()));
            }
            return id;
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String response = String.format("%1$s", (resp == null) ? "" : resp);
            throw IsilonException.exceptions.createResourceFailedOnIsilonArray(key, response, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /**
     * delete the snapshot schedule
     * 
     * @param url
     *            url to delete
     * @param id
     *            identifier to be deleted
     * @param key
     *            reference string representing the object type being deleted
     * @throws IsilonException
     */
    private void deleteSnapshotSchedule(URI url) throws IsilonException {
        ClientResponse resp = null;
        try {
            resp = _client.delete(url);
            // error 404 means Snapshot Schedule can not be found, assuming it already deleted.
            if (resp.getStatus() != 200 && resp.getStatus() != 204 && resp.getStatus() != 404) {
                processErrorResponse("delete", "URL =" + url, resp.getStatus(),
                        resp.hasEntity() ? resp.getEntity(JSONObject.class) : null);

            }
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String response = String.format("%1$s", (resp == null) ? "" : resp);
            throw IsilonException.exceptions.deletePolicyFailedOnIsilonArray(url.toString(), response, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /* snapshot schedule */
    /**
     * Create snapshot schedule
     * 
     * @param name
     *            String label to be used for the snapshot schedule
     * @param path
     *            directory path to snapshot
     * @param schedule
     *            frequency at which snapshot is taken
     * @param pattern
     *            naming pattern for the snapshot
     * @param duration
     *            expiration of snapshot
     * @return String identifier for the snapshot schedule created
     * @throws IsilonException
     */
    public String createSnapshotSchedule(String name, String path, String schedule, String pattern, Integer duration)
            throws IsilonException {
        IsilonSnapshotSchedule isiSchedule = new IsilonSnapshotSchedule(name, path, schedule, pattern, duration);
        sLogger.info("Isilon snapshot schedule: {} creation started", isiSchedule.toString());
        return createSnapshotSchedule(isiSchedule);
    }

    /**
     * Create snapshot schedule
     * 
     * @param isiSchedule
     * @return String identifier for the snapshot schedule created
     * @throws IsilonException
     */
    public String createSnapshotSchedule(IsilonSnapshotSchedule isiSchedule)
            throws IsilonException {
        sLogger.info("Isilon snapshot schedule: {} creation started", isiSchedule.toString());
        return createSnapshotSchedule(_baseUrl.resolve(URI_SNAPSHOT_SCHEDULES), "schedule", isiSchedule);
    }

    /**
     * Modify snapshot schedule
     * 
     * @param id
     *            Identifier for the snapshot schedule to be modified
     * @param s
     *            schedules object with the modified values
     * @throws IsilonException
     */
    public void modifySnapshotSchedule(String id, IsilonSnapshotSchedule s) throws IsilonException {
        try {
            id = URLEncoder.encode(id, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            sLogger.error("UnsupportedEncodingException occured", e);
        }
        modify(_baseUrl.resolve(URI_SNAPSHOT_SCHEDULES), id, "schedule", s);
    }

    /**
     * Delete a snapshot schedule
     * 
     * @param id
     *            Identifier of the snapshot to delete
     * @throws IsilonException
     */
    public void deleteSnapshotSchedule(String id) throws IsilonException {
        try {
            id = URLEncoder.encode(id, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            sLogger.error("UnsupportedEncodingException occured", e);
        }
        deleteSnapshotSchedule(_baseUrl.resolve(URI_SNAPSHOT_SCHEDULES + "/" + id));
    }

    /**
     * 
     * @return
     * @throws IsilonException
     */
    public IsilonList<IsilonSnapshotSchedule> getSnapshotSchedules() throws IsilonException {
        return list(_baseUrl.resolve(URI_SNAPSHOT_SCHEDULES), "schedules", IsilonSnapshotSchedule.class, "");
    }

    /**
     * Generic get resource
     * 
     * @param url
     *            url to get from
     * @param id
     *            identifier for the object
     * @param key
     *            reference string representing the object type being deleted
     * @param c
     *            Class of object representing the return value
     * @return T Object parsed from the response, on success
     * @throws IsilonException
     */
    private <T> T get(URI url, String id, String key, Class<T> c) throws IsilonException {

        ClientResponse resp = null;
        try {
            T returnInstance = null;
            resp = _client.get(url.resolve(id));

            if (resp.hasEntity()) {
                JSONObject jObj = resp.getEntity(JSONObject.class);
                if (resp.getStatus() == 200) {
                    JSONArray array = jObj.getJSONArray(key);
                    if (array.length() != 1) {
                        String length = String.format("%1$s", array.length());
                        throw IsilonException.exceptions.getResourceFailedOnIsilonArray(key, length);
                    }

                    JSONObject exp = array.getJSONObject(0);
                    returnInstance = new Gson().fromJson(SecurityUtils.sanitizeJsonString(exp.toString()), c);
                } else {
                    processErrorResponse("get", key + ": " + id, resp.getStatus(), jObj);
                }
            } else {
                // no entity in response
                processErrorResponse("get", key + ": " + id, resp.getStatus(), null);
            }
            return returnInstance;
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String response = String.format("%1$s", (resp == null) ? "" : resp);
            throw IsilonException.exceptions.getResourceFailedOnIsilonArrayExc(key, id, response, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /**
     * Generic get resource when key is not applicable
     * 
     * @param url
     *            url to get from
     * @param id
     *            identifier for the object
     * @param c
     *            Class of object representing the return value
     * @return T Object parsed from the response, on success
     * @throws IsilonException
     */
    private <T> T getObj(URI url, String id, Class<T> c) throws IsilonException {

        ClientResponse resp = null;
        try {
            T returnInstance = null;
            resp = _client.get(url.resolve(id));

            if (resp.hasEntity()) {
                JSONObject jObj = resp.getEntity(JSONObject.class);
                if (resp.getStatus() == 200) {
                    returnInstance = new Gson().fromJson(jObj.toString(), c);
                } else {
                    processErrorResponse("get", id, resp.getStatus(), jObj);
                }
            } else {
                // no entity in response
                processErrorResponse("get", id, resp.getStatus(), null);
            }
            return returnInstance;
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String response = String.format("%1$s", (resp == null) ? "" : resp);
            throw IsilonException.exceptions.getResourceFailedOnIsilonArrayExc("", id, response, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /**
     * Generic modify resource with 204 as HTTP response code.
     * 
     * @param url
     *            url to PUT the modify request
     * @param id
     *            identifier for the object to modify
     * @param key
     *            object type represented as string for error reporting
     * @param obj
     *            modified object to put
     * @throws IsilonException
     */
    private <T> void modify(URI url, String id, String key, T obj) throws IsilonException {
        ClientResponse resp = null;
        try {
            String body = new Gson().toJson(obj);
            resp = _client.put(url.resolve(id), null, body);
            if (resp.getStatus() != 204) {
                // error
                if (resp.hasEntity()) {
                    JSONObject jObj = resp.getEntity(JSONObject.class);
                    processErrorResponse("modify", key + ": " + id, resp.getStatus(), jObj);
                } else {
                    // no entity
                    processErrorResponse("modify", key + ": " + id, resp.getStatus(), null);
                }
            }
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String response = String.format("%1$s", (resp == null) ? "" : resp);
            throw IsilonException.exceptions.modifyResourceFailedOnIsilonArray(key, id,
                    response, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /**
     * Generic modify resource with 200 as HTTP response code.
     * 
     * @param url
     *            url to PUT the modify request
     * @param id
     *            identifier for the object to modify
     * @param key
     *            object type represented as string for error reporting
     * @param obj
     *            modified object to put
     * @throws IsilonException
     */
    private <T> void put(URI url, String id, String key, T obj) throws IsilonException {
        ClientResponse resp = null;
        try {
            String body = new Gson().toJson(obj);
            resp = _client.put(url.resolve(id), null, body);
            if (resp.getStatus() != 200) {
                // error
                if (resp.hasEntity()) {
                    JSONObject jObj = resp.getEntity(JSONObject.class);
                    processErrorResponse("modify", key + ": " + id, resp.getStatus(), jObj);
                } else {
                    // no entity
                    processErrorResponse("modify", key + ": " + id, resp.getStatus(), null);
                }
            }
        } catch (IsilonException ie) {
            throw ie;
        } catch (Exception e) {
            String response = String.format("%1$s", (resp == null) ? "" : resp);
            throw IsilonException.exceptions.modifyResourceFailedOnIsilonArray(key, id,
                    response, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    /* Aliases */
    /*
     * Not working on isilon yet -- commenting for now public String
     * createAlias(String fspath, String alias) throws IsilonException {
     * JSONObject obj; try { obj = new JSONObject(); obj.append("name", alias);
     * obj.append("path", fspath); } catch (Exception ex) { throw new
     * IsilonException("create alias failed alias: " + alias +
     * ": got exception: " + ex.getMessage()); } return create(_baseUrl +
     * URI_ALIAS, "alias", obj);
     * 
     * }
     * 
     * public void deleteAlias(String alias) throws IsilonException {
     * delete(_baseUrl+ URI_ALIAS, alias, "alias"); }
     */

    /* Exports */

    /**
     * List all exports
     * 
     * @return IsilonList of IsilonExport objects
     * @throws IsilonException
     */
    public IsilonList<IsilonExport> listExports(String resumeToken) throws IsilonException {
        return list(_baseUrl.resolve(URI_NFS_EXPORTS), "exports", IsilonExport.class, resumeToken);
    }

    /**
     * List all exports for given access zone
     * 
     * @return IsilonList of IsilonExport objects
     * @throws IsilonException
     */
    public IsilonList<IsilonExport> listExports(String resumeToken, String zoneName) throws IsilonException {
        URI uri = URI_NFS_EXPORTS;
        if (zoneName != null) {
            String baseUrl = getURIWithZoneName(_baseUrl.resolve(uri).toString(), zoneName);
            uri = URI.create(baseUrl);
            sLogger.info("get list of nfs exports for accesszone {} and uri {} ", zoneName, uri.toString());
        } else {
            uri = _baseUrl.resolve(uri);
        }

        return list(uri, "exports", IsilonExport.class, resumeToken);
    }

    /**
     * Create export
     * 
     * @param exp
     *            IsilonExport object with paths and clients set
     * @param force boolean flag to ignore client FQDN check against DNS
     * @return String identifier for the export created
     * @throws IsilonException
     */
    public String createExport(IsilonExport exp, boolean force) throws IsilonException {

        if (force) {
            return create(_baseUrl.resolve(URI_NFS_EXPORTS + "?force=true"), "Export", exp);
        } else {
            return create(_baseUrl.resolve(URI_NFS_EXPORTS), "Export", exp);
        }
    }

    /**
     * Create export on access zone
     * 
     * @param exp
     *            IsilonExport object with paths and clients set
     * @param force boolean flag to ignore client FQDN check against DNS
     * @return String identifier for the export created
     * @throws IsilonException
     */
    public String createExport(IsilonExport exp, String zoneName, boolean force) throws IsilonException {
        String baseUrl = getURIWithZoneName(_baseUrl.resolve(URI_NFS_EXPORTS).toString(), zoneName);
        URI uri = URI.create(baseUrl);
        if (force) {
            uri = URI.create(baseUrl + "&force=true");
        }

        return create(uri, "Export", exp);
    }

    /**
     * Modify export
     * 
     * @param id
     *            identifier of the export to modify
     * @param exp
     *            IsilonExport object with the modified properties
     * @param force boolean flag to ignore client FQDN check against DNS
     * @throws IsilonException
     */
    public void modifyExport(String id, IsilonExport exp, boolean force) throws IsilonException {
        if (force) {
            id = id + "?force=true";
        }
        modify(_baseUrl.resolve(URI_NFS_EXPORTS), id, "export", exp);
    }

    /**
     * Modify export in access zone
     * 
     * @param id
     *            identifier of the export to modify
     * @param exp
     *            IsilonExport object with the modified properties
     * @param force boolean flag to ignore client FQDN check against DNS
     * @throws IsilonException
     */
    public void modifyExport(String id, String zoneName, IsilonExport exp, boolean force) throws IsilonException {
        String uriWithZoneName = getURIWithZoneName(id, zoneName);
        if (force) {
            uriWithZoneName = uriWithZoneName + "&force=true";
        }
        modify(_baseUrl.resolve(URI_NFS_EXPORTS), uriWithZoneName, "export", exp);
    }

    /**
     * Get export
     * 
     * @param id
     *            identifier of the export to get
     * @return IsilonExport object
     * @throws IsilonException
     */
    public IsilonExport getExport(String id) throws IsilonException {
        return get(_baseUrl.resolve(URI_NFS_EXPORTS), id, "exports", IsilonExport.class);
    }

    /**
     * Get export for given access zone
     * 
     * @param id
     *            identifier of the export to get
     * @return IsilonExport object
     * @throws IsilonException
     */
    public IsilonExport getExport(String id, String zoneName) throws IsilonException {
        String uriWithZoneName = getURIWithZoneName(id, zoneName);
        return get(_baseUrl.resolve(URI_NFS_EXPORTS), uriWithZoneName, "exports", IsilonExport.class);
    }

    /**
     * Delete export
     * 
     * @param id
     *            identifier for the export object to delete
     * @throws IsilonException
     */
    public void deleteExport(String id) throws IsilonException {
        delete(_baseUrl.resolve(URI_NFS_EXPORTS), id, "export");
    }

    /**
     * Delete export in access zone
     * 
     * @param id
     *            identifier for the export object to delete
     * @throws IsilonException
     */
    public void deleteExport(String id, String zoneName) throws IsilonException {
        String uriWithZoneName = getURIWithZoneName(id, zoneName);
        delete(_baseUrl.resolve(URI_NFS_EXPORTS), uriWithZoneName, "export");
    }

    /* SmartQuotas */

    /**
     * List all smartquotas for given
     * 
     * @param resumeToken
     * @param pathBaseDir
     * @return
     * @throws IsilonException
     */
    public IsilonList<IsilonSmartQuota> listFileQuotas(String resumeToken) throws IsilonException {
        URI uri = URI_QUOTAS;
        StringBuffer URLBuffer = new StringBuffer(_baseUrl.resolve(uri).toString());
        URLBuffer.append("?path=").append("&recurse_path_children=true&type=directory");
        uri = URI.create(URLBuffer.toString());
        sLogger.info("get list of smart quotas of type directory for uri {}", uri.toString());
        uri = _baseUrl.resolve(uri);
        return list(uri, "quotas", IsilonSmartQuota.class, resumeToken);
    }

    /**
     * List all smartquotas
     * 
     * @return ArrayList of IsilonSmartQuota objects
     * @throws IsilonException
     */
    public IsilonList<IsilonSmartQuota> listQuotas(String resumeToken) throws IsilonException {
        return list(_baseUrl.resolve(URI_QUOTAS), "quotas", IsilonSmartQuota.class, resumeToken);
    }

    /**
     * List all smartquotas for given accesszone
     * 
     * @param resumeToken
     * @param pathBaseDir
     * @return
     * @throws IsilonException
     */
    public IsilonList<IsilonSmartQuota> listQuotas(String resumeToken, String pathBaseDir) throws IsilonException {
        URI uri = URI_QUOTAS;
        if (pathBaseDir != null) {
            StringBuffer URLBuffer = new StringBuffer(_baseUrl.resolve(uri).toString());
            URLBuffer.append("?path=").append(pathBaseDir).append("&recurse_path_children=true");
            uri = URI.create(URLBuffer.toString());
            sLogger.info("get list of smart quotas for pathbaseDir {} and uri {}", pathBaseDir, uri.toString());
        } else {
            uri = _baseUrl.resolve(uri);
        }
        return list(uri, "quotas", IsilonSmartQuota.class, resumeToken);
    }

    /**
     * Create a smartquota
     * 
     * @param path
     *            directory to set quota for
     * @param thresholds
     *            optional long values for the thresholds if none
     *            specified, an un-enforced quota will be created otherwise, the
     *            first value is used for hard limit and rest ignored for now
     * @return Identifier for the quota created
     * @throws IsilonException
     */
    public String createQuota(String path, Long... thresholds) throws IsilonException {
        IsilonSmartQuota quota;
        if (thresholds != null && thresholds.length > 0) {
            quota = constructIsilonSmartQuotaObjectWithThreshold(path, "directory", null, false, false, thresholds);
            quota.setContainer(true); // set to true, so user see hard limit not
            // cluster size.
        } else {
            quota = new IsilonSmartQuota(path);
        }
        sLogger.debug("IsilonApi createQuota {} - start", path);
        String quotaId = create(_baseUrl.resolve(URI_QUOTAS), "quota", quota);
        sLogger.debug("IsilonApi createQuota {} - complete", path);
        return quotaId;
    }

    /**
     * Create a smartquota
     * 
     * @param path
     *            directory to set quota for
     * @param thresholds
     *            optional long values for the thresholds if none
     *            specified, an un-enforced quota will be created otherwise, the
     *            first value is used for hard limit and rest ignored for now
     * @param bThresholdsIncludeOverhead
     *            value to indicate if overhead is
     *            to be included in the quota
     * @param bIncludeSnapshots
     *            value to indicate if snapshot size is to be included
     *            in the quota
     * @return Identifier for the quota created
     * @throws IsilonException
     */
    public String createQuota(String path, Long fsSize, boolean bThresholdsIncludeOverhead,
            boolean bIncludeSnapshots, Long... thresholds) throws IsilonException {
        IsilonSmartQuota quota;
        // Isilon does not allow to create zero quota directory.
        if (thresholds != null && thresholds.length > 0 && (thresholds[0] > 0 || thresholds[1] > 0 || thresholds[2] > 0)) {
            quota = constructIsilonSmartQuotaObjectWithThreshold(path, "directory", fsSize, bThresholdsIncludeOverhead, bIncludeSnapshots,
                    thresholds);
            if (thresholds[0] > 0) {
                quota.setContainer(true); // set to true, so user see hard limit not cluster size.
            }
        } else {
            quota = new IsilonSmartQuota(path, bThresholdsIncludeOverhead, bIncludeSnapshots);
        }
        sLogger.debug("IsilonApi createQuota {} - start", path);
        String quotaId = create(_baseUrl.resolve(URI_QUOTAS), "quota", quota);
        sLogger.debug("IsilonApi createQuota {} - complete", path);
        return quotaId;
    }

    // If we want to provide the UI to enter quota we can re-use this
    public IsilonSmartQuota constructIsilonSmartQuotaObjectWithThreshold(String path, String type, Long fsSize,
            Boolean bThresholdsIncludeOverhead, Boolean bIncludeSnapshots, Long... thresholds) {
        IsilonSmartQuota quota;
        Long size = thresholds[0];
        if (thresholds[0] == 0) {
            size = fsSize;
        }
        switch (thresholds.length) {
            case 2:
                quota = new IsilonSmartQuota(path, type, thresholds[0],
                        (thresholds[1] * size) / 100, 0L, 0L, bThresholdsIncludeOverhead,
                        bIncludeSnapshots);
                break;
            case 3:
                quota = new IsilonSmartQuota(path, type, thresholds[0],
                        (thresholds[1] * size) / 100,
                        (thresholds[2] * size) / 100, 0L, bThresholdsIncludeOverhead,
                        bIncludeSnapshots);
                break;
            case 4:
                quota = new IsilonSmartQuota(path, type, thresholds[0],
                        (thresholds[1] * size) / 100,
                        (thresholds[2] * size) / 100,
                        (thresholds[3] * 60 * 60 * 24), bThresholdsIncludeOverhead,
                        bIncludeSnapshots);
                break;
            default:
                quota = new IsilonSmartQuota(path, type, thresholds[0], 0L, 0L, 0L, bThresholdsIncludeOverhead,
                        bIncludeSnapshots);
                break;
        }
        return quota;
    }

    /**
     * Modify a smartquota
     * 
     * @param id
     *            Identifier for the quota to be modified
     * @param q
     *            IsilonSmartQuota object with the modified values set
     * @throws IsilonException
     */
    public void modifyQuota(String id, IsilonSmartQuota q) throws IsilonException {
        modify(_baseUrl.resolve(URI_QUOTAS), id, "quota", q);
    }

    /**
     * Get smart quota
     * 
     * @param id
     *            Identifier id the smartquota to get
     * @return IsilonSmartQuota object
     * @throws IsilonException
     */
    public IsilonSmartQuota getQuota(String id) throws IsilonException {
        return get(_baseUrl.resolve(URI_QUOTAS), id, "quotas", IsilonSmartQuota.class);
    }

    /**
     * Delete a smart quota
     * 
     * @param id
     *            Identifier of the smart quota object to delete
     * @throws IsilonException
     */
    public void deleteQuota(String id) throws IsilonException {
        delete(_baseUrl.resolve(URI_QUOTAS), id, "quota");
    }

    /* Snapshots */

    /**
     * List all snapshots
     * 
     * @return IsilonList of IsilonSnapshot objects
     * @throws IsilonException
     */
    public IsilonList<IsilonSnapshot> listSnapshots(String resumeToken) throws IsilonException {
        return list(_baseUrl.resolve(URI_SNAPSHOTS), "snapshots", IsilonSnapshot.class, resumeToken);
    }

    /**
     * List all snapshot for given access zone
     * 
     * @param resumeToken
     * @param pathBaseDir
     * @return
     * @throws IsilonException
     */
    public IsilonList<IsilonSnapshot> listSnapshots(String resumeToken, String pathBaseDir) throws IsilonException {
        URI uri = URI_SNAPSHOTS;
        if (pathBaseDir != null) {
            StringBuffer URLBuffer = new StringBuffer(_baseUrl.resolve(uri).toString());
            URLBuffer.append("?path=").append(pathBaseDir).append("&recurse_path_children=true");
            uri = URI.create(URLBuffer.toString());
            sLogger.info("get list of snapshots for pathbaseDir {} and uri {} .", pathBaseDir, uri.toString());
        } else {
            uri = _baseUrl.resolve(uri);
        }
        return list(uri, "snapshots", IsilonSnapshot.class, resumeToken);
    }

    /**
     * List all snapshot created by policy
     * 
     * @param resumeToken
     * @param pathBaseDir
     * @return
     * @throws IsilonException
     */
    public IsilonList<IsilonSnapshot> listSnapshotsCreatedByPolicy(String resumeToken, String policy) throws IsilonException {
        if (policy == null || policy.isEmpty()) {
            return null;
        }
        URI uri = URI_SNAPSHOTS;
        StringBuffer URLBuffer = new StringBuffer(_baseUrl.resolve(uri).toString());
        URLBuffer.append("?schedule=").append(policy);
        uri = URI.create(URLBuffer.toString());
        sLogger.info("get list of snapshots for policy {} and uri {} .", policy, uri.toString());
        return list(uri, "snapshots", IsilonSnapshot.class, resumeToken);
    }

    /**
     * Create snapshot
     * 
     * @param name
     *            String label to be used for the snapshot
     * @param path
     *            directory path to snapshot
     * @return String identifier for the snapshot created
     * @throws IsilonException
     */
    public String createSnapshot(String name, String path) throws IsilonException {
        return create(_baseUrl.resolve(URI_SNAPSHOTS), "snapshot", new IsilonSnapshot(name, path,
                null, null));
    }

    /**
     * Modify snapshot
     * 
     * @param id
     *            Identifier for the snapshot to be modified
     * @param s
     *            IsilonSnapshot object with the modified values
     * @throws IsilonException
     */
    public void modifySnapshot(String id, IsilonSnapshot s) throws IsilonException {
        modify(_baseUrl.resolve(URI_SNAPSHOTS), id, "snapshot", s);
    }

    /**
     * Get snapshot
     * 
     * @param id
     *            Identifier of the snapshot to get
     * @return IsilonSnapshot object
     * @throws IsilonException
     */
    public IsilonSnapshot getSnapshot(String id) throws IsilonException {
        return get(_baseUrl.resolve(URI_SNAPSHOTS), id, "snapshots", IsilonSnapshot.class);
    }

    /**
     * Delete a snapshot
     * 
     * @param id
     *            Identifier of the snapshot to delete
     * @throws IsilonException
     */
    public void deleteSnapshot(String id) throws IsilonException {
        delete(_baseUrl.resolve(URI_SNAPSHOTS), id, "snapshot");
    }

    /* SMB Shares */

    /**
     * List all SMB Shares
     * 
     * @return IsilonList of IsilonSMBShare objects
     * @throws IsilonException
     */
    public IsilonList<IsilonSMBShare> listShares(String resumeToken) throws IsilonException {
        return list(_baseUrl.resolve(URI_SMB_SHARES), "shares", IsilonSMBShare.class, resumeToken);
    }

    /**
     * List all SMB Shares
     * 
     * @return IsilonList of IsilonSMBShare objects
     * @throws IsilonException
     */
    public IsilonList<IsilonSMBShare> listShares(String resumeToken, String zoneName) throws IsilonException {
        URI uri = URI_SMB_SHARES;
        if (zoneName != null) {
            String baseUrl = getURIWithZoneName(_baseUrl.resolve(uri).toString(), zoneName);
            uri = URI.create(baseUrl);
            sLogger.info("get list of shares for accesszone {} and uri {}", zoneName, uri.toString());
        } else {
            uri = _baseUrl.resolve(uri);
        }

        return list(_baseUrl.resolve(uri), "shares", IsilonSMBShare.class, resumeToken);
    }

    /**
     * Create SMB share
     * 
     * @param name
     * @param path
     *            Path to create the share
     * @param desc
     *            Description
     * @param host
     *            Host for access
     * @return Identifier of the SMB share created
     * @throws IsilonException
     */
    public String createShare(String name, String path, String desc, String host)
            throws IsilonException {
        return create(_baseUrl.resolve(URI_SMB_SHARES), "share", new IsilonSMBShare(name, path,
                desc, host));
    }

    /**
     * Create Isilon SMB share.
     * 
     * @param smbFileShare
     * @return Identifier of the SMB share created
     * @throws IsilonException
     */
    public String createShare(IsilonSMBShare smbFileShare) throws IsilonException {
        return create(_baseUrl.resolve(URI_SMB_SHARES), "share", smbFileShare);
    }

    /**
     * Create Isilon SMB share on access zone.
     * 
     * @param smbFileShare
     * @return Identifier of the SMB share created
     * @throws IsilonException
     */
    public String createShare(IsilonSMBShare smbFileShare, String zoneName) throws IsilonException {
        String baseUrl = getURIWithZoneName(_baseUrl.resolve(URI_SMB_SHARES).toString(), zoneName);
        URI uri = URI.create(baseUrl);
        return create(uri, "share", smbFileShare);
    }

    /**
     * Modify SMB share
     * 
     * @param id
     *            Identifier for the SMB share to modify
     * @param s
     *            IsilonSMBShare object with the modified values set
     * @throws IsilonException
     */
    public void modifyShare(String id, IsilonSMBShare s) throws IsilonException {
        modify(_baseUrl.resolve(URI_SMB_SHARES), id, "share", s);
    }

    /**
     * Modify SMB share in access zone
     * 
     * @param id
     *            Identifier for the SMB share to modify
     * @param s
     *            IsilonSMBShare object with the modified values set
     * @throws IsilonException
     */
    public void modifyShare(String id, String zoneName, IsilonSMBShare s) throws IsilonException {
        String uriWithZoneName = getURIWithZoneName(id, zoneName);
        modify(_baseUrl.resolve(URI_SMB_SHARES), uriWithZoneName, "share", s);
    }

    /**
     * Get SMB share properties
     * 
     * @param id
     *            Identifier of the SMB share to get
     * @return IsilonSMBShare object
     * @throws IsilonException
     */
    public IsilonSMBShare getShare(String id) throws IsilonException {
        return get(_baseUrl.resolve(URI_SMB_SHARES), id, "shares", IsilonSMBShare.class);
    }

    /**
     * Get SMB share properties on access zone
     * 
     * @param id
     *            Identifier of the SMB share to get
     * @return IsilonSMBShare object
     * @throws IsilonException
     */
    public IsilonSMBShare getShare(String id, String zoneName) throws IsilonException {

        String uriWithZoneName = getURIWithZoneName(id, zoneName);
        return get(_baseUrl.resolve(URI_SMB_SHARES), uriWithZoneName, "shares", IsilonSMBShare.class);
    }

    /**
     * Delete SMB share
     * 
     * @param id
     *            Identifier of the SMB share to delete
     * @throws IsilonException
     */
    public void deleteShare(String id) throws IsilonException {
        delete(_baseUrl.resolve(URI_SMB_SHARES), id, "share");
    }

    /**
     * Delete SMB share in access zone
     * 
     * @param id
     *            Identifier of the SMB share to delete
     * @throws IsilonException
     */
    public void deleteShare(String id, String zoneName) throws IsilonException {
        String uriWithZoneName = getURIWithZoneName(id, zoneName);
        delete(_baseUrl.resolve(URI_SMB_SHARES), uriWithZoneName, "share");
    }

    /**
     * Modify NFS ACL
     * 
     * @param path
     *            path for the directory or file system to set ACL
     * @param IsilonNFSACL
     *            object with the modified values set
     * @throws IsilonException
     */
    public void modifyNFSACL(String fspath, IsilonNFSACL acl) throws IsilonException {
        try {
            fspath = fspath.substring(1);// remove '/' prefix
            fspath = URLEncoder.encode(fspath, "UTF-8");
            fspath = fspath.concat("?acl");// add suffix ?acl

        } catch (UnsupportedEncodingException e) {
            sLogger.error("UnsupportedEncodingException occured", e);
        }

        put(_baseUrl.resolve(URI_IFS), fspath, "ACL", acl);
    }

    /**
     * Get NFS ACL properties
     * 
     * @param path
     *            Identifier of the SMB share to get
     * @return IsilonNFSACL object
     * @throws IsilonException
     */
    public IsilonNFSACL getNFSACL(String fspath) throws IsilonException {
        try {
            fspath = fspath.substring(1);// remove '/' prefix
            fspath = URLEncoder.encode(fspath, "UTF-8");
            fspath = fspath.concat("?acl");// add suffix ?acl
        } catch (UnsupportedEncodingException e) {
            sLogger.error("UnsupportedEncodingException occured", e);
        }
        return getObj(_baseUrl.resolve(URI_IFS), fspath, IsilonNFSACL.class);
    }

    /**
     * Get storage pools.
     * 
     * @return storage pools
     * @throws IsilonException
     */
    public List<? extends IsilonPool> getStoragePools() throws IsilonException {
        IsilonList<IsilonStoragePool> pools = list(_baseUrl.resolve(URI_STORAGE_POOLS),
                "storagepools", IsilonStoragePool.class, null);
        return pools.getList();
    }

    /**
     * Get disk pools for OneFS version < 7.2
     * 
     * @return disk pools
     * @throws IsilonException
     */
    public List<? extends IsilonPool> getDiskPools() throws IsilonException {
        IsilonList<IsilonDiskPool> pools = list(_baseUrl.resolve(URI_DISK_POOLS),
                "diskpools", IsilonDiskPool.class, null);
        return pools.getList();
    }

    /**
     * Get storage ports.
     * 
     * @return storage ports
     * @throws IsilonException
     */
    public List<IsilonStoragePort> getSmartConnectPorts() throws IsilonException {
        IsilonList<IsilonStoragePort> ports = list(_baseUrl.resolve(URI_STORAGE_PORTS), "zones",
                IsilonStoragePort.class, null);
        return ports.getList();
    }

    public IsilonSmartConnectInfo getSmartConnectInfo() throws IsilonException {
        ClientResponse clientResp = null;
        try {
            clientResp = _client.get(_baseUrl.resolve(URI_STORAGE_PORTS));
            if (clientResp.getStatus() != 200) {
                throw IsilonException.exceptions.getStorageConnectionInfoFailedOnIsilonArray(clientResp.getStatus());
            }

            JSONObject resp = clientResp.getEntity(JSONObject.class);
            sLogger.debug(resp.toString());
            IsilonSmartConnectInfo info = new Gson().fromJson(SecurityUtils.sanitizeJsonString(resp.toString()),
                    IsilonSmartConnectInfo.class);
            return info;
        } catch (Exception e) {
            String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            throw IsilonException.exceptions.getStorageConnectionInfoFailedOnIsilonArrayExc(response, e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
        }
    }

    public IsilonSmartConnectInfoV2 getSmartConnectInfoV2() throws IsilonException {
        ClientResponse clientResp = null;
        try {
            clientResp = _client.get(_baseUrl.resolve(URI_STORAGE_PORTS));
            if (clientResp.getStatus() != 200) {
                sLogger.debug("Response: Exception :" + clientResp.toString());
                throw new IsilonException(clientResp.getStatus() + "");
            }

            IsilonSmartConnectInfoV2 info = null;
            String responseString = null;
            try {
                responseString = clientResp.getEntity(String.class);
                sLogger.debug("Response:" + responseString);
                info = new Gson().fromJson(SecurityUtils.sanitizeJsonString(responseString),
                        IsilonSmartConnectInfoV2.class);
            } catch (Exception e) {
                sLogger.debug("Got Exception trying to get Json out of it " + e);
                sLogger.debug("Response:String:" + responseString);
                String fixedString = "{\"settings\":" + responseString + "}";
                sLogger.debug("Fixed:String:" + fixedString);
                info = new Gson().fromJson(SecurityUtils.sanitizeJsonString(fixedString),
                        IsilonSmartConnectInfoV2.class);
            }
            return info;
        } catch (Exception e) {
            String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            throw new IsilonException(response, e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
        }
    }

    /**
     * get the list of access zone
     * 
     * @return
     * @throws IsilonException
     */
    public List<IsilonAccessZone> getAccessZones(String resumeToken) throws IsilonException {
        IsilonList<IsilonAccessZone> accessZoneIsilonList = list(_baseUrl.resolve(URI_ACCESS_ZONES),
                "zones", IsilonAccessZone.class, resumeToken);
        return accessZoneIsilonList.getList();
    }

    /**
     * get user details from providers configured in Isilon.
     * 
     * @return
     * @throws IsilonException
     */
    public List<IsilonUser> getUsersDetail(String zone, String provider, String domain, String name, String resumeToken)
            throws IsilonException {
        StringBuffer buffer = new StringBuffer(_baseUrl.resolve(URI_AUTH_USERS).toString());
        buffer.append("?resolve_names=true");
        buffer.append("&provider=" + provider);
        if (domain != null && !domain.isEmpty()) {
            buffer.append("&domain=" + domain);
        }
        buffer.append("&zone=");
        String zoneName = zone.replace(" ", "%20");
        buffer.append(zoneName);
        buffer.append("&filter=" + name);
        URI uri = URI.create(buffer.toString());
        IsilonList<IsilonUser> userList = list(uri, "users", IsilonUser.class, resumeToken);
        return userList.getList();
    }

    /**
     * get group details from providers configured in Isilon.
     * 
     * @return
     * @throws IsilonException
     */
    public List<IsilonGroup> getGroupsDetail(String zone, String provider, String domain, String name, String resumeToken)
            throws IsilonException {
        StringBuffer buffer = new StringBuffer(_baseUrl.resolve(URI_AUTH_GROUPS).toString());
        buffer.append("?resolve_names=true");
        buffer.append("&provider=" + provider);
        if (domain != null && !domain.isEmpty()) {
            buffer.append("&domain=" + domain);
        }
        buffer.append("&zone=");
        String zoneName = zone.replace(" ", "%20");
        buffer.append(zoneName);
        buffer.append("&filter=" + name);
        URI uri = URI.create(buffer.toString());
        IsilonList<IsilonGroup> groupList = list(uri, "groups", IsilonGroup.class, resumeToken);
        return groupList.getList();

    }

    /**
     * get the list of network pools
     * 
     * @return
     * @throws IsilonException
     */
    public List<IsilonNetworkPool> getNetworkPools(String resumeToken) throws IsilonException {
        IsilonList<IsilonNetworkPool> accessZoneIsilonList = list(_baseUrl.resolve(URI_NETWORK_POOLS),
                "pools", IsilonNetworkPool.class, resumeToken);
        return accessZoneIsilonList.getList();
    }

    /**
     * Get list of events from the url
     * 
     * @param url
     * @param firmwareVersion
     *            : Isilon version
     * @return ArrayList of IsilonEvent objects
     * @throws IsilonException
     */
    private IsilonList<IsilonEvent> getEvents(URI url, String firmwareVersion) throws IsilonException {

        // Get list of ISILON events using eventlists if ISILON version is OneFS8.0 or more else using events.
        if (firmwareVersion != null && firmwareVersion.startsWith("8")) {
            List<IsilonOneFS8Event> eventLists = list(url, "eventlists", IsilonOneFS8Event.class, null).getList();
            IsilonList<IsilonEvent> isilonEventList = new IsilonList<IsilonEvent>();

            for (IsilonOneFS8Event eventFS8 : eventLists) {
                for (Events event : eventFS8.getEvents()) {
                    IsilonEvent isilonEvent = new IsilonEvent();
                    isilonEvent.devid = event.devid;
                    isilonEvent.event_type = event.event;
                    isilonEvent.id = event.id;
                    isilonEvent.message = event.message;
                    isilonEvent.severity = event.severity;
                    isilonEvent.start = event.time;
                    isilonEvent.specifiers = event.getSpecifier();
                    isilonEvent.value = event.value;
                    isilonEventList.add(isilonEvent);
                }
            }
            return isilonEventList;
        }
        return list(url, "events", IsilonEvent.class, null);

    }

    /**
     * Get the list of events
     * 
     * @return IsilonList of IsilonEvent objects
     * @throws IsilonException
     */
    public IsilonList<IsilonEvent> listEvents(String resumeToken) throws IsilonException {
        return list(_baseUrl.resolve(URI_EVENTS), "events", IsilonEvent.class, resumeToken);

    }

    /**
     * Get the list of events in the time range
     * 
     * @param begin
     *            number of seconds relative to current (e.g. -3600 for 1hr
     *            back)
     * @param end
     *            number of seconds relative to current
     * @param firmwareVersion
     *            : Isilon version
     * @return ArrayList of IsilonEvent objects
     * @throws IsilonException
     */
    public IsilonList<IsilonEvent> queryEvents(long begin, long end, String firmwareVersion) throws IsilonException {
        // In Isilon API, 0 value for time in query is used as beginning of
        // absolute time.
        // We use 0 value for time to indicate current time on remote host.
        // To use current end time on remote host in Isilon API query, do not
        // specify end time in the query.
        // TODO: Need to find out what does "intersect" query parameter mean.
        // Default behavior is intersect = true.
        String query = (end != 0) ? String.format("?begin=%1$d&end=%2$d", begin, end) : String
                .format("?begin=%1$d", begin);

        // If ISILON version is OneFS8.0 then get events URI will be /platform/3/event/eventlists/.
        if (firmwareVersion != null && firmwareVersion.startsWith("8")) {
            return getEvents(_baseUrl.resolve(URI_ONEFS8_EVENTS.resolve(query)), firmwareVersion);
        }
        return getEvents(_baseUrl.resolve(URI_EVENTS.resolve(query)), firmwareVersion);
    }

    /**
     * Remove leading slash (if it exists) to use as relative path (with a base
     * URI)
     * 
     * @param fsPath
     * @return
     */
    private String scrubPath(String fsPath) {
        if (fsPath.charAt(0) == '/') {
            return fsPath.substring(1);
        }
        return fsPath;
    }

    /**
     * Get current statistics
     * 
     * @param key
     *            Stats's key
     * @return map of node number to IsilonStats.StatValueCurrent
     * @throws IsilonException
     */
    public <T> HashMap<String, IsilonStats.StatValueCurrent<T>> getStatsCurrent(String key,
            Type valueType) throws IsilonException {
        ClientResponse clientResp = null;
        try {
            clientResp = _client.get(_baseUrl.resolve(URI_STATS.resolve(String.format(
                    "current?key=%1$s&devid=0", key))));
            if (clientResp.getStatus() != 200) {
                throw IsilonException.exceptions.getCurrentStatisticsFailedOnIsilonArray(clientResp.getStatus());
            }

            JSONObject resp = clientResp.getEntity(JSONObject.class);
            JSONObject obj = resp.getJSONObject(key);

            HashMap<String, IsilonStats.StatValueCurrent<T>> retMap = new HashMap<String, IsilonStats.StatValueCurrent<T>>();
            Iterator it = obj.keys();
            while (it.hasNext()) {
                String entryKey = it.next().toString();
                JSONObject entryObj = obj.getJSONObject(entryKey);
                IsilonStats.StatValueCurrent<T> statValueCurrent = new IsilonStats.StatValueCurrent<T>();
                statValueCurrent.time = entryObj.getLong("time");
                statValueCurrent.error = new ArrayList<String>();
                JSONArray jsonArray = entryObj.getJSONArray("error");
                int len = jsonArray.length();
                for (int i = 0; i < len; i++) {
                    String val = jsonArray.get(i).toString();
                    if (val.equals("0")) {
                        continue;
                    }
                    statValueCurrent.error.add(jsonArray.get(i).toString());
                }
                if (!entryObj.has("value")) {
                    throw IsilonException.exceptions.getCurrentStatisticsFailedOnIsilonArrayErr(key,
                            statValueCurrent.error.toString());
                }
                statValueCurrent.value = new Gson()
                        .fromJson(SecurityUtils.sanitizeJsonString(entryObj.getString("value")), valueType);
                retMap.put(entryKey, statValueCurrent);
            }
            return retMap;
        } catch (Exception e) {
            String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            throw IsilonException.exceptions.getCurrentStatisticsFailedOnIsilonArrayExc(response, e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
        }
    }

    /**
     * Get statistic history
     * 
     * @param key
     *            Stats's key
     * @param valueType
     * @return IsilonStats.StatValueHistory
     * @throws IsilonException
     */
    public <T> HashMap<String, IsilonStats.StatValueHistory<T>> getStatsHistory(String key,
            long begin, Type valueType) throws IsilonException {
        ClientResponse clientResp = null;
        try {
            clientResp = _client.get(_baseUrl.resolve(URI_STATS.resolve(String.format(
                    "history?key=%1$s&devid=0&begin=%2$s", key, begin))));
            if (clientResp.getStatus() != 200) {
                throw IsilonException.exceptions.getStatisticsHistoryFailedOnIsilonArray(clientResp.getStatus());
            }

            JSONObject resp = clientResp.getEntity(JSONObject.class);
            JSONObject obj = resp.getJSONObject(key);
            HashMap<String, IsilonStats.StatValueHistory<T>> retMap = new HashMap<String, IsilonStats.StatValueHistory<T>>();
            Iterator it = obj.keys();
            while (it.hasNext()) {
                String entryKey = it.next().toString();
                JSONObject entryObj = obj.getJSONObject(entryKey);
                IsilonStats.StatValueHistory<T> statValueHistory = new IsilonStats.StatValueHistory<T>();
                if (entryObj.has("error")) {
                    JSONArray array = entryObj.getJSONArray("error");
                    for (int i = 0; i < array.length(); i++) {
                        String val = array.get(i).toString();
                        if (val.equals("0")) {
                            continue;
                        }
                        statValueHistory.error.add(array.get(i).toString());
                    }
                }
                if (entryObj.has("values")) {
                    JSONArray array = entryObj.getJSONArray("values");
                    for (int i = 0; i < array.length(); i++) {
                        // each value is again an array, [timestamp, T]
                        JSONArray entry = (JSONArray) array.get(i);
                        if (entry.length() == 2) {
                            long timestamp = entry.getLong(0);
                            T value = new Gson().fromJson(SecurityUtils.sanitizeJsonString(entry.getString(1)), valueType);
                            statValueHistory.values.put(timestamp, value);
                        }
                    }
                }
                if (statValueHistory.error.isEmpty() && statValueHistory.values.isEmpty()) {
                    continue;
                }
                retMap.put(entryKey, statValueHistory);
            }
            return retMap;
        } catch (Exception e) {
            String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            throw IsilonException.exceptions.getStatisticsHistoryFailedOnIsilonArrayExc(response, e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
        }
    }

    /**
     * Get statistic protocols
     * 
     * @return protocol list
     * @throws Exception
     *             IsilonException
     */
    public ArrayList<IsilonStats.Protocol> getStatsProtocols() throws IsilonException {

        ArrayList<IsilonStats.Protocol> statProtocols = new ArrayList<IsilonStats.Protocol>();
        ClientResponse clientResp = null;
        try {
            clientResp = _client.get(_baseUrl.resolve(URI_STATS.resolve("protocols")));
            if (clientResp.getStatus() != 200) {
                throw IsilonException.exceptions.getStatisticsProtocolFailedOnIsilonArray(clientResp.getStatus());
            }

            // ObjectMapper mapper = new ObjectMapper();
            JSONObject resp = clientResp.getEntity(JSONObject.class);
            // IsilonStats.Protocols protocols =
            // mapper.readValue(resp.toString(), IsilonStats.Protocols.class);
            JSONArray protocols = resp.getJSONArray("protocols");
            for (int i = 0; i < protocols.length(); i++) {
                JSONObject protocol = protocols.getJSONObject(i);
                statProtocols.add(new Gson().fromJson(SecurityUtils.sanitizeJsonString(protocol.toString()),
                        IsilonStats.Protocol.class));
            }
            return statProtocols;
        } catch (Exception e) {
            String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            throw IsilonException.exceptions.getStatisticsProtocolFailedOnIsilonArrayExc(response, e);

        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
        }
    }

    /**
     * Process http error response from Isilon
     * 
     * @param operationKey
     *            opertaion key: list, create, delete, modify, etc
     * @param objectKey
     *            object type: export, snapshot, smb share,...
     * @param httpStatus
     *            http status
     * @param errorEntity
     *            entity of error response
     * @throws IsilonException
     * @throws JSONException
     */
    private void processErrorResponse(String operationKey, String objectKey, int httpStatus,
            JSONObject errorEntity) throws IsilonException, JSONException {
        if (errorEntity == null) {
            throw IsilonException.exceptions.processErrorResponseFromIsilon(operationKey,
                    objectKey, httpStatus, _baseUrl);
        } else if (errorEntity.has("errors")) {
            throw IsilonException.exceptions.processErrorResponseFromIsilonMsg(operationKey,
                    objectKey, httpStatus, _baseUrl, errorEntity.getString("errors"));
        } else if (errorEntity.has("message")) {
            throw IsilonException.exceptions.processErrorResponseFromIsilonMsg(operationKey,
                    objectKey, httpStatus, _baseUrl, errorEntity.getString("message"));
        } else {
            throw IsilonException.exceptions.processErrorResponseFromIsilon(operationKey,
                    objectKey, httpStatus, _baseUrl);
        }
    }

    /**
     * Checks to see if the NFSv4 service is enabled on the isilon device
     * 
     * @return boolean true if exists, false otherwise
     */
    public boolean nfsv4Enabled(String firmwareVersion) throws IsilonException {
        ClientResponse resp = null;
        boolean isNfsv4Enabled = false;
        try {
            sLogger.debug("IsilonApi check nfsV4 support retrieve global status - start");

            // Check if ISILON ONEFS version is 8.0 and more to get NFSV4 details
            if (firmwareVersion.startsWith("8")) {
                resp = _client.get(_baseUrl.resolve(URI_ARRAY_GLOBAL_STATUS_ONEFS8));
            } else {
                resp = _client.get(_baseUrl.resolve(URI_ARRAY_GLOBAL_STATUS));
            }
            sLogger.debug("IsilonApi check nfsV4 support retrieve global status - complete");

            JSONObject jsonResp = resp.getEntity(JSONObject.class);

            isNfsv4Enabled = Boolean.parseBoolean(jsonResp.getJSONObject(
                    "settings").getString("nfsv4_enabled"));

            sLogger.info("IsilonApi  nfsv4 enable/disable is set to {}",
                    isNfsv4Enabled);

        } catch (Exception e) {
            throw IsilonException.exceptions.unableToConnect(_baseUrl, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
        return isNfsv4Enabled;
    }

    /**
     * Checks to see if the SyncIQ service is enabled on the isilon device
     * 
     * @return boolean true if exists, false otherwise
     */
    public boolean isSyncIQEnabled(String firmwareVersion) throws IsilonException {
        ClientResponse resp = null;
        boolean isSyncIqEnabled = false;

        try {
            // Verify the Sync service is enable or not
            // JSON response for the below should have service=on
            resp = _client.get(_baseUrl.resolve(URI_SYNCIQ_SERVICE_STATUS));
            JSONObject jsonResp = resp.getEntity(JSONObject.class);
            if (jsonResp.has("settings") && jsonResp.getJSONObject("settings") != null) {
                if (jsonResp.getJSONObject("settings").has("service")) {
                    String syncService = jsonResp.getJSONObject("settings").getString("service");
                    if (syncService != null && !syncService.isEmpty()) {
                        sLogger.info("IsilonApi - SyncIQ service status {} ", syncService);
                        if ("on".equalsIgnoreCase(syncService)) {
                            isSyncIqEnabled = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw IsilonException.exceptions.unableToConnect(_baseUrl, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
        return isSyncIqEnabled;
    }

    /**
     * Get SyncIq license information from the Isilon array
     * 
     * @return IsilonReplicationLicenseInfo object
     * @throws IsilonException
     * @throws JSONException
     */

    public String getReplicationLicenseInfo() throws IsilonException, JSONException {
        String licenseStatus = "Unknown";
        ClientResponse clientResp = _client.get(_baseUrl.resolve(URI_REPLICATION_LICENSE_INFO));
        JSONObject jsonResp = clientResp.getEntity(JSONObject.class);
        if (jsonResp.has("status")) {
            licenseStatus = jsonResp.get("status").toString();
            return licenseStatus;
        }
        return licenseStatus;
    }

    /**
     * Get Replication Policy information from the Isilon array
     * 
     * @return IsilonSyncPolicy object
     * @throws IsilonException
     */
    public IsilonSyncPolicy getReplicationPolicy(String id) throws IsilonException {
        return get(_baseUrl.resolve(URI_REPLICATION_POLICIES), id, "policies", IsilonSyncPolicy.class);
    }

    /**
     * Get Replication Policy information from the Isilon array using oneFS v8 above
     * 
     * @return IsilonSyncPolicy object
     * @throws IsilonException
     */
    public IsilonSyncPolicy8Above getReplicationPolicy8above(String id) throws IsilonException {
        return get(_baseUrl.resolve(URI_REPLICATION_POLICIES_8), id, "policies", IsilonSyncPolicy8Above.class);
    }

    /**
     * Get All Replication Policies information from the Isilon array
     * 
     * @return IsilonList<IsilonSyncPolicy>
     * @throws IsilonException
     */
    public IsilonList<IsilonSyncPolicy> getReplicationPolicies() throws IsilonException {
        return list(_baseUrl.resolve(URI_REPLICATION_POLICIES), "policies", IsilonSyncPolicy.class, "");
    }

    /**
     * Get All Replication Policies information from the Isilon array
     * 
     * @return IsilonList<IsilonSyncPolicy>
     * @throws IsilonException
     */
    public IsilonList<IsilonSyncPolicy8Above> getReplicationPolicies8above() throws IsilonException {
        return list(_baseUrl.resolve(URI_REPLICATION_POLICIES_8), "policies", IsilonSyncPolicy8Above.class, "");
    }

    /**
     * Get Target Replication Policy information from the Isilon array
     * 
     * @return IsilonSyncPolicy object
     * @throws IsilonException
     */
    public IsilonSyncTargetPolicy getTargetReplicationPolicy(String id) throws IsilonException {
        return get(_baseUrl.resolve(URI_TARGET_REPLICATION_POLICIES), id, "policies", IsilonSyncTargetPolicy.class);
    }

    /**
     * Create Replication Policy
     * 
     * @param replicationPolicy
     *            IsilonSyncPolicy object
     * @return String identifier for the policy created
     * @throws IsilonException
     */
    public String createReplicationPolicy(IsilonSyncPolicy replicationPolicy) throws IsilonException {
        return create(_baseUrl.resolve(URI_REPLICATION_POLICIES), "policies", replicationPolicy);
    }

    /**
     * Create Replication Policy for isilon array using oneFSv8 and above
     * 
     * @param replicationPolicy
     *            IsilonSyncPolicy object
     * @return String identifier for the policy created
     * @throws IsilonException
     */
    public String createReplicationPolicy8above(IsilonSyncPolicy8Above replicationPolicy) throws IsilonException {
        return create(_baseUrl.resolve(URI_REPLICATION_POLICIES_8), "policies", replicationPolicy);
    }

    /**
     * Modify Replication Policy
     * 
     * @param id
     *            identifier/name of the Replication Policy to modify
     * @param syncPolicy
     *            IsilonSyncPolicy object with the modified properties
     * @throws IsilonException
     */
    public void modifyReplicationPolicy(String id, IsilonSyncPolicy syncPolicy) throws IsilonException {
        modify(_baseUrl.resolve(URI_REPLICATION_POLICIES), id, "policies", syncPolicy);
    }

    /**
     * Modify Replication Policyfor isilon array using oneFSv8 and above
     * 
     * @param id
     *            identifier/name of the Replication Policy to modify
     * @param syncPolicy
     *            IsilonSyncPolicy object with the modified properties
     * @throws IsilonException
     */
    public void modifyReplicationPolicy8above(String id, IsilonSyncPolicy8Above syncPolicy) throws IsilonException {
        modify(_baseUrl.resolve(URI_REPLICATION_POLICIES_8), id, "policies", syncPolicy);
    }

    /**
     * Delete replication policy
     * 
     * @param id
     *            identifier for the replication policy object to delete
     * @throws IsilonException
     */
    public void deleteReplicationPolicy(String id) throws IsilonException {
        delete(_baseUrl.resolve(URI_REPLICATION_POLICIES), id, "policies");
    }

    /**
     * Get Replication Jobs information from the Isilon array
     * 
     * @param id
     *            identifier for the replication policy
     * @return Replication Jobs object
     * @throws IsilonException
     */
    public IsilonSyncJob getReplicationJob(String id) throws IsilonException {
        return get(_baseUrl.resolve(URI_REPLICATION_JOBS), id, "jobs", IsilonSyncJob.class);
    }

    /**
     * Start a Replication Job
     * 
     * @param IsilonSyncJob
     *            Object
     * @return policy_name
     * @throws IsilonException
     */
    public String modifyReplicationJob(IsilonSyncJob job) throws IsilonException {
        return create(_baseUrl.resolve(URI_REPLICATION_JOBS), "jobs", job);
    }

    /**
     * Modify Replication Job
     * 
     * @param id
     *            identifier/name of the Replication Policy to modify
     * @param syncPolicy
     *            IsilonSyncPolicy object with the modified properties
     * @throws IsilonException
     */
    public void modifyReplicationJob(String id, IsilonSyncJob job) throws IsilonException {
        modify(_baseUrl.resolve(URI_REPLICATION_JOB), id, "jobs", job);
    }

    /**
     * Get Replication Reports information from the Isilon array
     * 
     * @param Name
     *            for the replication policy
     * @return Replication Report Object
     * @throws IsilonException
     */

    public IsilonList<IsilonSyncPolicyReport> getReplicationPolicyReports(String policyName) throws IsilonException {
        URI uri = URI.create(URI_REPLICATION_POLICY_REPORTS.toString() + policyName);
        return list(_baseUrl.resolve(uri), "reports", IsilonSyncPolicyReport.class, "");
    }

    /**
     * Get Target Replication Reports information from the Isilon array
     * 
     * @param Name
     *            for the replication policy
     * @return Replication Report Object
     * @throws IsilonException
     */

    public IsilonList<IsilonSyncPolicyReport> getTargetReplicationPolicyReports(String policyName) throws IsilonException {
        URI uri = URI.create(URI_TARGET_REPLICATION_POLICY_REPORTS.toString() + policyName);
        return list(_baseUrl.resolve(uri), "reports", IsilonSyncPolicyReport.class, "");
    }

    private String getURIWithZoneName(String id, String zoneName) {
        StringBuffer buffer = new StringBuffer(id);
        buffer.append("?zone=");
        String accesszoneName = zoneName.replace(" ", "%20");
        buffer.append(accesszoneName);
        return buffer.toString();
    }

    /**
     * Checks the status of a license on Isilon
     * 
     * @param licenseType
     *            type of the license for which the activation status is required
     * @return licenseStatus Status of license
     * @throws IsilonException
     * @throws JSONException
     */
    public String getLicenseInfo(IsilonLicenseType licenseType) throws IsilonException, JSONException {
        ClientResponse resp = null;
        String licenseStatus = "Unknown";

        try {
            // Verify whether specified license is activated on ISILON array or not
            resp = _client.get(_baseUrl.resolve(licenseMap.get(licenseType)));
            JSONObject jsonResp = resp.getEntity(JSONObject.class);
            if (jsonResp.has("status")) {
                licenseStatus = jsonResp.get("status").toString();
                return licenseStatus;
            }
        } catch (Exception e) {
            throw IsilonException.exceptions.unableToConnect(_baseUrl, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
        return licenseStatus;
    }

    /**
     * Checks to see if the SnapshotIQ service is enabled on the isilon device
     * 
     * @return licenseStatus Status of SnapshotIQ license
     * @throws IsilonException
     * @throws JSONException
     */
    public String snapshotIQLicenseInfo() throws IsilonException, JSONException {
        ClientResponse resp = null;
        String licenseStatus = "Unknown";

        try {
            // Verify whether SnapshotIQ service is enabled on ISILON array or not
            resp = _client.get(_baseUrl.resolve(URI_SNAPSHOTIQ_LICENSE_INFO));
            JSONObject jsonResp = resp.getEntity(JSONObject.class);
            if (jsonResp.has("status")) {
                licenseStatus = jsonResp.get("status").toString();
            }
        } catch (Exception e) {
            throw IsilonException.exceptions.unableToConnect(_baseUrl, e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
        sLogger.info("Isilon snapshotIQ license status is  {}", licenseStatus);
        return licenseStatus;
    }

}
