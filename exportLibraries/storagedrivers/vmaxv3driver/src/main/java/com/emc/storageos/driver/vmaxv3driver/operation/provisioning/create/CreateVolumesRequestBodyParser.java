/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.provisioning.create;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RequestBodyParser;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.rest.request.RequestStorageGroupPost;
import com.emc.storageos.driver.vmaxv3driver.rest.request.SloBasedStorageGroupParam;
import com.emc.storageos.driver.vmaxv3driver.rest.request.VolumeAttribute;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Create the POST StorageGroup request according to the passed-in arguments.
 *
 * {
 *   "srpId": "SRP_1",
 *   "storageGroupId": "test2",
 *   "sloBasedStorageGroupParam": [
 *     {
 *       "num_of_vols": 3,
 *       "sloId": "Silver",
 *       "workloadSelection": "OLTP",
 *       "volumeAttribute": {
 *         "volume_size": "100",
 *         "capacityUnit": "GB"
 *       }
 *     },
 *     {
 *       "num_of_vols": 2,
 *       "sloId": "Gold",
 *       "workloadSelection": "OLAP",
 *       "volumeAttribute": {
 *         "volume_size": "2",
 *         "capacityUnit": "TB"
 *       }
 *     }
 *   ]
 * }
 *
 * TODO:
 * Is it possible that one "createVolumes" request may create volumes in more than one pool(srpId)?
 * If yes, more than one POST requests are needed.
 *
 * Created by gang on 9/28/16.
 */
public class CreateVolumesRequestBodyParser implements RequestBodyParser {

    protected List<StorageVolume> volumes;
    protected StorageCapabilities capabilities;

    public CreateVolumesRequestBodyParser(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        this.volumes = volumes;
        this.capabilities = capabilities;
    }

    @Override
    public RequestStorageGroupPost parse() {
        RequestStorageGroupPost overallResult = null;
        for (StorageVolume volume : this.volumes) {
            RequestStorageGroupPost singleResult = this.parseSingleVolume(volume);
            if (overallResult == null) { // It's the first parsed volume.
                overallResult = singleResult;
            } else { // It's not the first parsed volume.
                /*
                Before combining, check if the current volume is in the same pool(srp)
                as the first one. If not, this kind of request cannot be handled by the
                current code, throw exception and stop.
                 */
                if (!overallResult.getSrpId().equals(singleResult.getSrpId())) {
                    throw new Vmaxv3RestCallException("The given volumes are not in the same pool.");
                }
                combineSingleVolumeIntoOverall(overallResult, singleResult);
            }
        }
        return overallResult;
    }

    /**
     * Parse the given volume into a RequestStorageGroupPost instance.
     *
     * @param volume
     * @return
     */
    private RequestStorageGroupPost parseSingleVolume(StorageVolume volume) {
        RequestStorageGroupPost result = new RequestStorageGroupPost();
        SloBasedStorageGroupParam param = new SloBasedStorageGroupParam();
        param.setNum_of_vols(1);
        param.setSloId(Vmaxv3Constants.DEFAULT_VALUE_STORAGE_GROUP_SLO);
        param.setWorkloadSelection(Vmaxv3Constants.DEFAULT_VALUE_STORAGE_GROUP_WORKLOAD);
        VolumeAttribute volumeAttribute = new VolumeAttribute();
        volumeAttribute.setVolume_size(String.valueOf(volume.getRequestedCapacity()));
        volumeAttribute.setCapacityUnit(Vmaxv3Constants.DEFAULT_VALUE_VOLUME_CAPACITY_UNIT);
        param.setVolumeAttribute(volumeAttribute);
        List<SloBasedStorageGroupParam> params = new ArrayList<>();
        params.add(param);
        result.setSloBasedStorageGroupParam(params);
        result.setSrpId(volume.getStoragePoolId());
        result.setStorageGroupId(generateStorageGroupId(result));
        return result;
    }

    /**
     * Combine the given volume into the existing RequestStorageGroupPost instance.
     *
     * @param postRequest
     * @param singleVolumeResult
     */
    private void combineSingleVolumeIntoOverall(RequestStorageGroupPost postRequest,
                                                RequestStorageGroupPost singleVolumeResult) {
        boolean isCombinedIntoExistingParam = false;
        for (SloBasedStorageGroupParam param : postRequest.getSloBasedStorageGroupParam()) {
            if (isParamEqual(param, singleVolumeResult.getSloBasedStorageGroupParam().get(0))) {
                // Increase the existing param by "+1" to the "num_of_vols" field value.
                param.setNum_of_vols(param.getNum_of_vols() + 1);
                isCombinedIntoExistingParam = true;
            }
        }
        // Add a new param item if not already combined into a existing param.
        if(!isCombinedIntoExistingParam) {
            postRequest.getSloBasedStorageGroupParam().add(singleVolumeResult.getSloBasedStorageGroupParam().get(0));
        }
    }

    /**
     * Generate a StorageGroup name. The name looks like below:
     *   "ViPRSBSDK_Optimized_NONE_SRP_1_682bdba3-e4fb-4b32-98b6-c4a33d67d6a6"
     *
     * @param storageGroup
     * @return
     */
    private String generateStorageGroupId(RequestStorageGroupPost storageGroup) {
        SloBasedStorageGroupParam param = storageGroup.getSloBasedStorageGroupParam().get(0);
        return String.format(Vmaxv3Constants.DEFAULT_VALUE_STORAGE_GROUP_NAME, param.getSloId(),
            param.getWorkloadSelection(), storageGroup.getSrpId(), UUID.randomUUID());
    }

    /**
     * Check if the given 2 SloBasedStorageGroupParam instance is equal or not. For combining operation.
     *
     * @param p1
     * @param p2
     * @return
     */
    private boolean isParamEqual(SloBasedStorageGroupParam p1, SloBasedStorageGroupParam p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        VolumeAttribute v1 = p1.getVolumeAttribute();
        VolumeAttribute v2 = p2.getVolumeAttribute();
        return p1.getSloId().equals(p2.getSloId())
            && p1.getWorkloadSelection().equals(p2.getWorkloadSelection())
            && v1.getVolume_size().equals(v2.getVolume_size())
            && v1.getCapacityUnit().equals(v2.getCapacityUnit());
    }
}
