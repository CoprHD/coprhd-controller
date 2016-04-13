package com.emc.storageos.model.vnas;

import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "vnas_update")
public class VirtualNasUpdateParam extends VirtualNasParam {

    private static final long serialVersionUID = 6355777536519941066L;

    private List<String> removeIpAdds;
    private List<String> removeStorageUnits;
    private Set<String> removeProtocols;

    @XmlElementWrapper(name = "remove_ip_addresses")
    @XmlElement(required = true, name = "remove_ip_address")
    public List<String> getRemoveIpAdds() {
        return removeIpAdds;
    }

    public void setRemoveIpAdds(List<String> removeIpAdds) {
        this.removeIpAdds = removeIpAdds;
    }

    @XmlElementWrapper(name = "remove_storage_units")
    @XmlElement(required = true, name = "remove_storage_unit")
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
