/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.iso;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Class used to create ISO 9660 image.
 */
public class CreateISO {
    private static final Logger _log = LoggerFactory
            .getLogger(CreateISO.class);

    /**
     * Method that creates ISO 9660 image having properties file directly under root
     * directory.
     * 
     * @param propertyInfo Properties to be copied to iso image.
     * @return ISO image as byte array
     */
    public static byte[] getBytes(Map<String, String> ovfPropertiesMap, Map<String, String> mutatedPropertiesMap) {

        String ovfProperties = formatProperties(ovfPropertiesMap);
        String overrideProperties = formatProperties(mutatedPropertiesMap);

        ISOBuffer isoBuffer = new ISOBuffer();

        isoBuffer.addFile(ISOConstants.CONTROLLER_OVF_FILE_NAME, ovfProperties.getBytes());
        isoBuffer.addFile(ISOConstants.OVERRIDES_FILE_NAME, overrideProperties.getBytes());

        return isoBuffer.createISO();
    }

    private static String formatProperties(Map<String, String> properties) {
        StringBuffer props = new StringBuffer();
        PropertyMetadata metadata;
        String equal = "=";
        String newLine = "\n";

        // Getting properties metadata
        Map<String, PropertyMetadata> metadataMap = PropertiesMetadata.getGlobalMetadata();
        if (metadataMap == null) {
            _log.warn("Properties metadata not found");
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Properties metadata");
        }

        // Looping through each property, checking if it is visible to all nodes
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            // Check if this property is visible to all nodes
            metadata = metadataMap.get(entry.getKey());
            if (metadata != null && !metadata.getControlNodeOnly()) {
                props.append(entry.getKey());
                props.append(equal);
                props.append(entry.getValue());
                props.append(newLine);
            }
            else {
                _log.info("Property metadata not found or control only: " + entry.getKey());
            }
        }
        return props.toString();
    }
}
