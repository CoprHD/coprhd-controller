/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Query volume list by given StorageGroup.
 *
 * Created by gang on 9/26/16.
 */
public class SloprovisioningSymmetrixVolumeListByStorageGroup extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(SloprovisioningSymmetrixVolumeListByStorageGroup.class);

    private String symmetrixId;
    private String storageGroupId;
    private RestClient client;

    public SloprovisioningSymmetrixVolumeListByStorageGroup(String symmetrixId, String storageGroupId) {
        this.symmetrixId = symmetrixId;
        this.storageGroupId = storageGroupId;
    }

    @Override
    public Object perform(RestClient client) {
        this.client = client;
        String path = String.format(Vmaxv3Constants.RA_SLOPROVISIONING_SYMMETRIX_VOLUME_LIST_BY_STORAGE_GROUP,
            this.symmetrixId, this.storageGroupId);
        logger.info("SloprovisioningSymmetrixVolumeListByStorageGroup request path = {}", path);
        String responseBody = client.request(path);
        logger.info("SloprovisioningSymmetrixVolumeListByStorageGroup response body = {}", responseBody);
        List<String> volumeIds = parseRestResult(responseBody);
        return volumeIds;
    }

    /**
     * Parse the REST response below and return the array list:
     *
     * {
     *   "expirationTime": 1474959058679,
     *   "count": 2,
     *   "maxPageSize": 1000,
     *   "id": "0f7e89f7-e5ce-491c-bc36-8b29ace35651_0",
     *   "resultList": {
     *     "result": [
     *       {
     *         "volumeId": "00543"
     *       },
     *       {
     *         "volumeId": "00545"
     *       }
     *     ],
     *     "from": 1,
     *     "to": 2
     *   }
     * }
     *
     * If the result items count is more than one page, need to call iteration API
     * to get the other items not in the first page.
     *
     * @param responseBody
     */
    private List<String> parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        JsonObject resultList = root.get("resultList").getAsJsonObject();
        if (resultList == null) {
            throw new Vmaxv3RestCallException("No result in 'SloprovisioningSymmetrixVolumeListByStorageGroup' call: " +
                root.toString());
        }
        // Get the records in the first page.
        List<String> volumeIds = new ArrayList<>();
        JsonArray list = resultList.getAsJsonArray("result");
        Iterator<JsonElement> it = list.iterator();
        while (it.hasNext()) {
            JsonObject item = it.next().getAsJsonObject();
            volumeIds.add(item.get("volumeId").getAsString());
        }
        // Get the other pages if there are more than one page records.
        String iteratorId = root.get("id").getAsString();
        int totalCount = root.get("count").getAsInt();
        int maxPageSize = root.get("maxPageSize").getAsInt();
        int fromIndex = resultList.get("from").getAsInt();
        int toIndex = resultList.get("to").getAsInt();
        while (toIndex < totalCount) {
            fromIndex = toIndex + 1;
            toIndex = (toIndex + maxPageSize) > totalCount ? totalCount : (toIndex + maxPageSize);
            logger.info("Querying the paging records from {} to {}...", fromIndex, toIndex);
            CommonIteratorPageGet pageGet = new CommonIteratorPageGet(iteratorId, fromIndex, toIndex);
            JsonObject pageResult = (JsonObject)pageGet.perform(this.client);
            /*
            pageResult is something like below:
            {
              "result": [
                {
                  "volumeId": "003E9"
                },
                {
                  "volumeId": "003EA"
                },
                {
                  "volumeId": "003EB"
                },
                ...
              ],
              "from": 1001,
              "to": 2000
            }
             */
            JsonArray pageList = pageResult.getAsJsonArray("result");
            Iterator<JsonElement> pageIt = pageList.iterator();
            while (pageIt.hasNext()) {
                JsonObject item = pageIt.next().getAsJsonObject();
                volumeIds.add(item.get("volumeId").getAsString());
            }
        }
        // Return result.
        return volumeIds;
    }
}
