/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnx.xmlapi;

public class VNXControlStation extends VNXBaseClass {
    private String _serialNumber;
    private String _softwareVersion;

    public VNXControlStation() {
    }

    public VNXControlStation(String serialNum, String softwareVer) {
        _serialNumber = serialNum;
        _softwareVersion = softwareVer;
    }

    public String getSerialNumber() {
        return _serialNumber;
    }

    public void setSerialNumber(String serialNum) {
        _serialNumber = serialNum;
    }

    public String getSoftwareVersion() {
        return _softwareVersion;
    }

    public void setSoftwareVersion(String softwareVer) {
        _softwareVersion = softwareVer;
    }

    public static String discoverControlStation() {
        String xml = requestHeader +
                "\t<Query>\n" +
                "\t<CelerraSystemQueryParams/>\n" +
                "\t</Query>\n" +
                requestFooter;

        return xml;
    }
}
