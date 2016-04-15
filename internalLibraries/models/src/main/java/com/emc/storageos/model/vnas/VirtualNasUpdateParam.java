/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vnas;

import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a Virtual NAS, specified
 * during virtual NAS update.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "vnas_update")
public class VirtualNasUpdateParam extends VirtualNasParam {

    private static final long serialVersionUID = 6355777536519941066L;

    // List of IP addresses add to VNAS
    private List<String> addIpAdds;

    // List of Storage Units add to VNAS
    private List<String> addStorageUnits;

    // List of IP addresses remove from VNAS
    private List<String> removeIpAdds;

    // List of Storage Units remove from VNAS
    private List<String> removeStorageUnits;

    // List of protocols remove from VNAS
    private Set<String> removeProtocols;

    @XmlElementWrapper(name = "add_ip_addresses")
    @XmlElement(name = "add_ip_address")
    public List<String> getAddIpAdds() {
        return addIpAdds;
    }

    public void setAddIpAdds(List<String> addIpAdds) {
        this.addIpAdds = addIpAdds;
    }

    @XmlElementWrapper(name = "add_storage_units")
    @XmlElement(name = "add_storage_unit")
    public List<String> getAddStorageUnits() {
        return addStorageUnits;
    }

    public void setAddStorageUnits(List<String> addStorageUnits) {
        this.addStorageUnits = addStorageUnits;
    }

    @XmlElementWrapper(name = "remove_ip_addresses")
    @XmlElement(name = "remove_ip_address")
    public List<String> getRemoveIpAdds() {
        return removeIpAdds;
    }

    public void setRemoveIpAdds(List<String> removeIpAdds) {
        this.removeIpAdds = removeIpAdds;
    }

    @XmlElementWrapper(name = "remove_storage_units")
    @XmlElement(name = "remove_storage_unit")
    public List<String> getRemoveStorageUnits() {
        return removeStorageUnits;
    }

    public void setRemoveStorageUnits(List<String> removeStorageUnits) {
        this.removeStorageUnits = removeStorageUnits;
    }

    @XmlElementWrapper(name = "remove_protocols")
    @XmlElement(name = "remove_protocol")
    public Set<String> getRemoveProtocols() {
        return removeProtocols;
    }

    public void setRemoveProtocols(Set<String> removeProtocols) {
        this.removeProtocols = removeProtocols;
    }

}
