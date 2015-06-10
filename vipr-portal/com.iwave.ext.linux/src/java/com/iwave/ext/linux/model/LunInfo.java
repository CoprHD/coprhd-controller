/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.model;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class LunInfo {
    private int host;
    private int channel;
    private int id;
    private int lun;
    private String vendor;
    private String model;
    private String revision;
    private String type;
    private String scsiRevision;

    public int getHost() {
        return host;
    }

    public void setHost(int host) {
        this.host = host;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getLun() {
        return lun;
    }

    public void setLun(int lun) {
        this.lun = lun;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getScsiRevision() {
        return scsiRevision;
    }

    public void setScsiRevision(String scsiRevision) {
        this.scsiRevision = scsiRevision;
    }

    public String toString() {
        ToStringBuilder sb = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        sb.append("host", host);
        sb.append("channel", channel);
        sb.append("id", id);
        sb.append("lun", lun);
        sb.append("vendor", vendor);
        sb.append("model", model);
        sb.append("revision", revision);
        sb.append("type", type);
        sb.append("scsiRevision", scsiRevision);
        return sb.toString();
    }
}
