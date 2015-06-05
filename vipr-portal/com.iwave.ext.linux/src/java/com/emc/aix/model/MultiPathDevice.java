/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.aix.model;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;

public class MultiPathDevice implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private String device;
    private String vendor;
    private String product;
    private String wwn;
    
    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }
    
    public String toString() {
        String paddedDevice = StringUtils.rightPad(device, 17);
        String paddedVendor = StringUtils.rightPad(vendor, 8);
        String paddedProduct = StringUtils.rightPad(product, 16);
        return String.format("%s:%s:%s:%s", paddedDevice, paddedVendor, paddedProduct, wwn);
    }
    
    public String dump() {
        StringBuilder sb = new StringBuilder("\nMultiPath Device:\n");
        sb.append("\tDevice:\t").append(device);
        sb.append("\tVendor:\t").append(vendor);
        sb.append("\tProduct:\t").append(product);
        sb.append("\tWWN:\t").append(wwn);
        return sb.toString();
    }

    public Object getDeviceName() {
        return StringUtils.substringAfterLast(getDevice(), "/");
    }

}
