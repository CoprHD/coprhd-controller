/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnx.xmlapi;

import java.util.List;

public class VNXVdm extends VNXBaseClass {
    private String _moverId;
    private String _vdmId;

    private String _vdmName;

    private List<String> _interfaces;

    public VNXVdm() {
    }

    public VNXVdm(String vdmName, String moverId, String vdmId) {
        _vdmName = vdmName;
        _moverId = moverId;
        _vdmId = vdmId;
    }

    public String getVdmName() {
        return _vdmName;
    }

    public void setVdmName(String vdmName) {
        this._vdmName = vdmName;
    }

    public String getMoverId() {
        return _moverId;
    }

    public void setMoverId(String moverId) {
        _moverId = moverId;
    }

    public String getVdmId() {
        return _vdmId;
    }

    public void setVdmId(String vdmId) {
        _vdmId = vdmId;
    }

    public void setInterfaces(List<String> interfaces) {
        this._interfaces = interfaces;
    }

    public List<String> getInterfaces() {
        return this._interfaces;
    }

    public static String discoverVdm() {
        String xml = requestHeader +
                "\t<Query>\n" +
                "\t<VdmQueryParams/>\n" +
                "\t</Query>\n" +
                requestFooter;

        return xml;
    }
}
