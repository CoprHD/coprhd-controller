/**
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

    public void setInterfaces(List<String> interfaces){
        this._interfaces = interfaces;
    }

    public List<String> getInterfaces(){
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

