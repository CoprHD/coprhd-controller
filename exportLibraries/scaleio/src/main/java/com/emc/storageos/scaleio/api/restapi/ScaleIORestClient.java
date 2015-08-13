/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.scaleio.api.restapi;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.scaleio.ScaleIOException;
import com.emc.storageos.scaleio.api.ScaleIOAddVolumeResult;
import com.emc.storageos.scaleio.api.ScaleIOHandle;
import com.emc.storageos.scaleio.api.ScaleIOMapVolumeToSCSIInitiatorResult;
import com.emc.storageos.scaleio.api.ScaleIOMapVolumeToSDCResult;
import com.emc.storageos.scaleio.api.ScaleIOModifyVolumeCapacityResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllSCSIInitiatorsResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllSDCResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllSDSResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryAllVolumesResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryClusterResult;
import com.emc.storageos.scaleio.api.ScaleIOQueryStoragePoolResult;
import com.emc.storageos.scaleio.api.ScaleIORemoveConsistencyGroupSnapshotsResult;
import com.emc.storageos.scaleio.api.ScaleIORemoveVolumeResult;
import com.emc.storageos.scaleio.api.ScaleIOSnapshotMultiVolumeResult;
import com.emc.storageos.scaleio.api.ScaleIOSnapshotVolumeResult;
import com.emc.storageos.scaleio.api.ScaleIOUnMapVolumeFromSCSIInitiatorResult;
import com.emc.storageos.scaleio.api.ScaleIOUnMapVolumeToSDCResult;
import com.emc.storageos.services.restutil.StandardRestClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;

import com.emc.storageos.scaleio.api.ScaleIOContants;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIOCreateVolume;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIOMapVolumeToSDC;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIOMapVolumeToScsiInitiator;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIOModifyVolumeSize;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIORemoveConsistencyGroupSnapshots;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIORemoveVolume;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIOSnapshotVolumes;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIOUnmapVolumeToSDC;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIOUnmapVolumeToScsiInitiator;
import com.emc.storageos.scaleio.api.restapi.request.ScaleIOVolumeList;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOProtectionDomain;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOScsiInitiator;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSdc;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSds;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSds.Ip;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSnapshotVolumeResponse;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOStoragePool;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSystem;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;

import com.google.gson.Gson;

/**
 * This class implements interfaces for calling ScaleIO REST API.
 */

public class ScaleIORestClient extends StandardRestClient implements ScaleIOHandle {

    private static Logger log = LoggerFactory.getLogger(ScaleIORestClient.class);
    ScaleIORestClientFactory factory;
    private Map<String, String> protectionDomainMap = new HashMap<String, String>();

    /**
     * Constructor
     * 
     * @param client
     *            A reference to a Jersey Apache HTTP client.
     * @param username
     *            The user to be authenticated.
     * @param password
     *            The user password for authentication.
     */
    public ScaleIORestClient(ScaleIORestClientFactory factory, URI baseURI, String username, String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _authToken = "";
        this.factory = factory;
    }

    public void setUsername(String username) {
        _username = username;
    }

    public void setPassword(String password) {
        _password = password;
    }

    public String init() throws Exception {
        String version = getVersion();
        List<ScaleIOProtectionDomain> domains = getProtectionDomains();
        for (ScaleIOProtectionDomain domain : domains) {
            protectionDomainMap.put(domain.getId(), domain.getName());
        }
        return version;
    }

    @Override
    public ScaleIOQueryAllResult queryAll() throws Exception {
        ScaleIOSystem system = getSystem();

        List<ScaleIOProtectionDomain> domains = getProtectionDomains();
        ScaleIOQueryAllResult result = new ScaleIOQueryAllResult();
        result.setProperty(ScaleIOContants.SCALEIO_INSTALLATION_ID, system.getInstallId());
        result.setProperty(ScaleIOContants.SCALEIO_VERSION, system.getVersion());
        result.setProperty(ScaleIOContants.SCALEIO_CUSTOMER_ID, system.getId());
        for (ScaleIOProtectionDomain domain : domains) {
            String domainName = domain.getName();
            result.addProtectionDomain(domainName, domain.getId());
            List<ScaleIOStoragePool> pools = getProtectionDomainStoragePools(domain.getId());
            for (ScaleIOStoragePool pool : pools) {
                String poolName = pool.getName();
                String id = pool.getId();
                ScaleIOStoragePool poolResult = getStoragePoolStats(id);
                result.addProtectionDomainStoragePool(domainName, id);
                result.addStoragePoolProperty(domainName, id, ScaleIOContants.POOL_AVAILABLE_CAPACITY,
                        poolResult.getCapacityAvailableForVolumeAllocationInKb());
                result.addStoragePoolProperty(domainName, id, ScaleIOContants.SCALEIO_TOTAL_CAPACITY,
                        poolResult.getMaxCapacityInKb());
                result.addStoragePoolProperty(domainName, id, ScaleIOContants.POOL_ALLOCATED_VOLUME_COUNT,
                        poolResult.getNumOfVolumes());
                result.addStoragePoolProperty(domainName, id, ScaleIOContants.NAME, poolName);
            }
        }
        return result;
    }

    @Override
    public ScaleIOQueryAllSDCResult queryAllSDC() throws JSONException {
        ClientResponse response = get(URI.create(ScaleIOContants.GET_SDC_URI));
        List<ScaleIOSdc> sdcList = getResponseObjects(ScaleIOSdc.class, response);
        ScaleIOQueryAllSDCResult result = new ScaleIOQueryAllSDCResult();
        for (ScaleIOSdc sdc : sdcList) {
            result.addClient(sdc.getId(), sdc.getSdcIp(), sdc.getMdmConnectionState(), sdc.getSdcGuid());
        }
        return result;
    }

    @Override
    public ScaleIOQueryAllSDSResult queryAllSDS() throws Exception {
        log.info("Discoverying all SDS.");
        ClientResponse response = get(URI.create(ScaleIOContants.GET_SDS_URI));
        List<ScaleIOSds> sdsList = getResponseObjects(ScaleIOSds.class, response);
        ScaleIOQueryAllSDSResult result = new ScaleIOQueryAllSDSResult();
        for (ScaleIOSds sds : sdsList) {
            List<Ip> ips = sds.getIpList();
            String sdsIp = null;
            if (ips != null && ips.size() > 0) {
                sdsIp = ips.get(0).getIp();
            }
            String domainId = sds.getProtectionDomainId();
            String domainName = protectionDomainMap.get(domainId);
            result.addProtectionDomain(domainId, domainName);
            result.addSDS(sds.getProtectionDomainId(), sds.getId(), sds.getName(), sdsIp, sds.getPort());

        }
        return result;
    }

    @Override
    public ScaleIOQueryClusterResult queryClusterCommand() throws Exception {
        ClientResponse response = get(URI.create(ScaleIOContants.GET_SYSTEMS_URI));
        List<ScaleIOSystem> systemsInfo = getResponseObjects(ScaleIOSystem.class,
                response);
        ScaleIOSystem system = systemsInfo.get(0);
        return system.toQueryClusterResult();
    }

    @Override
    public ScaleIOQueryStoragePoolResult queryStoragePool(
            String protectionDomainId, String poolId) throws Exception {
        ClientResponse response = get(URI.create(ScaleIOContants.getStoragePoolStatsURI(poolId)));
        ScaleIOStoragePool pool = getResponseObject(ScaleIOStoragePool.class, response);
        ScaleIOStoragePool poolStats = getStoragePoolStats(poolId);
        pool.setCapacityAvailableForVolumeAllocationInKb(poolStats.getCapacityAvailableForVolumeAllocationInKb());
        pool.setMaxCapacityInKb(poolStats.getMaxCapacityInKb());
        return pool.toQueryStoragePoolResult();
    }

    @Override
    public ScaleIOAddVolumeResult addVolume(String protectionDomainId,
            String storagePoolId, String volumeName, String volumeSize) throws Exception {
        ScaleIOCreateVolume volume = new ScaleIOCreateVolume();
        Long sizeInKb = Long.parseLong(volumeSize) / 1024L;
        volume.setVolumeSizeInKb(sizeInKb.toString());
        volume.setStoragePoolId(storagePoolId);
        volume.setName(volumeName);
        volume.setProtectionDomainId(protectionDomainId);
        ClientResponse response = post(URI.create(ScaleIOContants.VOLUMES_URI), getJsonForEntity(volume));
        ScaleIOVolume createdVol = getResponseObject(ScaleIOVolume.class, response);
        ScaleIOVolume vol = queryVolume(createdVol.getId());
        ScaleIOAddVolumeResult result = new ScaleIOAddVolumeResult();
        result.setActualSize(vol.getSizeInKb());
        result.setId(vol.getId());
        result.setIsSuccess(true);
        result.setName(vol.getName());
        result.setRequestedSize(volumeSize);
        result.setIsThinlyProvisioned(vol.isThinProvisioned());
        return result;

    }

    @Override
    public ScaleIOAddVolumeResult addVolume(String protectionDomainId,
            String storagePoolId, String volumeName, String volumeSize,
            boolean thinProvisioned) throws Exception {
        ScaleIOCreateVolume volume = new ScaleIOCreateVolume();
        Long sizeInKb = Long.parseLong(volumeSize) / 1024L;
        volume.setVolumeSizeInKb(sizeInKb.toString());
        volume.setStoragePoolId(storagePoolId);
        volume.setName(volumeName);
        volume.setProtectionDomainId(protectionDomainId);
        if (thinProvisioned) {
            volume.setVolumeType(ScaleIOContants.THIN_PROVISIONED);
        } else {
            volume.setVolumeType(ScaleIOContants.THICK_PROVISIONED);
        }
        ClientResponse response = post(URI.create(ScaleIOContants.VOLUMES_URI), getJsonForEntity(volume));
        ScaleIOVolume createdVol = getResponseObject(ScaleIOVolume.class, response);
        ScaleIOVolume vol = queryVolume(createdVol.getId());
        ScaleIOAddVolumeResult result = new ScaleIOAddVolumeResult();
        String size = vol.getSizeInKb();
        Long sizeInGB = Long.parseLong(size) / (1024L * 1024L);
        result.setActualSize(sizeInGB.toString());
        result.setId(vol.getId());
        result.setIsSuccess(true);
        result.setName(vol.getName());
        result.setRequestedSize(volumeSize);
        result.setIsThinlyProvisioned(vol.isThinProvisioned());
        return result;
    }

    @Override
    public ScaleIORemoveVolumeResult removeVolume(String volumeId) throws Exception {
        String uri = ScaleIOContants.getRemoveVolumeURI(volumeId);
        ScaleIORemoveVolume removeParm = new ScaleIORemoveVolume();
        post(URI.create(uri), getJsonForEntity(removeParm));
        ScaleIORemoveVolumeResult result = new ScaleIORemoveVolumeResult();
        result.setIsSuccess(true);
        return result;
    }

    @Override
    public ScaleIOModifyVolumeCapacityResult modifyVolumeCapacity(
            String volumeId, String newSizeGB) throws Exception {
        String uri = ScaleIOContants.getModifyVolumeSizeURI(volumeId);
        ScaleIOModifyVolumeSize modifyParm = new ScaleIOModifyVolumeSize();
        modifyParm.setSizeInGB(newSizeGB);
        post(URI.create(uri), getJsonForEntity(modifyParm));
        ScaleIOModifyVolumeCapacityResult result = new ScaleIOModifyVolumeCapacityResult();
        result.setIsSuccess(true);
        return result;
    }

    @Override
    public ScaleIOSnapshotVolumeResult snapshotVolume(String volId, String snapshot, String systemId) throws Exception {
        String uri = ScaleIOContants.getSnapshotVolumesURI(systemId);
        ScaleIOSnapshotVolumes spVol = new ScaleIOSnapshotVolumes();
        spVol.addSnapshot(volId, snapshot);
        ClientResponse response = post(URI.create(uri), getJsonForEntity(spVol));
        ScaleIOSnapshotVolumeResponse snapVolumeInfo = getResponseObject(ScaleIOSnapshotVolumeResponse.class, response);
        ScaleIOSnapshotVolumeResult result = new ScaleIOSnapshotVolumeResult();
        result.setIsSuccess(true);
        result.setId(snapVolumeInfo.getVolumeIdList().get(0));
        return result;
    }

    @Override
    public ScaleIOSnapshotMultiVolumeResult snapshotMultiVolume(Map<String, String> id2snapshot, String systemId) throws Exception {
        String uri = ScaleIOContants.getSnapshotVolumesURI(systemId);
        ScaleIOSnapshotVolumes spVol = new ScaleIOSnapshotVolumes();
        for (Map.Entry<String, String> entry : id2snapshot.entrySet()) {
            spVol.addSnapshot(entry.getKey(), entry.getValue());
        }
        ClientResponse response = post(URI.create(uri), getJsonForEntity(spVol));
        ScaleIOSnapshotVolumeResponse snapVolumeInfo = getResponseObject(ScaleIOSnapshotVolumeResponse.class, response);
        Map<String, String> snapshotMap = getVolumes(snapVolumeInfo.getVolumeIdList());
        ScaleIOSnapshotMultiVolumeResult result = new ScaleIOSnapshotMultiVolumeResult();
        result.setIsSuccess(true);
        result.setConsistencyGroupId(snapVolumeInfo.getSnapshotGroupId());
        for (Map.Entry<String, String> entry : snapshotMap.entrySet()) {
            ScaleIOSnapshotVolumeResult snapResult = new ScaleIOSnapshotVolumeResult();
            snapResult.setId(entry.getValue());
            snapResult.setName(entry.getKey());
            snapResult.setSuccess(true);
            result.addResult(snapResult);

        }
        return result;
    }

    @Override
    public ScaleIOMapVolumeToSDCResult mapVolumeToSDC(String volumeId,
            String sdcId) throws Exception {
        ScaleIOMapVolumeToSDCResult result = new ScaleIOMapVolumeToSDCResult();
        log.info("mapping to sdc");
        try {
            String uri = ScaleIOContants.getMapVolumeToSDCURI(volumeId);
            ScaleIOMapVolumeToSDC mapParm = new ScaleIOMapVolumeToSDC();
            mapParm.setSdcId(sdcId);
            mapParm.setAllowMultipleMappings("TRUE");
            post(URI.create(uri), getJsonForEntity(mapParm));
            result.setIsSuccess(true);
        } catch (ScaleIOException e) {
            log.info(e.getMessage());
            result.setErrorString(e.getMessage());
            result.setIsSuccess(false);
        }
        return result;
    }

    @Override
    public ScaleIOUnMapVolumeToSDCResult unMapVolumeToSDC(String volumeId,
            String sdcId) throws Exception {
        String uri = ScaleIOContants.getUnmapVolumeToSDCURI(volumeId);
        ScaleIOUnmapVolumeToSDC unmapParm = new ScaleIOUnmapVolumeToSDC();
        unmapParm.setSdcId(sdcId);
        unmapParm.setIgnoreScsiInitiators("TRUE");
        post(URI.create(uri), getJsonForEntity(unmapParm));
        ScaleIOUnMapVolumeToSDCResult result = new ScaleIOUnMapVolumeToSDCResult();
        result.setIsSuccess(true);
        return result;
    }

    @Override
    public ScaleIORemoveConsistencyGroupSnapshotsResult removeConsistencyGroupSnapshot(
            String consistencyGroupId) throws Exception {
        String systemId = getSystemId();
        String uri = ScaleIOContants.getRemoveConsistencyGroupSnapshotsURI(systemId);
        ScaleIORemoveConsistencyGroupSnapshots parm = new ScaleIORemoveConsistencyGroupSnapshots();
        parm.setSnapGroupId(consistencyGroupId);
        post(URI.create(uri), getJsonForEntity(parm));
        ScaleIORemoveConsistencyGroupSnapshotsResult result = new ScaleIORemoveConsistencyGroupSnapshotsResult();
        result.setIsSuccess(true);
        return result;
    }

    @Override
    public ScaleIOQueryAllSCSIInitiatorsResult queryAllSCSIInitiators() throws JSONException {
        log.info("Discovery all SCSI Initiators");
        ClientResponse response = get(URI.create(ScaleIOContants.GET_SCSI_INITIATOR_URI));
        List<ScaleIOScsiInitiator> initList = getResponseObjects(ScaleIOScsiInitiator.class, response);
        ScaleIOQueryAllSCSIInitiatorsResult result = new ScaleIOQueryAllSCSIInitiatorsResult();
        if (initList != null && !initList.isEmpty()) {
            for (ScaleIOScsiInitiator init : initList) {
                result.addInitiator(init.getId(), init.getName(), "", init.getIqn());
            }
        }
        return result;
    }

    @Override
    public ScaleIOMapVolumeToSCSIInitiatorResult mapVolumeToSCSIInitiator(
            String volumeId, String initiatorId) throws Exception {
        ScaleIOMapVolumeToSCSIInitiatorResult result = new ScaleIOMapVolumeToSCSIInitiatorResult();
        log.info("mapping to scsi");
        try {
            String uri = ScaleIOContants.getMapVolumeToScsiInitiatorURI(volumeId);
            ScaleIOMapVolumeToScsiInitiator mapParm = new ScaleIOMapVolumeToScsiInitiator();
            mapParm.setScsiInitiatorId(initiatorId);
            mapParm.setAllowMultipleMapp("TRUE");
            post(URI.create(uri), getJsonForEntity(mapParm));
            result.setIsSuccess(true);
        } catch (ScaleIOException e) {
            result.setErrorString(e.getMessage());
            result.setIsSuccess(false);
        }
        return result;
    }

    @Override
    public ScaleIOUnMapVolumeFromSCSIInitiatorResult unMapVolumeFromSCSIInitiator(
            String volumeId, String initiatorId) throws Exception {
        String uri = ScaleIOContants.getUnmapVolumeToScsiInitiatorURI(volumeId);
        ScaleIOUnmapVolumeToScsiInitiator unmapParm = new ScaleIOUnmapVolumeToScsiInitiator();
        unmapParm.setScsiInitiatorId(initiatorId);
        post(URI.create(uri), getJsonForEntity(unmapParm));
        ScaleIOUnMapVolumeFromSCSIInitiatorResult result = new ScaleIOUnMapVolumeFromSCSIInitiatorResult();
        result.setIsSuccess(true);
        return result;
    }

    @Override
    public String getVersion() {
        String result = null;
        ClientResponse response = get(URI.create(ScaleIOContants.GET_SYSTEMS_URI));
        try {
            List<ScaleIOSystem> systemsInfo = getResponseObjects(ScaleIOSystem.class,
                    response);
            ScaleIOSystem system = systemsInfo.get(0);
            result = system.getVersion();
        } catch (Exception e) {
            log.error("Exception while getting version", e);
        }
        return result;
    }

    @Override
    protected Builder setResourceHeaders(WebResource resource) {
        return resource.getRequestBuilder();
    }

    @Override
    protected void authenticate() {
        log.info("Authenticating");
        try {
            _client.removeAllFilters();
            _client.addFilter(new HTTPBasicAuthFilter(_username, _password));
            if (log.isDebugEnabled()) {
                _client.addFilter(new LoggingFilter(System.out));
            }
            URI requestURI = _base.resolve(URI.create(ScaleIOContants.API_LOGIN));
            ClientResponse response = _client.resource(requestURI).type(MediaType.APPLICATION_JSON)
                    .get(ClientResponse.class);

            if (response.getClientResponseStatus() != ClientResponse.Status.OK
                    && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
                throw ScaleIOException.exceptions.authenticationFailure(_base.toString());
            }
            _authToken = response.getEntity(String.class).replace("\"", "");
            _client.removeAllFilters();
            _client.addFilter(new HTTPBasicAuthFilter(_username, _authToken));
            if (log.isDebugEnabled()) {
                _client.addFilter(new LoggingFilter(System.out));
            }
        } catch (Exception e) {
            ScaleIOException.exceptions.authenticationFailure(_base.toString());
        }

    }

    @Override
    protected int checkResponse(URI uri, ClientResponse response) {
        ClientResponse.Status status = response.getClientResponseStatus();
        int errorCode = status.getStatusCode();
        if (errorCode >= 300) {
            JSONObject obj = null;
            int code = 0;
            try {
                obj = response.getEntity(JSONObject.class);
                code = obj.getInt(ScaleIOContants.ERROR_CODE);
            } catch (Exception e) {
                log.error("Parsing the failure response object failed", e);
            }

            if (code == 404 || code == 410) {
                throw ScaleIOException.exceptions.resourceNotFound(uri.toString());
            } else if (code == 401) {
                throw ScaleIOException.exceptions.authenticationFailure(uri.toString());
            } else {
                throw ScaleIOException.exceptions.internalError(uri.toString(), obj.toString());
            }
        } else {
            return errorCode;
        }
    }

    private <T> List<T> getResponseObjects(Class<T> clazz, ClientResponse response) throws JSONException {
        JSONArray resp = response.getEntity(JSONArray.class);
        List<T> result = null;
        if (resp != null && resp.length() > 0) {
            result = new ArrayList<T>();
            for (int i = 0; i < resp.length(); i++) {
                JSONObject entry = resp.getJSONObject(i);
                T respObject = new Gson().fromJson(entry.toString(), clazz);
                result.add(respObject);
            }
        }
        return result;
    }

    private ScaleIOSystem getSystem() throws Exception {
        ClientResponse response = get(URI.create(ScaleIOContants.GET_SYSTEMS_URI));
        List<ScaleIOSystem> systemsInfo = getResponseObjects(ScaleIOSystem.class, response);
        return systemsInfo.get(0);
    }

    private List<ScaleIOProtectionDomain> getProtectionDomains() throws JSONException {
        ClientResponse response = get(URI.create(ScaleIOContants.GET_PROTECTION_DOMAIN_URI));
        return getResponseObjects(ScaleIOProtectionDomain.class, response);
    }

    private List<ScaleIOStoragePool> getProtectionDomainStoragePools(String pdId) throws JSONException {
        ClientResponse response = get(URI.create(ScaleIOContants.getProtectionDomainStoragePoolURI(pdId)));
        return getResponseObjects(ScaleIOStoragePool.class, response);
    }

    private ScaleIOStoragePool getStoragePoolStats(String poolId) throws Exception {
        ClientResponse response = get(URI.create(ScaleIOContants.getStoragePoolStatsURI(poolId)));
        ScaleIOStoragePool pool = getResponseObject(ScaleIOStoragePool.class, response);
        pool.setId(poolId);
        return pool;
    }

    @Override
    public String getSystemId() throws Exception {
        ClientResponse response = get(URI.create(ScaleIOContants.GET_SYSTEMS_URI));
        List<ScaleIOSystem> systemsInfo = getResponseObjects(ScaleIOSystem.class, response);
        return systemsInfo.get(0).getId();
    }

    @Override
    public ScaleIOVolume queryVolume(String volId) throws Exception {
        ClientResponse response = get(URI.create(ScaleIOContants.getVolumeURI(volId)));
        return getResponseObject(ScaleIOVolume.class, response);
    }

    public Map<String, String> getVolumes(List<String> volumeIds) throws Exception {
        Map<String, String> result = new HashMap<String, String>();
        ScaleIOVolumeList parm = new ScaleIOVolumeList();
        parm.setIds(volumeIds);
        ClientResponse response = post(URI.create(ScaleIOContants.GET_VOLUMES_BYIDS_URI), getJsonForEntity(parm));
        List<ScaleIOVolume> volumes = getResponseObjects(ScaleIOVolume.class, response);
        for (ScaleIOVolume volume : volumes) {
            result.put(volume.getName(), volume.getId());
        }
        return result;
    }
}
