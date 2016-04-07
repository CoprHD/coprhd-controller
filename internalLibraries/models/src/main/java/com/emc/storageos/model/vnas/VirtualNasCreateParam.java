package com.emc.storageos.model.vnas;

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@SuppressWarnings("serial")
@XmlRootElement(name = "vnas_create")
public class VirtualNasCreateParam implements Serializable {

    private String vNasName;
    private List<String> ipAddresses;
    private String ipSpace;
    private List<String> storageUnits;
    private URI storageSystem;
    private Set<String> protocols;

    @XmlElement(required = true, name = "vnas_name")
    public String getvNasName() {
        return vNasName;
    }

    public void setvNasName(String vNasName) {
        this.vNasName = vNasName;
    }

    @XmlElementWrapper(name = "ip_addresses")
    @XmlElement(required = true, name = "ip_address")
    public List<String> getIpAddresses() {
        return ipAddresses;
    }

    public void setIpAddresses(List<String> ipAddresses) {
        this.ipAddresses = ipAddresses;
    }

    @XmlElement(name = "ip_space")
    public String getIpSpace() {
        return ipSpace;
    }

    public void setIpSpace(String ipSpace) {
        this.ipSpace = ipSpace;
    }

    @XmlElementWrapper(name = "storage_units")
    @XmlElement(required = true, name = "storage_unit")
    public List<String> getStorageUnits() {
        return storageUnits;
    }

    public void setStorageUnits(List<String> storageUnits) {
        this.storageUnits = storageUnits;
    }

    @XmlElement(required = true, name = "storage_system")
    public URI getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
    }

    @XmlElementWrapper(name = "protocols")
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }
}
