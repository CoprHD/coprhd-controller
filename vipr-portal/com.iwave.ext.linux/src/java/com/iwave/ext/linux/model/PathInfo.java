/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;

public class PathInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private int host;
    private int channel;
    private int id;
    private int lun;
    private String device;
    private String status;

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

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isFailed() {
        return StringUtils.contains(status, "failed") || StringUtils.contains(status, "faulty");
    }

    public String toString() {
        return String.format("%d:%d:%d:%d %s %s", host, channel, id, lun, device, status);
    }
}
