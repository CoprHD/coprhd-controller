/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

@XmlRootElement()
public class NetAppDevice implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String DEVICE_TYPE = "NetAppDevice";

    public static final String IP_KEY = "device.ip";
    public static final String PORT_KEY = "device.port";
    public static final String USR_KEY = "device.usr";
    public static final String PWD_KEY = "device.pwd";
    public static final String SECURE_KEY = "device.secure";
    public static final String PASS_WD = "***";

    private String label;
    private String host;
    private int port;
    boolean secure;
    private String username;
    private String password;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("label", label);
        builder.append("host", host);
        builder.append("port", port);
        builder.append("secure", secure);
        builder.append("username", username);
        builder.append("password", PASS_WD);
        return builder.toString();
    }

    public Map<String, String> connectionInfoMap() {
        Map<String, String> info = new HashMap<String, String>();
        info.put(IP_KEY, host);
        info.put(PORT_KEY, port + "");
        info.put(USR_KEY, username);
        info.put(PWD_KEY, password);
        info.put(SECURE_KEY, secure + "");
        return info;
    }

}
