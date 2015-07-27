/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnx.xmlapi;

public class VNXControlStation extends VNXBaseClass {
    private String _serialNumber;
    private String _softwareVersion;

    public VNXControlStation() {
    }

    public VNXControlStation(String serialNum, String softwareVer) {
        _serialNumber    = serialNum;
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

