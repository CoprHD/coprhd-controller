package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;
import com.emc.storageos.model.valid.Range;

public abstract class ManagementStationParam {
    private String type;
    private String osVersion;
    private String name;
    private Integer portNumber;
    private String userName;
    private String password;
    private Boolean useSsl;
    private Boolean discoverable;

    public ManagementStationParam() {
    }

    @XmlElement(required = false)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The operating system version of the host.
     * 
     */
    @XmlElement(name = "os_version", required = false)
    @JsonProperty("os_version")
    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    /**
     * The user label for this host.
     * 
     */
    @Length(min = 2, max = 128)
    @XmlElement()
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The integer port number of the host management interface.
     */
    @XmlElement(name = "port_number")
    @Range(min = 1, max = 65535)
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * The user name used to log in to the host.
     * 
     */
    @XmlElement(name = "user_name")
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * The password credential used to login to the host.
     * 
     */
    @XmlElement(name = "password")
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * The boolean flag that indicates if SSL should be used when communicating with the host.
     * 
     */
    @XmlElement(name = "use_ssl")
    public Boolean getUseSsl() {
        return useSsl;
    }

    public void setUseSsl(Boolean useSsl) {
        this.useSsl = useSsl;
    }

    /**
     * Gets the discoverable flag. Discoverable indicates if automatic discovery should be
     * performed against this host. Defaults to true.
     * 
     * @return true if automatic discovery is enabled, false if automatic discovery is disabled.
     *         default value is true
     */
    @XmlElement(name = "discoverable")
    public Boolean getDiscoverable() {
        return discoverable;
    }

    public void setDiscoverable(Boolean discoverable) {
        this.discoverable = discoverable;
    }

    /** Gets the management Station IP address */
    public abstract String findIpAddress();

}
