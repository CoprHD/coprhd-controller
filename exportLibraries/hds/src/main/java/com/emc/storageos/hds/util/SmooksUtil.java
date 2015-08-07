/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.util;

import java.io.InputStream;

import javax.xml.transform.stream.StreamSource;

import org.milyn.Smooks;
import org.milyn.container.ExecutionContext;
import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hds.HDSException;

public class SmooksUtil {

    private static final Logger log = LoggerFactory.getLogger(SmooksUtil.class);

    /**
     * This initializes the Smooks & parse the given inputStream and returns the javaResult.
     * 
     * @param inputStream : Response in inputStream received from server.
     * @param configFile : Smooks configuration file.
     * @return
     */
    public static JavaResult getParsedXMLJavaResult(InputStream inputStream, String configFile) {
        Smooks smooks = null;
        JavaResult javaResult = null;
        try {
            smooks = new Smooks(configFile);
            log.debug("initialized smooks");
            ExecutionContext executionContext = smooks.createExecutionContext();
            // The result of this transform is a set of Java objects...
            javaResult = new JavaResult();

            // Filter the input message to extract, using the execution context...
            smooks.filterSource(executionContext, new StreamSource(inputStream), javaResult);
            log.debug("Parsing completed");
        } catch (Exception e) {
            log.error("Unable to parse the response received from server.", e);
            throw HDSException.exceptions.unableToParseResponse();
        } finally {
            if (null != smooks) {
                smooks.close();
            }
        }
        return javaResult;
    }

}
