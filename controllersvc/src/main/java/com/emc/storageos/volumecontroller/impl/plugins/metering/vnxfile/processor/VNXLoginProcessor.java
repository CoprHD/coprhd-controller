/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;

/**
 * VNXLoginProcessor is responsible to process the result received from XML API
 * Server during Login request. This is extracts the cookie information from
 * response packet and uses the cookie in the rest of the requests.
 */
public class VNXLoginProcessor extends Processor {

    /**
     * Logger instance.
     */
    private final Logger _logger = LoggerFactory
            .getLogger(VNXLoginProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        final PostMethod postMethod = (PostMethod) resultObj;
        try {
            Header[] headers = postMethod.getResponseHeaders("Set-Cookie");
            if (null != headers && headers.length > 0) {
                keyMap.put(VNXFileConstants.COOKIE, headers[0].getValue());
                _logger.debug("Recieved cookie information from the Server.");
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the login response due to {}",
                    ex.getMessage());
            throw new VNXFilePluginException(
                    "Exception occurred while processing the login response.",
                    ex.getCause());
        } finally {
            postMethod.releaseConnection();
        }

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {

    }

}
