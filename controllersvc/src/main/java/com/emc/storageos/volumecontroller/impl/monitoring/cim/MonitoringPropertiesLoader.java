/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring.cim;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

@Component
public class MonitoringPropertiesLoader {

    /**
     * represents to log the indications or not. property will be set based on
     * the value provided in monitoring.properties during initialization
     * automatically.
     */
    private @Value("#{mntrProperties['monitoring.isIndicationsToLog']}")
    String _isToLogIndications;

    /**
     * In Active Operational status codes for block related events
     */
    private @Value("#{mntrProperties['block.event.active.operationalStatus.codes']}")
    String _blockEventActiveOSCodes;

    /**
     * In Active Operational status descriptions for block related events
     */
    private @Value("#{mntrProperties['block.event.active.operationalStatus.descriptions']}")
    String _blockEventActiveOSDescs;

    /**
     * In Active Operational status codes for file related events
     */
    private @Value("#{mntrProperties['file.event.active.operationalStatus.codes']}")
    String _fileEventActiveOSCodes;

    /**
     * In Active Operational status descriptions for file related events
     */
    private @Value("#{mntrProperties['file.event.active.operationalStatus.descriptions']}")
    String _fileEventActiveOSDescs;

    public Boolean isToLogIndications() {
        if (_isToLogIndications != null && _isToLogIndications.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    /**
     * This Method will help you to split the String separated with comma into a String Array 
     * @param value
     * @return
     */
    public static String[] splitStringIntoArray(String value) {
        String[] values = new String[0];
        if (value != null && value.length() > 0) {
            if (value.indexOf(CIMConstants.COMMA_SEPERATOR) != -1) {
                values = value.split(",");
            } else {
                values = new String[1];
                values[0] = value;
            }
        }
        return values;
    }

    /**
     * Returns the block related operational status codes defined in properties
     * in a List format
     * 
     * @return
     */
    public List<String> getBlockEventActiveOSCodes() {
        String[] fileCodes = splitStringIntoArray(_blockEventActiveOSCodes);
        List<String> values = Arrays.asList(fileCodes);
        return values;
    }

    /**
     * Returns the file related operational status codes defined in properties
     * in a List format
     * 
     * @return
     */
    public List<String> getFileSystemEventActiveOSCodes() {
        String[] fileCodes = splitStringIntoArray(_fileEventActiveOSCodes);
        List<String> values = Arrays.asList(fileCodes);
        return values;
    }

    /**
     * Returns the block related operational status descriptions defined in
     * properties in a List format
     * 
     * @return
     */
    public List<String> getBlockEventActiveOSDescs() {
        String[] fileDescs = splitStringIntoArray(_blockEventActiveOSDescs);
        List<String> values = Arrays.asList(fileDescs);
        return values;
    }

    /**
     * Returns the file related operational status descriptions defined in
     * properties in a List format
     * 
     * @return
     */
    public List<String> getFileSystemEventActiveOSDescs() {
        String[] fileDescs = splitStringIntoArray(_fileEventActiveOSDescs);
        List<String> values = Arrays.asList(fileDescs);
        return values;
    }

}
