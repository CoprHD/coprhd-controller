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
    private String _vdmState;

    public VNXVdm() {
    }

    public VNXVdm(String vdmName, String moverId, String vdmId, String vdmState) {
        _vdmName = vdmName;
        _moverId = moverId;
        _vdmId = vdmId;
        _vdmState = vdmState;
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

    public String getState() {
        return _vdmState;
    }

    public void setState(String state) {
        this._vdmState = state;
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
