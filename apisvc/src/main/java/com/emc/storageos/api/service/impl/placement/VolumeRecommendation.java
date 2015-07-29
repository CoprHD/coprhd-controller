/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.Recommendation;

public class VolumeRecommendation extends Recommendation {
    private static final long serialVersionUID = 2945344796836227644L;

    public static final String BLOCK_VOLUME = "block_volume";
    public static final String LOCAL_MIRROR = "local_mirror";
    public static final String ARRAY_CG = "array_cg";

    public enum VolumeType {
        BLOCK_VOLUME,          // user's data volume
        BLOCK_LOCAL_MIRROR,    // local mirror
        BLOCK_COPY,            // full copy
        VPLEX_VIRTUAL_VOLUME,  // VPLEX Virtual Volume
        RP_VOLUME              // RP volume (a.k.a RP CG)
    };

    public enum PlacementConstraint {
        STORAGE_SYSTEM,
    };

    private VolumeType _type;
    private Long _size;         // size of the volume
    private URI _id;
    private VirtualPool _virtualPool;           // the class of service used for this volume
    private URI _neighborhood;   // neighborhood where volume should be created

    // Layer/device specific parameters (key/value) for this volume. This could be
    // used to hold the recover point or vplex device that will serve the volume.
    private Map<String, Object> _parameters = new HashMap<String, Object>();

    // candidate pools for this volume
    private List<URI> _candidatePools = new ArrayList<URI>();

    // candidate systems for this volume (ex. VPLEX system for virtual volume, RP cluster for RP CG, storage array for block volume)
    private List<URI> _candidateSystems = new ArrayList<URI>();

    private Map<PlacementConstraint, List<VolumeRecommendation>> _constraints = new HashMap<PlacementConstraint, List<VolumeRecommendation>>();

    public VolumeRecommendation(VolumeType type, Long size, VirtualPool virtualPool, URI neighborhood) {
        this._type = type;
        this._size = size;
        this._virtualPool = virtualPool;
        this._neighborhood = neighborhood;
    }

    public URI getId() {
        return _id;
    }

    public void setId(URI id) {
        this._id = id;
    }

    public void setParameter(String name, Object value) {
        if (null != name && null != value) {
            _parameters.put(name, value);
        }
    }

    public Object getParameter(String name) {
        return _parameters.get(name);
    }

    public List<URI> getCandidatePools() {
        return _candidatePools;
    }

    public void addStoragePool(URI storagePool) {
        _candidatePools.add(storagePool);
    }

    public List<URI> getCandidateSystems() {
        return _candidateSystems;
    }

    public void addStorageSystem(URI candidateSystem) {
        _candidateSystems.add(candidateSystem);
    }

    public VolumeType getType() {
        return _type;
    }

    public List<VolumeRecommendation> getConstraints(PlacementConstraint constraint) {
        return _constraints.get(constraint);
    }

    public void addConstraint(PlacementConstraint type, List<VolumeRecommendation> values) {
        List<VolumeRecommendation> constraintList = _constraints.get(type);
        if (constraintList == null) {
            constraintList = new ArrayList<VolumeRecommendation>();
        }
        constraintList.addAll(values);
    }

    /**
     * Return a map of device URI to a list of recommendations in that device.
     * This method can be used only for block volume recommendations since it assumes that there is only a single pool in the recommended
     * storage pools.
     * 
     * @param recommendations List<VolumeRecommendation>
     * @param dbClient
     * @return Map of device URI to List<VolumeRecommendation> in that device
     */
    static public Map<URI, List<VolumeRecommendation>> getDeviceMap(List<Recommendation> recommendations, DbClient dbClient) {
        HashMap<URI, List<VolumeRecommendation>> deviceMap = new HashMap<URI, List<VolumeRecommendation>>();

        for (Recommendation baseRecommendation : recommendations) {
            VolumeRecommendation recommendation = (VolumeRecommendation) baseRecommendation;
            URI poolId = recommendation.getCandidatePools().get(0);
            StoragePool storagePool = dbClient.queryObject(StoragePool.class, poolId);
            URI deviceId = storagePool.getStorageDevice();
            if (deviceMap.get(deviceId) == null) {
                deviceMap.put(deviceId, new ArrayList<VolumeRecommendation>());
            }
            deviceMap.get(deviceId).add(recommendation);
        }
        return deviceMap;
    }
}
