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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VNXStoragePool extends VNXBaseClass {

    /**
     * Logger instance to log messages.
     */
    private static final Logger _logger = LoggerFactory.getLogger(VNXStoragePool.class);
    
    private String _name;
    private String _description;
    private String _poolId;
    private String _movers;
    private String _size;
    private String _autoSize;
    private String _mayContainSlices;
    private String _virtualProv;
    private String _diskType;
    private String _memberVolumes;
    private String _usedSize;
    private String _dynamic = "false";

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();

        builder.append("\nname :" + getName() + "\n");
        builder.append("description :" + getDescription() + "\n");
        builder.append("poolId :" + getPoolId() + "\n");
        builder.append("movers :" + getMovers() + "\n");
        builder.append("size :" + getSize() + "\n");
        builder.append("autoSize :" + getAutoSize() + "\n");
        builder.append("mayContainSlices :" + getMayContainSlices() + "\n");
        builder.append("virtualProv :" + getVirtualProv() + "\n");
        builder.append("diskType :" + getDiskType() + "\n");
        builder.append("memberVolumes :" + getMemberVolumes() + "\n");
        builder.append("usedSize :" + getUsedSize() + "\n");
        builder.append("dynamic : " + getDynamic() + "\n");

        return builder.toString();
    }
    public VNXStoragePool() {
    }

    public VNXStoragePool(String poolId) {
        _poolId = poolId;
    }

    public String getPoolId() {
        return _poolId;
    }

    public void setPoolId(String poolId) {
        _poolId = poolId;
    }

    public String getVirtualProv() {
        return _virtualProv;
    }

    public void setVirtualProv(String virtualProv) {
        _virtualProv = virtualProv;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
    }

    public String getMovers() {
        return _movers;
    }

    public void setMovers(String movers) {
        _movers = movers;
    }

    public String getSize() {
        return _size;
    }

    public void setSize(String size) {
        _size = size;
    }

    public String getAutoSize() {
        return _autoSize;
    }

    public void setAutoSize(String autoSize) {
        _autoSize = autoSize;
    }

    public String getMayContainSlices() {
        return _mayContainSlices;
    }

    public void setMayContainSlices(String mayContainSlices) {
        _mayContainSlices = mayContainSlices;
    }

    public String getDiskType() {
        return _diskType;
    }

    public void setDiskType(String diskType) {
        _diskType = diskType;
    }

    public String getMemberVolumes() {
        return _memberVolumes;
    }

    public void setMemberVolumes(String memberVolumes) {
        _memberVolumes = memberVolumes;
    }

    public String getUsedSize() {
        return _usedSize;
    }

    public void setUsedSize(String usedSize) {
        _usedSize = usedSize;
    }

    public String getDynamic() {
        return _dynamic;
    }

    public void setDynamic(String dynamic) {
        _dynamic = dynamic;
    }

    public static String discoverControlStation() {
        String xml = requestHeader +
                "\t<Query>\n" +
                "\t<StoragePoolQueryParams/>\n" +
                "\t</Query>\n" +
                requestFooter;

        return xml;
    }

}
