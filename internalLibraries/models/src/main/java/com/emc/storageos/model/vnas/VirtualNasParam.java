package com.emc.storageos.model.vnas;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class VirtualNasParam implements Serializable {

    private static final long serialVersionUID = -2256076906843969208L;

    private List<String> addIpAdds;
    private List<String> addStorageUnits;
    private Set<String> addProtocols;
    private String ipSpace;

    @XmlElementWrapper(name = "add_ip_addresses")
    @XmlElement(required = true, name = "add_ip_address")
    public List<String> getAddIpAdds() {
        return addIpAdds;
    }

    public void setAddIpAdds(List<String> addIpAdds) {
        this.addIpAdds = addIpAdds;
    }

    @XmlElementWrapper(name = "add_storage_units")
    @XmlElement(required = true, name = "add_storage_unit")
    public List<String> getAddStorageUnits() {
        return addStorageUnits;
    }

    public void setAddStorageUnits(List<String> addStorageUnits) {
        this.addStorageUnits = addStorageUnits;
    }

    @XmlElementWrapper(name = "add_protocols")
    @XmlElement(name = "add_protocol")
    public Set<String> getAddProtocols() {
        return addProtocols;
    }

    public void setAddProtocols(Set<String> addProtocols) {
        this.addProtocols = addProtocols;
    }

    @XmlElement(name = "ip_space")
    public String getIpSpace() {
        return ipSpace;
    }

    public void setIpSpace(String ipSpace) {
        this.ipSpace = ipSpace;
    }

}
