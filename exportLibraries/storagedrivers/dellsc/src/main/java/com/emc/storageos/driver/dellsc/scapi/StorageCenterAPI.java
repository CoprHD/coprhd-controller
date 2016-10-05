/*
 * Copyright 2016 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.driver.dellsc.scapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.scapi.objects.ApiConnection;
import com.emc.storageos.driver.dellsc.scapi.objects.EmDataCollector;
import com.emc.storageos.driver.dellsc.scapi.objects.ScConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPort;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPortFibreChannelConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPortIscsiConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScCopyMirrorMigrate;
import com.emc.storageos.driver.dellsc.scapi.objects.ScFaultDomain;
import com.emc.storageos.driver.dellsc.scapi.objects.ScMapping;
import com.emc.storageos.driver.dellsc.scapi.objects.ScMappingProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScObject;
import com.emc.storageos.driver.dellsc.scapi.objects.ScPhysicalServer;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplay;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplayConsistencyGroup;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplayProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScServer;
import com.emc.storageos.driver.dellsc.scapi.objects.ScServerHba;
import com.emc.storageos.driver.dellsc.scapi.objects.ScServerOperatingSystem;
import com.emc.storageos.driver.dellsc.scapi.objects.ScStorageType;
import com.emc.storageos.driver.dellsc.scapi.objects.ScStorageTypeStorageUsage;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolumeConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolumeStorageUsage;
import com.emc.storageos.driver.dellsc.scapi.objects.StorageCenter;
import com.emc.storageos.driver.dellsc.scapi.objects.StorageCenterStorageUsage;
import com.emc.storageos.driver.dellsc.scapi.rest.Parameters;
import com.emc.storageos.driver.dellsc.scapi.rest.PayloadFilter;
import com.emc.storageos.driver.dellsc.scapi.rest.PayloadFilter.ValueFilterType;
import com.emc.storageos.driver.dellsc.scapi.rest.RestClient;
import com.emc.storageos.driver.dellsc.scapi.rest.RestResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * API client for managing Storage Center via DSM.
 */
public class StorageCenterAPI implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(StorageCenterAPI.class);

    private static final String NOTES_STRING = "Created by CoprHD driver";

    private RestClient restClient;
    private Gson gson;
    private String host;

    private StorageCenterAPI(String host, int port, String user, String password)
            throws StorageCenterAPIException {
        LOG.debug("{} {} {}", host, port, user);
        restClient = new RestClient(host, port, user, password);
        gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssX").create();

        Parameters params = new Parameters();
        params.add("Application", "CoprHD Driver");
        params.add("ApplicationVersion", "1.0");

        RestResult result = restClient.post("/ApiConnection/Login", params.toJson());
        if (result.getResponseCode() != 200) {
            throw new StorageCenterAPIException(result.getErrorMsg(), result.getResponseCode());
        }

        ApiConnection connection = gson.fromJson(result.getResult(), ApiConnection.class);
        LOG.debug("Connected to {} {} {}", connection.hostName, connection.provider, connection.providerVersion);
        this.host = host;
    }

    /**
     * Gets the connection host name.
     * 
     * @return The host name.
     */
    public String getHost() {
        return host;
    }

    /**
     * Get a Storage Center API connection.
     *
     * @param host The DSM host name or IP address.
     * @param port The DSM port.
     * @param user The user to connect as.
     * @param password The password.
     * @return The Storage Center API connection.
     * @throws StorageCenterAPIException the storage center API exception
     */
    public static StorageCenterAPI openConnection(String host, int port, String user, String password)
            throws StorageCenterAPIException {
        return new StorageCenterAPI(host, port, user, password);
    }

    /**
     * Close API connection.
     */
    public void closeConnection() {
        // Log out of the API
        restClient.post("/ApiConnection/Logout", "{}");
        restClient = null;
    }

    @Override
    public void close() {
        this.closeConnection();
    }

    @SuppressWarnings("unchecked")
    private boolean checkResults(RestResult result) {
        if (result.getResponseCode() >= 200 &&
                result.getResponseCode() < 300) {
            return true;
        }

        // Some versions return the reason text, some return a JSON structure
        String text = result.getResult();
        if (text.startsWith("{\"")) {
            Map<String, String> resultJson = new HashMap<>(0);
            resultJson = gson.fromJson(text, resultJson.getClass());
            text = resultJson.get("result");
        }

        LOG.warn("REST call result:\n\tURL:         {}\n\tStatus Code: {}\n\tReason:      {}\n\tText:        {}",
                result.getUrl(), result.getResponseCode(), result.getErrorMsg(), text);
        result.setErrorMsg(text);
        return false;
    }

    /**
     * Get all Storage Centers managed by this DSM instance.
     *
     * @return Collection of Storage Centers.
     */
    public StorageCenter[] getStorageCenterInfo() {
        RestResult result = restClient.get("/StorageCenter/StorageCenter");

        try {
            return gson.fromJson(result.getResult(), StorageCenter[].class);
        } catch (Exception e) {
            LOG.warn("Error getting Storage Center info results: {}", e);
        }
        return new StorageCenter[0];
    }

    /**
     * Find a specific Storage Center.
     *
     * @param ssn The Storage Center serial number.
     * @return The Storage Center.
     * @throws StorageCenterAPIException if the Storage Center is not found.
     */
    public StorageCenter findStorageCenter(String ssn) throws StorageCenterAPIException {
        StorageCenter[] scs = getStorageCenterInfo();
        for (StorageCenter sc : scs) {
            if (ssn.equals(sc.scSerialNumber)) {
                return sc;
            }
        }

        throw new StorageCenterAPIException(String.format("Unable to locate Storage Center %s", ssn));
    }

    /**
     * Creates a volume on the Storage Center.
     *
     * @param ssn The Storage Center SN on which to create the volume.
     * @param name The volume name.
     * @param storageType The storage type to use.
     * @param sizeInGB The size in GB
     * @param cgID The consistency group ID to add volume to or null.
     * @return The Storage Center volume.
     * @throws StorageCenterAPIException
     */
    public ScVolume createVolume(String ssn, String name, String storageType, int sizeInGB, String cgID) throws StorageCenterAPIException {
        LOG.debug("Creating {}GB volume: '{}'", sizeInGB, name);
        String errorMessage = "";

        Parameters params = new Parameters();
        params.add("Name", name);
        params.add("Notes", NOTES_STRING);
        params.add("Size", String.format("%d MB", sizeInGB));
        params.add("StorageCenter", ssn);
        if (cgID != null && !cgID.isEmpty()) {
            String[] ids = { cgID };
            params.add("ReplayProfileList", ids);
        }

        ScStorageType[] storageTypes = getStorageTypes(ssn);
        for (ScStorageType storType : storageTypes) {
            if (storType.name.equals(storageType)) {
                params.add("StorageType", storType.instanceId);
                break;
            }
        }

        try {
            RestResult result = restClient.post("StorageCenter/ScVolume", params.toJson());
            if (checkResults(result)) {
                return gson.fromJson(result.getResult(), ScVolume.class);
            }
        } catch (Exception e) {
            errorMessage = String.format("Error creating volume: %s", e);
            LOG.warn(errorMessage);
        }

        if (errorMessage.length() == 0) {
            errorMessage = String.format("Unable to create volume %s on SC %s", name, ssn);
        }
        throw new StorageCenterAPIException(errorMessage);
    }

    /**
     * Gets a volume.
     *
     * @param instanceId the instance id of the volume.
     * @return the volume or null if not found
     */
    public ScVolume getVolume(String instanceId) {
        ScVolume result = null;

        if (instanceId != null) {
            RestResult rr = restClient.get(String.format("StorageCenter/ScVolume/%s", instanceId));
            if (checkResults(rr)) {
                result = gson.fromJson(rr.getResult(), ScVolume.class);
            }
        }

        return result;
    }

    /**
     * Delete a volume.
     *
     * If the volume can't be found we return success assuming the volume has been deleted
     * by some other means.
     *
     * @param instanceId the instance id of the volume.
     * @throws StorageCenterAPIException if error encountered deleting the volume.
     */
    public void deleteVolume(String instanceId) throws StorageCenterAPIException {
        ScVolume vol = getVolume(instanceId);

        if (vol == null) {
            LOG.warn("Volume delete request for {}, volume not found. Assuming deleted.", instanceId);
            return;
        }

        RestResult rr = restClient.delete(String.format("StorageCenter/ScVolume/%s", instanceId));
        if (!checkResults(rr)) {
            String msg = String.format("Error deleting volume %s", instanceId);
            LOG.error(msg);
            throw new StorageCenterAPIException(msg);
        }
    }

    /**
     * Find an initiator WWN or iSCSI IQN.
     * 
     * @param ssn The Storage Center SN on which to check.
     * @param iqnOrWwn The FC WWN or iSCSI IQN.
     * @return The HBA object.
     */
    private ScServerHba findServerHba(String ssn, String iqnOrWwn) {
        ScServerHba result = null;

        PayloadFilter filter = new PayloadFilter();
        filter.append("scSerialNumber", ssn);
        filter.append("instanceName", iqnOrWwn);

        RestResult rr = restClient.post("StorageCenter/ScServerHba/GetList", filter.toJson());
        if (checkResults(rr)) {
            ScServerHba[] hbas = gson.fromJson(rr.getResult(), ScServerHba[].class);
            for (ScServerHba hba : hbas) {
                // Should only return one if found, but just grab first from list
                result = hba;
                break;
            }
        }

        return result;
    }

    /**
     * Find a server definition.
     * 
     * @param ssn The Storage Center SN on which to check.
     * @param iqnOrWwn The WWN or IQN.
     * @return The server definition.
     */
    public ScServer findServer(String ssn, String iqnOrWwn) {
        ScServer result = null;
        ScServerHba hba = findServerHba(ssn, iqnOrWwn);

        // If the initiator has been seen but never used to define a server it will have a null server
        if (hba != null && hba.server != null) {
            RestResult rr = restClient.get(String.format("StorageCenter/ScServer/%s", hba.server.instanceId));
            if (checkResults(rr)) {
                result = gson.fromJson(rr.getResult(), ScServer.class);
            }
        }

        return result;
    }

    /**
     * Gets all server definitions on the array.
     * 
     * @param ssn The Storage Center system serial number.
     * @return The server definitions.
     */
    public ScServer[] getServerDefinitions(String ssn) {
        PayloadFilter filter = new PayloadFilter();
        filter.append("scSerialNumber", ssn);

        RestResult rr = restClient.post("StorageCenter/ScServer/GetList", filter.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScServer[].class);
        }

        return new ScServer[0];
    }

    /**
     * Gets a specific server definition.
     *
     * @param instanceId The server instance ID.
     * @return The server or null if not found.
     */
    public ScServer getServerDefinition(String instanceId) {
        RestResult rr = restClient.get(String.format("StorageCenter/ScServer/%s", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScServer.class);
        }

        return null;
    }

    /**
     * Gets a specific physical server definition.
     *
     * @param instanceId The server instance ID.
     * @return The server or null if not found.
     */
    public ScPhysicalServer getPhysicalServerDefinition(String instanceId) {
        RestResult rr = restClient.get(String.format("StorageCenter/ScPhysicalServer/%s", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScPhysicalServer.class);
        }

        return null;
    }

    /**
     * Get all storage types from the system.
     * 
     * @param ssn The Storage Center system serial number.
     * @return The storage types.
     */
    public ScStorageType[] getStorageTypes(String ssn) {

        PayloadFilter filter = new PayloadFilter();
        if (ssn != null) {
            filter.append("scSerialNumber", ssn);
        }

        RestResult rr = restClient.post("StorageCenter/ScStorageType/GetList", filter.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScStorageType[].class);
        }

        return new ScStorageType[0];
    }

    /**
     * Gets a specific storage type.
     *
     * @param instanceId The storage type instance ID.
     * @return The storage type.
     */
    public ScStorageType getStorageType(String instanceId) {
        RestResult rr = restClient.get(
                String.format("StorageCenter/ScStorageType/%s", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScStorageType.class);
        }

        return null;
    }

    /**
     * Gets the storage usage information for a storage type.
     *
     * @param instanceId The storage type to get.
     * @return The storage usage.
     */
    public ScStorageTypeStorageUsage getStorageTypeStorageUsage(String instanceId) {
        RestResult rr = restClient.get(String.format("StorageCenter/ScStorageType/%s/StorageUsage", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScStorageTypeStorageUsage.class);
        }

        return new ScStorageTypeStorageUsage();
    }

    /**
     * Gets the Storage Center usage data.
     * 
     * @param ssn The Storage Center system serial number.
     * @return The storage usage.
     */
    public StorageCenterStorageUsage getStorageUsage(String ssn) {
        RestResult rr = restClient.get(String.format("StorageCenter/StorageCenter/%s/StorageUsage", ssn));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), StorageCenterStorageUsage.class);
        }

        return new StorageCenterStorageUsage();
    }

    /**
     * Gets all controller target ports.
     * 
     * @param ssn The Storage Center serial number.
     * @param type The type of port to get or Null for all types.
     * @return The controller ports.
     */
    public ScControllerPort[] getTargetPorts(String ssn, String type) {
        PayloadFilter filter = new PayloadFilter();
        filter.append("scSerialNumber", ssn);
        if (type != null && type.length() > 0) {
            filter.append("transportType", type);
        }

        RestResult rr = restClient.post("StorageCenter/ScControllerPort/GetList", filter.toJson());
        if (checkResults(rr)) {
            List<ScControllerPort> ports = new ArrayList<>();
            ScControllerPort[] scPorts = gson.fromJson(rr.getResult(), ScControllerPort[].class);
            for (ScControllerPort port : scPorts) {
                if (port.purpose.startsWith("FrontEnd") && !"FrontEndReserved".equals(port.purpose)) {
                    ports.add(port);
                }
            }

            return ports.toArray(new ScControllerPort[0]);
        }

        return new ScControllerPort[0];
    }

    /**
     * Gets all iSCSI target ports.
     * 
     * @param ssn The Storage Center serial number.
     * @return The iSCSI controller ports.
     */
    public ScControllerPort[] getIscsiTargetPorts(String ssn) {
        return getTargetPorts(ssn, ScControllerPort.ISCSI_TRANSPORT_TYPE);
    }

    /**
     * Gets all fibre channel target ports.
     *
     * @param ssn The Storage Center serial number.
     * @return The FC controller ports.
     */
    public ScControllerPort[] getFcTargetPorts(String ssn) {
        return getTargetPorts(ssn, ScControllerPort.FC_TRANSPORT_TYPE);
    }

    /**
     * Gets all defined HBAs for a server definition.
     * 
     * @param ssn The Storage Center serial number.
     * @param serverInstanceId The server definition.
     * @return The HBAs.
     */
    public ScServerHba[] getServerHbas(String ssn, String serverInstanceId) {
        PayloadFilter filter = new PayloadFilter();
        filter.append("scSerialNumber", ssn);
        filter.append("Server", serverInstanceId);

        RestResult rr = restClient.post("StorageCenter/ScServerHba/GetList", filter.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScServerHba[].class);
        }

        return new ScServerHba[0];
    }

    /**
     * Gets a server HBA.
     *
     * @param instanceId The server HBA instance ID.
     * @return The server HBA.
     */
    public ScServerHba getServerHba(String instanceId) {
        RestResult rr = restClient.get(
                String.format("StorageCenter/ScServerHba/%s", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScServerHba.class);
        }

        return null;
    }

    /**
     * Get the fault domains configured for a port.
     * 
     * @param instanceId The port instance ID.
     * @return The fault domains.
     */
    public ScFaultDomain[] getControllerPortFaultDomains(String instanceId) {
        RestResult rr = restClient.get(
                String.format("StorageCenter/ScControllerPort/%s/FaultDomainList", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScFaultDomain[].class);
        }

        return new ScFaultDomain[0];
    }

    /**
     * Gets configuration about an iSCSI controller port.
     * 
     * @param instanceId The port instance ID.
     * @return The port configuration.
     */
    public ScControllerPortIscsiConfiguration getControllerPortIscsiConfig(String instanceId) {
        RestResult rr = restClient.get(
                String.format("/StorageCenter/ScControllerPort/%s/ControllerPortConfiguration", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScControllerPortIscsiConfiguration.class);
        }

        return new ScControllerPortIscsiConfiguration();
    }

    /**
     * Gets configuration about a fibre channel controller port.
     * 
     * @param instanceId The port instance ID.
     * @return The port configuration.
     */
    public ScControllerPortFibreChannelConfiguration getControllerPortFCConfig(String instanceId) {
        RestResult rr = restClient.get(
                String.format("/StorageCenter/ScControllerPort/%s/ControllerPortConfiguration", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScControllerPortFibreChannelConfiguration.class);
        }

        return new ScControllerPortFibreChannelConfiguration();
    }

    /**
     * Expand a volume to a larger size.
     * 
     * @param instanceId The volume instance ID.
     * @param newSize The new size.
     * @return The ScVolume object.
     * @throws StorageCenterAPIException
     */
    public ScVolume expandVolume(String instanceId, int newSize) throws StorageCenterAPIException {
        LOG.debug("Expanding volume '{}' to {}GB", instanceId, newSize);

        Parameters params = new Parameters();
        params.add("NewSize", String.format("%d MB", newSize));

        try {
            RestResult result = restClient.post(
                    String.format("StorageCenter/ScVolume/%s/ExpandToSize", instanceId),
                    params.toJson());
            if (checkResults(result)) {
                return gson.fromJson(result.getResult(), ScVolume.class);
            }

            throw new StorageCenterAPIException(
                    String.format("Failed to expande volume: %s", result.getErrorMsg()));
        } catch (Exception e) {
            LOG.warn(String.format("Error expanding volume: %s", e));
            throw new StorageCenterAPIException("Error expanding volume", e);
        }
    }

    /**
     * Gets all volumes.
     *
     * @param ssn The Storage Center to query from.
     * @return The volumes on the requested SC.
     */
    public ScVolume[] getAllVolumes(String ssn) {

        PayloadFilter filter = new PayloadFilter();
        filter.append("scSerialNumber", ssn);

        RestResult rr = restClient.post("StorageCenter/ScVolume/GetList", filter.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScVolume[].class);
        }

        return new ScVolume[0];
    }

    /**
     * Gets all replays for a given volume.
     *
     * @param instanceId The volume instance ID.
     * @return The volume's replays.
     */
    public ScReplay[] getVolumeSnapshots(String instanceId) {

        PayloadFilter filter = new PayloadFilter();
        filter.append("createVolume", instanceId);
        filter.append("active", false);
        filter.append("markedForExpiration", false);

        RestResult rr = restClient.post("StorageCenter/ScReplay/GetList", filter.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScReplay[].class);
        }

        return new ScReplay[0];
    }

    /**
     * Gets the storage consumption information for a volume.
     *
     * @param instanceId The volume instance ID.
     * @return The storage usage information.
     * @throws StorageCenterAPIException
     */
    public ScVolumeStorageUsage getVolumeStorageUsage(String instanceId) throws StorageCenterAPIException {

        RestResult rr = restClient.get(String.format("StorageCenter/ScVolume/%s/StorageUsage", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScVolumeStorageUsage.class);
        }

        String message = "Unknown";
        if (rr.getErrorMsg().length() > 0) {
            message = rr.getErrorMsg();
        }
        message = String.format("Error getting storage usage for volume %s: %s", instanceId, message);

        throw new StorageCenterAPIException(message);
    }

    /**
     * Creates a new consistency group on the Storage Center.
     *
     * @param ssn The Storage Center on which to create the CG.
     * @param name The name of the CG.
     * @return The created consistency group.
     * @throws StorageCenterAPIException
     */
    public ScReplayProfile createConsistencyGroup(String ssn, String name) throws StorageCenterAPIException {
        LOG.debug("Creating consistency group '{}'", name);
        String errorMessage = "";

        Parameters params = new Parameters();
        params.add("Name", name);
        params.add("Notes", NOTES_STRING);
        params.add("Type", "Consistent");
        params.add("StorageCenter", ssn);

        try {
            RestResult result = restClient.post("StorageCenter/ScReplayProfile", params.toJson());
            if (checkResults(result)) {
                return gson.fromJson(result.getResult(), ScReplayProfile.class);
            }

            errorMessage = String.format(
                    "Unable to create CG %s on SC %s: %s", name, ssn, result.getErrorMsg());
        } catch (Exception e) {
            errorMessage = String.format("Error creating consistency group: %s", e);
            LOG.warn(errorMessage);
        }

        if (errorMessage.length() == 0) {
            errorMessage = String.format("Unable to create CG %s on SC %s", name, ssn);
        }
        throw new StorageCenterAPIException(errorMessage);
    }

    /**
     * Deletes a consistency group.
     *
     * @param instanceId The replay profile instance ID.
     * @throws StorageCenterAPIException
     */
    public void deleteConsistencyGroup(String instanceId) throws StorageCenterAPIException {
        LOG.debug("Deleting consistency group '{}'", instanceId);

        RestResult rr = restClient.delete(String.format("StorageCenter/ScReplayProfile/%s", instanceId));
        if (!checkResults(rr)) {
            String msg = String.format("Error deleting CG %s: %s", instanceId, rr.getErrorMsg());
            LOG.error(msg);
            throw new StorageCenterAPIException(msg);
        }
    }

    /**
     * Gets the volumes that are part of a consistency group.
     *
     * @param instanceId The CG ID.
     * @return The volumes.
     * @throws StorageCenterAPIException
     */
    public ScVolume[] getConsistencyGroupVolumes(String instanceId) throws StorageCenterAPIException {
        LOG.debug("Getting volume for consistency group {}", instanceId);

        RestResult rr = restClient.get(String.format("StorageCenter/ScReplayProfile/%s/VolumeList", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScVolume[].class);
        }

        throw new StorageCenterAPIException(rr.getErrorMsg());
    }

    /**
     * Create snapshots for the consistency group.
     *
     * @param instanceId The replay profile instance ID.
     * @return The replays created.
     * @throws StorageCenterAPIException
     */
    public ScReplay[] createConsistencyGroupSnapshots(String instanceId) throws StorageCenterAPIException {
        LOG.debug("Creating consistency group snapshots for '{}'", instanceId);

        // Get a random identifier that will fit in our description field
        String id = UUID.randomUUID().toString().substring(0, 31);
        Parameters params = new Parameters();
        params.add("description", id);
        params.add("expireTime", 0);

        RestResult rr = restClient.post(
                String.format("StorageCenter/ScReplayProfile/%s/CreateReplay", instanceId),
                params.toJson());
        if (!checkResults(rr)) {
            String msg = String.format("Error creating snapshots from CG %s: %s", instanceId, rr.getErrorMsg());
            LOG.error(msg);
            throw new StorageCenterAPIException(msg);
        }

        rr = restClient.get(
                String.format("StorageCenter/ScReplayProfile/%s/ConsistencyGroupList", instanceId));
        if (!checkResults(rr)) {
            String msg = String.format("Error getting consistent groups: %s", rr.getErrorMsg());
            LOG.warn(msg);
            throw new StorageCenterAPIException(msg);
        }

        ScReplayConsistencyGroup consistentGroup = null;
        ScReplayConsistencyGroup[] cgs = gson.fromJson(rr.getResult(), ScReplayConsistencyGroup[].class);
        for (ScReplayConsistencyGroup cg : cgs) {
            if (id.equals(cg.description)) {
                consistentGroup = cg;
            }
        }

        if (consistentGroup != null) {
            rr = restClient.get(
                    String.format(
                            "StorageCenter/ScReplayConsistencyGroup/%s/ReplayList",
                            consistentGroup.instanceId));
            if (checkResults(rr)) {
                return gson.fromJson(rr.getResult(), ScReplay[].class);
            }
        }

        throw new StorageCenterAPIException("Unable to get replays created for consistency group.");
    }

    /**
     * Gets all replay profiles that are consistent type.
     *
     * @param ssn The Storage Center serial number.
     * @return The consistent replay profiles.
     */
    public ScReplayProfile[] getConsistencyGroups(String ssn) {
        PayloadFilter filter = new PayloadFilter();
        filter.append("scSerialNumber", ssn);
        filter.append("type", "Consistent");

        RestResult rr = restClient.post("StorageCenter/ScReplayProfile/GetList", filter.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScReplayProfile[].class);
        }

        return new ScReplayProfile[0];
    }

    /**
     * Gets all volumes that are part of a replay profile.
     *
     * @param instanceId The replay profile instance ID.
     * @return The member volumes.
     * @throws StorageCenterAPIException
     */
    public ScVolume[] getReplayProfileVolumes(String instanceId) throws StorageCenterAPIException {

        RestResult rr = restClient.get(String.format("StorageCenter/ScReplayProfile/%s/VolumeList", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScVolume[].class);
        }

        String message = "Unknown";
        if (rr.getErrorMsg().length() > 0) {
            message = rr.getErrorMsg();
        }
        message = String.format("Error getting replay profile member volumes: %s", message);

        throw new StorageCenterAPIException(message);
    }

    /**
     * Gets a replay.
     * 
     * @param instanceId The replay's instance ID.
     * @return The replay.
     */
    public ScReplay getReplay(String instanceId) {
        RestResult rr = restClient.get(String.format("StorageCenter/ScReplay/%s", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScReplay.class);
        }

        return null;
    }

    /**
     * Creates a replay on a volume.
     *
     * @param instanceId The volume instance ID.
     * @return The created replay.
     * @throws StorageCenterAPIException
     */
    public ScReplay createReplay(String instanceId) throws StorageCenterAPIException {
        return createReplay(instanceId, 0);
    }

    /**
     * Creates a replay on a volume.
     *
     * @param instanceId The volume instance ID.
     * @param expireTime The number of minutes before the snapshot expires.
     * @return The created replay.
     * @throws StorageCenterAPIException
     */
    public ScReplay createReplay(String instanceId, int expireTime) throws StorageCenterAPIException {
        Parameters params = new Parameters();
        params.add("description", NOTES_STRING);
        params.add("expireTime", expireTime);

        RestResult rr = restClient.post(
                String.format("StorageCenter/ScVolume/%s/CreateReplay", instanceId), params.toJson());
        if (!checkResults(rr)) {
            String msg = String.format("Error creating replay %s: %s", instanceId, rr.getErrorMsg());
            LOG.warn(msg);
            throw new StorageCenterAPIException(msg);
        }

        return gson.fromJson(rr.getResult(), ScReplay.class);
    }

    /**
     * Create a view volume of a replay.
     *
     * @param instanceId The replay instance ID.
     * @param name The name to give the volume.
     * @return The created volume.
     * @throws StorageCenterAPIException
     */
    public ScVolume createReplayView(String instanceId, String name) throws StorageCenterAPIException {
        Parameters params = new Parameters();
        params.add("Name", name);
        params.add("Notes", instanceId);

        RestResult rr = restClient.post(
                String.format("StorageCenter/ScReplay/%s/CreateView", instanceId), params.toJson());
        if (!checkResults(rr)) {
            LOG.warn("Error creating view volume of replay {}: {}", instanceId, rr.getErrorMsg());
            throw new StorageCenterAPIException(rr.getErrorMsg());
        }

        return gson.fromJson(rr.getResult(), ScVolume.class);
    }

    /**
     * Finds a view volume for a given replay.
     *
     * @param instanceId The replay instance ID.
     * @return The volume or null if not found.
     */
    public ScVolume findReplayView(String instanceId) {
        PayloadFilter filter = new PayloadFilter();
        filter.append("notes", instanceId);

        RestResult rr = restClient.post("StorageCenter/ScVolumeConfiguration/GetList", filter.toJson());
        if (checkResults(rr)) {
            ScVolumeConfiguration[] config = gson.fromJson(rr.getResult(), ScVolumeConfiguration[].class);
            rr = restClient.get(
                    String.format("StorageCenter/ScVolume/%s", config[0].volume.instanceId));
            if (checkResults(rr)) {
                return gson.fromJson(rr.getResult(), ScVolume.class);
            } else {
                LOG.warn(rr.getErrorMsg());
            }
        } else {
            LOG.warn(rr.getErrorMsg());
        }

        return null;
    }

    /**
     * Expire a replay.
     *
     * @param instanceId The replay instance ID.
     * @throws StorageCenterAPIException
     */
    public void expireReplay(String instanceId) throws StorageCenterAPIException {
        Parameters params = new Parameters();
        RestResult rr = restClient.post(
                String.format("StorageCenter/ScReplay/%s/Expire", instanceId), params.toJson());
        if (!checkResults(rr)) {
            String msg = String.format("Error expiring replay %s: %s", instanceId, rr.getErrorMsg());
            LOG.warn(msg);
            throw new StorageCenterAPIException(msg);
        }
    }

    /**
     * Gets all copy, mirror, and migrate operations for a volume.
     *
     * @param instanceId The volume instance ID.
     * @return The CMM operations.
     */
    public ScCopyMirrorMigrate[] getVolumeCopyMirrorMigrate(String instanceId) {
        RestResult rr = restClient.get(String.format("StorageCenter/ScVolume/%s/CopyMirrorMigrateList", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScCopyMirrorMigrate[].class);
        }

        return new ScCopyMirrorMigrate[0];
    }

    /**
     * Adds a volume to a consistency group.
     *
     * @param instanceId The volume instance ID.
     * @param cgID The consistency group ID.
     * @throws StorageCenterAPIException
     */
    public void addVolumeToConsistencyGroup(String instanceId, String cgID) throws StorageCenterAPIException {
        RestResult rr = restClient.get(String.format("StorageCenter/ScVolumeConfiguration/%s", instanceId));
        if (!checkResults(rr)) {
            throw new StorageCenterAPIException(String.format("Error getting volume configuration: %s", rr.getErrorMsg()));
        }

        ScVolumeConfiguration volConfig = gson.fromJson(rr.getResult(), ScVolumeConfiguration.class);
        List<String> profiles = new ArrayList<>();
        for (ScObject profile : volConfig.replayProfileList) {
            if (!cgID.equals(profile.instanceId)) {
                profiles.add(profile.instanceId);
            }
        }
        profiles.add(cgID);

        Parameters params = new Parameters();
        params.add("ReplayProfileList", profiles.toArray(new String[0]));
        rr = restClient.put(String.format("StorageCenter/ScVolumeConfiguration/%s", instanceId), params.toJson());
        if (!checkResults(rr)) {
            throw new StorageCenterAPIException(String.format("Error updating volume replay profile membership: %s", rr.getErrorMsg()));
        }
    }

    /**
     * Removes a volume from a consistency group.
     *
     * @param instanceId The volume instance ID.
     * @param cgID The consistency group ID.
     * @throws StorageCenterAPIException
     */
    public void removeVolumeFromConsistencyGroup(String instanceId, String cgID) throws StorageCenterAPIException {
        RestResult rr = restClient.get(String.format("StorageCenter/ScVolumeConfiguration/%s", instanceId));
        if (!checkResults(rr)) {
            throw new StorageCenterAPIException(String.format("Error getting volume configuration: %s", rr.getErrorMsg()));
        }

        ScVolumeConfiguration volConfig = gson.fromJson(rr.getResult(), ScVolumeConfiguration.class);
        List<String> profiles = new ArrayList<>();
        for (ScObject profile : volConfig.replayProfileList) {
            if (!cgID.equals(profile.instanceId)) {
                profiles.add(profile.instanceId);
            }
        }

        Parameters params = new Parameters();
        params.add("ReplayProfileList", profiles.toArray(new String[0]));
        rr = restClient.put(String.format("StorageCenter/ScVolumeConfiguration/%s", instanceId), params.toJson());
        if (!checkResults(rr)) {
            throw new StorageCenterAPIException(String.format("Error updating volume replay profile membership: %s", rr.getErrorMsg()));
        }
    }

    /**
     * Gets a replay profile with the given ID.
     *
     * @param instanceId The replay profile instance ID.
     * @return The replay profile.
     * @throws StorageCenterAPIException
     */
    public ScReplayProfile getConsistencyGroup(String instanceId) throws StorageCenterAPIException {

        RestResult rr = restClient.get(String.format("StorageCenter/ScReplayProfile/%s", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScReplayProfile.class);
        }

        String message = String.format("Error getting replay profile : %s", rr.getErrorMsg());
        throw new StorageCenterAPIException(message);
    }

    /**
     * Gets existing mapping profiles between a server and volume.
     *
     * @param volInstanceId The volume instance ID.
     * @param serverInstanceId The server instance ID.
     * @return The mapping profiles between the two.
     */
    public ScMappingProfile[] getServerVolumeMapping(String volInstanceId, String serverInstanceId) {
        PayloadFilter filter = new PayloadFilter();
        filter.append("volume", volInstanceId);
        filter.append("server", serverInstanceId);

        RestResult rr = restClient.post("StorageCenter/ScMappingProfile/GetList", filter.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScMappingProfile[].class);
        }

        return new ScMappingProfile[0];
    }

    /**
     * Maps a volume to a server.
     *
     * @param volInstanceId The volume instance ID.
     * @param serverInstanceId The server instance ID.
     * @param preferredLun The preferred LUN to use or -1 to let the system decided.
     * @param preferredPorts The preferred server HBA ports to use.
     * @param maxPathCount The maximum paths to map or -1 for no restriction.
     * @param preferredController The preferred controller to map through or null.
     * @return The created mapping profile.
     * @throws StorageCenterAPIException
     */
    public ScMappingProfile createVolumeMappingProfile(
            String volInstanceId, String serverInstanceId, int preferredLun,
            String[] preferredPorts, int maxPathCount, String preferredController)
                    throws StorageCenterAPIException {
        Parameters advancedParams = new Parameters();
        advancedParams.add("MapToDownServerHbas", true);
        if (preferredLun != -1) {
            advancedParams.add("PreferredLun", preferredLun);
        }
        if (preferredPorts != null && preferredPorts.length > 0) {
            advancedParams.add("PreferredServerHbaList", preferredPorts);
        }
        if (maxPathCount > 0) {
            advancedParams.add("MaximumPathCount", maxPathCount);
        }
        if (preferredController != null) {
            advancedParams.add("PreferredController", preferredController);
        }

        Parameters params = new Parameters();
        params.add("server", serverInstanceId);
        params.add("Advanced", advancedParams.getRawPayload());

        RestResult rr = restClient.post(
                String.format("StorageCenter/ScVolume/%s/MapToServer", volInstanceId), params.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScMappingProfile.class);
        }

        throw new StorageCenterAPIException(
                String.format("Error creating volume mapping: %s", rr.getErrorMsg()));
    }

    /**
     * Gets the individual maps created for a mapping profile.
     *
     * @param instanceId The mapping profile instance ID.
     * @return The mappings.
     * @throws StorageCenterAPIException
     */
    public ScMapping[] getMappingProfileMaps(String instanceId) throws StorageCenterAPIException {

        RestResult rr = restClient.get(
                String.format("StorageCenter/ScMappingProfile/%s/MappingList", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScMapping[].class);
        }

        String message = String.format("Error getting volume maps: %s", rr.getErrorMsg());
        throw new StorageCenterAPIException(message);
    }

    /**
     * Gets the individual maps for a volume.
     *
     * @param instanceId The volume instance ID.
     * @return The mappings.
     * @throws StorageCenterAPIException
     */
    public ScMapping[] getVolumeMaps(String instanceId) throws StorageCenterAPIException {
        RestResult rr = restClient.get(
                String.format("StorageCenter/ScVolume/%s/MappingList", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScMapping[].class);
        }

        String message = String.format("Error getting volume maps: %s", rr.getErrorMsg());
        throw new StorageCenterAPIException(message);
    }

    /**
     * Gets a controller port.
     *
     * @param instanceId The controller port ID.
     * @return The controller port.
     * @throws StorageCenterAPIException
     */
    public ScControllerPort getControllerPort(String instanceId) throws StorageCenterAPIException {
        RestResult rr = restClient.get(
                String.format("/StorageCenter/ScControllerPort/%s", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScControllerPort.class);
        }

        String error = String.format("Error getting controller port %s: %s", instanceId, rr.getErrorMsg());
        throw new StorageCenterAPIException(error);
    }

    /**
     * Add an HBA to a server definition.
     *
     * @param instanceId The server instance ID.
     * @param iqnOrWwn The IQN or WWN to add.
     * @param isIscsi Whether it is iSCSI or FC.
     * @return The added ScServerHba.
     */
    public ScServerHba addHbaToServer(String instanceId, String iqnOrWwn, boolean isIscsi) {
        Parameters params = new Parameters();
        params.add("HbaPortType", isIscsi ? "Iscsi" : "FibreChannel");
        params.add("WwnOrIscsiName", iqnOrWwn);
        params.add("AllowManual", true);

        RestResult rr = restClient.post(
                String.format("StorageCenter/ScPhysicalServer/%s/AddHba", instanceId), params.toJson());
        if (!checkResults(rr)) {
            LOG.warn("Error adding HBA to server {}", rr.getErrorMsg());
            return null;
        }

        return gson.fromJson(rr.getResult(), ScServerHba.class);
    }

    /**
     * Create a new server definition.
     *
     * @param ssn The Storage Center system serial number.
     * @param hostName The host name.
     * @param isIscsi Whether it is iSCSI or FC.
     * @param osId The OS instance ID.
     * @return The created server.
     * @throws StorageCenterAPIException
     */
    public ScPhysicalServer createServer(String ssn, String hostName, boolean isIscsi, String osId)
            throws StorageCenterAPIException {
        Parameters params = new Parameters();
        params.add("Name", hostName);
        params.add("StorageCenter", ssn);
        params.add("Notes", NOTES_STRING);
        params.add("OperatingSystem", osId);

        RestResult rr = restClient.post("StorageCenter/ScPhysicalServer", params.toJson());
        if (!checkResults(rr)) {
            String error = String.format("Error creating server '%s': %s", hostName, rr.getErrorMsg());
            throw new StorageCenterAPIException(error);
        }

        return gson.fromJson(rr.getResult(), ScPhysicalServer.class);
    }

    /**
     * Creates a new cluster server definition.
     *
     * @param ssn The Storage Center serial number.
     * @param clusterName The cluster name.
     * @param osId The OS instance ID.
     * @return The created server.
     * @throws StorageCenterAPIException
     */
    public ScServer createClusterServer(String ssn, String clusterName, String osId) throws StorageCenterAPIException {
        Parameters params = new Parameters();
        params.add("Name", clusterName);
        params.add("StorageCenter", ssn);
        params.add("Notes", NOTES_STRING);
        params.add("OperatingSystem", osId);

        RestResult rr = restClient.post("StorageCenter/ScServerCluster", params.toJson());
        if (!checkResults(rr)) {
            String error = String.format("Error creating cluster server '%s': %s", clusterName, rr.getErrorMsg());
            throw new StorageCenterAPIException(error);
        }

        return gson.fromJson(rr.getResult(), ScServer.class);
    }

    /**
     * Move a server under a cluster.
     *
     * @param serverId The server instance ID.
     * @param clusterId The cluster instance ID.
     * @return True if successful, false otherwise.
     */
    public boolean setAddServerToCluster(String serverId, String clusterId) {
        Parameters params = new Parameters();
        params.add("Parent", clusterId);

        RestResult rr = restClient.post(
                String.format("StorageCenter/ScPhysicalServer/%s/AddToCluster", serverId), params.toJson());
        return checkResults(rr);
    }

    /**
     * Gets the OS types.
     * 
     * @param ssn The Storage Center system serial number.
     * @param product The product to filter on or null.
     * @return The OS types.
     */
    public ScServerOperatingSystem[] getServerOperatingSystems(String ssn, String product) {
        PayloadFilter filter = new PayloadFilter();
        filter.append("scSerialNumber", ssn);
        if (product != null && !product.isEmpty()) {
            filter.append("product", product, ValueFilterType.INCLUDESSTRING);
        }

        RestResult rr = restClient.post("StorageCenter/ScServerOperatingSystem/GetList", filter.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScServerOperatingSystem[].class);
        }

        return new ScServerOperatingSystem[0];
    }

    /**
     * Finds mapping between a server and volume.
     *
     * @param serverId The server instance ID.
     * @param volumeId The volume instance ID.
     * @return The mapping profiles.
     * @throws StorageCenterAPIException
     */
    public ScMappingProfile[] findMappingProfiles(String serverId, String volumeId) throws StorageCenterAPIException {
        PayloadFilter filter = new PayloadFilter();
        filter.append("server", serverId);
        filter.append("volume", volumeId);

        RestResult rr = restClient.post("StorageCenter/ScMappingProfile/GetList", filter.toJson());
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScMappingProfile[].class);
        }

        String message = String.format("Error getting mapping profiles from server %s and volume %s: %s",
                serverId, volumeId, rr.getErrorMsg());
        throw new StorageCenterAPIException(message);
    }

    /**
     * Deletes a mapping profile.
     *
     * @param instanceId The mapping profile instance ID.
     * @throws StorageCenterAPIException
     */
    public void deleteMappingProfile(String instanceId) throws StorageCenterAPIException {
        RestResult rr = restClient.delete(String.format("StorageCenter/ScMappingProfile/%s", instanceId));
        if (!checkResults(rr)) {
            throw new StorageCenterAPIException(rr.getErrorMsg());
        }
    }

    /**
     * Creates a volume from a snapshot.
     *
     * @param name The name for the new volume.
     * @param instanceId The replay instance ID.
     * @return The created volume.
     * @throws StorageCenterAPIException
     */
    public ScVolume createViewVolume(String name, String instanceId) throws StorageCenterAPIException {
        LOG.debug("Creating view volume of replay {}", instanceId);
        String errorMessage = "";

        Parameters params = new Parameters();
        params.add("Name", name);
        params.add("Notes", NOTES_STRING);

        try {
            RestResult result = restClient.post(
                    String.format("StorageCenter/ScReplay/%s/CreateView", instanceId),
                    params.toJson());
            if (checkResults(result)) {
                return gson.fromJson(result.getResult(), ScVolume.class);
            }
        } catch (Exception e) {
            errorMessage = String.format("Error creating view volume: %s", e);
            LOG.warn(errorMessage);
        }

        if (errorMessage.length() == 0) {
            errorMessage = String.format("Unable to create view volume %s from replay %s", name, instanceId);
        }
        throw new StorageCenterAPIException(errorMessage);
    }

    /**
     * Create a mirror from one volume to another.
     *
     * @param ssn The Storage Center to create the mirror.
     * @param srcId The source volume ID.
     * @param dstId The destination volume ID.
     * @return The CMM operation.
     * @throws StorageCenterAPIException
     */
    public ScCopyMirrorMigrate createMirror(String ssn, String srcId, String dstId) throws StorageCenterAPIException {
        Parameters params = new Parameters();
        params.add("StorageCenter", ssn);
        params.add("SourceVolume", srcId);
        params.add("DestinationVolume", dstId);
        params.add("CopyReplays", false);

        RestResult rr = restClient.post("StorageCenter/ScCopyMirrorMigrate/Mirror", params.toJson());
        if (!checkResults(rr)) {
            String msg = String.format("Error creating mirror from %s to %s: %s", srcId, dstId, rr.getErrorMsg());
            LOG.warn(msg);
            throw new StorageCenterAPIException(msg);
        }

        return gson.fromJson(rr.getResult(), ScCopyMirrorMigrate.class);
    }

    /**
     * Gets a mirror operation.
     *
     * @param instanceId The CMM instance ID.
     * @return The CMM operation.
     * @throws StorageCenterAPIException
     */
    public ScCopyMirrorMigrate getMirror(String instanceId) throws StorageCenterAPIException {
        RestResult rr = restClient.get(String.format("StorageCenter/ScCopyMirrorMigrate/%s", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScCopyMirrorMigrate.class);
        }

        String message = String.format("Error getting mirror operation: %s", rr.getErrorMsg());
        throw new StorageCenterAPIException(message);
    }

    /**
     * Delete a mirror operation.
     *
     * @param instanceId The CMM instance ID.
     * @throws StorageCenterAPIException
     */
    public void deleteMirror(String instanceId) throws StorageCenterAPIException {
        LOG.debug("Deleting mirror '{}'", instanceId);

        RestResult rr = restClient.delete(String.format("StorageCenter/ScCopyMirrorMigrate/%s", instanceId));
        if (!checkResults(rr)) {
            String msg = String.format("Error deleting mirror %s: %s", instanceId, rr.getErrorMsg());
            LOG.error(msg);
            throw new StorageCenterAPIException(msg);
        }
    }

    /**
     * Gets some basic DSM (EM) information.
     * 
     * @return The EmDataCollector information.
     */
    public EmDataCollector getDSMInfo() {
        RestResult rr = restClient.get("EnterpriseManager/EmDataCollector");
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), EmDataCollector.class);
        }

        return null;
    }

    /**
     * Gets API connection information.
     *
     * @return The API connection info.
     * @throws StorageCenterAPIException
     */
    public ApiConnection getApiConnection() throws StorageCenterAPIException {
        RestResult rr = restClient.get("ApiConnection/ApiConnection");
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ApiConnection.class);
        }

        throw new StorageCenterAPIException("Failed to get API connection");
    }

    /**
     * Workaround for tests. Will make sure a volume is active somewhere so
     * snapshots and other operations can be performed on it.
     *
     * @param volumeId The instance ID of the volume.
     */
    public void checkAndInitVolume(String volumeId) {
        ScVolume volume = getVolume(volumeId);
        if (volume == null) {
            // Just let the calling method handle the subsequent failure
            return;
        }

        if (volume.active && volume.replayAllowed) {
            // It's all good
            return;
        }

        ScServer[] servers = getServerDefinitions(volume.scSerialNumber);
        for (ScServer server : servers) {
            if (!"down".equalsIgnoreCase(server.status)) {
                try {
                    ScMappingProfile mapping = createVolumeMappingProfile(
                            volume.instanceId, server.instanceId, -1, new String[0], -1, null);
                    deleteMappingProfile(mapping.instanceId);
                    break;
                } catch (StorageCenterAPIException e) {
                    LOG.debug("Failed to activate volume to server {}.", server.instanceId);
                }
            }
        }
    }

    /**
     * Gets a volume's configuration.
     *
     * @param instanceId The volume instance ID.
     * @return The volume config.
     */
    public ScVolumeConfiguration getVolumeConfig(String instanceId) {
        RestResult rr = restClient.get(
                String.format("StorageCenter/ScVolume/%s/VolumeConfiguration", instanceId));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScVolumeConfiguration.class);
        }

        LOG.warn(String.format("Error getting volume configuration: %s", rr.getErrorMsg()));
        return null;
    }

    /**
     * Gets SC configuration settings.
     *
     * @param ssn The system serial number.
     * @return The configuration settings.
     * @throws StorageCenterAPIException
     */
    public ScConfiguration getScConfig(String ssn) throws StorageCenterAPIException {
        RestResult rr = restClient.get(String.format("StorageCenter/ScConfiguration/%s", ssn));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScConfiguration.class);
        }

        String message = String.format("Error getting Storage Center configuration: %s", rr.getErrorMsg());
        throw new StorageCenterAPIException(message);
    }

    /**
     * Gets all fault domains for a system.
     *
     * @param ssn The system serial number.
     * @return The fault domains.
     */
    public ScFaultDomain[] getFaultDomains(String ssn) {
        RestResult rr = restClient.get(String.format("StorageCenter/StorageCenter/%s/FaultDomainList", ssn));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScFaultDomain[].class);
        }

        LOG.warn(String.format("Error getting Storage Center configuration: %s", rr.getErrorMsg()));
        return new ScFaultDomain[0];
    }

    /**
     * Gets the virtual ports in a fault domain.
     *
     * @param instanceId The fault domain instance ID.
     * @param True to get virtual ports, false to get physical.
     * @return The controller ports.
     */
    public ScControllerPort[] getFaultDomainPorts(String instanceId, boolean virtualPorts) {
        String portType = virtualPorts ? "Virtual" : "Physical";
        RestResult rr = restClient.get(
                String.format("StorageCenter/ScFaultDomain/%s/%sPortList", instanceId, portType));
        if (checkResults(rr)) {
            return gson.fromJson(rr.getResult(), ScControllerPort[].class);
        }

        LOG.warn(String.format("Error getting controller ports for fault domain %s: %s", instanceId, rr.getErrorMsg()));
        return new ScControllerPort[0];
    }
}
