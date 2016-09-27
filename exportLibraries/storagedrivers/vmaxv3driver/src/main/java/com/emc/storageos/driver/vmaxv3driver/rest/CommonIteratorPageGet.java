/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest;

import com.emc.storageos.driver.vmaxv3driver.Vmaxv3Constants;
import com.emc.storageos.driver.vmaxv3driver.base.RestActionImpl;
import com.emc.storageos.driver.vmaxv3driver.exception.Vmaxv3RestCallException;
import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query paging iteration list by given iteratorId, fromIndex and toIndex.
 *
 * Created by gang on 9/27/16.
 */
public class CommonIteratorPageGet extends RestActionImpl {

    private static final Logger logger = LoggerFactory.getLogger(CommonIteratorPageGet.class);

    private String iteratorId;
    private int fromIndex;
    private int toIndex;

    public CommonIteratorPageGet(String iteratorId, int fromIndex, int toIndex) {
        this.iteratorId = iteratorId;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }

    @Override
    public Object perform(RestClient client) {
        String path = String.format(Vmaxv3Constants.RA_COMMON_ITERATOR_PAGE,
            this.iteratorId, this.fromIndex, this.toIndex);
        String responseBody = client.request(path);
        JsonObject pageResult = parseRestResult(responseBody);
        return pageResult;
    }

    /**
     * Parse the REST response below and return the array list:
     *
     * {
     *   "result": [
     *     {
     *       "volumeId": "003E9"
     *     },
     *     {
     *       "volumeId": "003EA"
     *     },
     *     {
     *      "volumeId": "003EB"
     *     },
     *    ...
     *   ],
     *   "from": 1001,
     *   "to": 2000
     * }
     *
     * @param responseBody
     */
    private JsonObject parseRestResult(String responseBody) {
        logger.debug("Response body = {}", responseBody);
        JsonObject root = this.parseResponse(responseBody);
        JsonObject resultList = root.get("result").getAsJsonObject();
        if (resultList == null) {
            throw new Vmaxv3RestCallException("No result in 'CommonIteratorPageGet' call: " + root.toString());
        }
        return root;
    }
}
