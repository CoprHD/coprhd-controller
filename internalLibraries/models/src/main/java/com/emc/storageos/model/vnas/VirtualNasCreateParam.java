package com.emc.storageos.model.vnas;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "vnas_create")
public class VirtualNasCreateParam extends VirtualNasParam {

    private static final long serialVersionUID = -1618701804694842786L;

    private String vNasName;
    private URI storageSystem;
    private String adminHostIp;
    private String adminHostName;
    private String dnsDomain;
    private List<String> dnsServers;
    private String nisDomain;
    private List<String> nisServers;
    private String authPassword;

    @XmlElement(required = true, name = "vnas_name")
    public String getvNasName() {
        return vNasName;
    }

    public void setvNasName(String vNasName) {
        this.vNasName = vNasName;
    }

    @XmlElement(required = true, name = "storage_system")
    public URI getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
    }

    @XmlElement(required = true, name = "admin_host_ip")
    public String getAdminHostIp() {
        return adminHostIp;
    }

    public void setAdminHostIp(String adminHostIp) {
        this.adminHostIp = adminHostIp;
    }

    @XmlElement(name = "admin_host_name")
    public String getAdminHostName() {
        return adminHostName;
    }

    public void setAdminHostName(String adminHostName) {
        this.adminHostName = adminHostName;
    }

    @XmlElement(required = true, name = "dns_domain")
    public String getDnsDomain() {
        return dnsDomain;
    }

    public void setDnsDomain(String dnsDomain) {
        this.dnsDomain = dnsDomain;
    }

    @XmlElementWrapper(name = "dns_servers")
    @XmlElement(required = true, name = "dns_server")
    public List<String> getDnsServers() {
        return dnsServers;
    }

    public void setDnsServers(List<String> dnsServers) {
        this.dnsServers = dnsServers;
    }

    @XmlElement(required = true, name = "nis_domain")
    public String getNisDomain() {
        return nisDomain;
    }

    public void setNisDomain(String nisDomain) {
        this.nisDomain = nisDomain;
    }

    @XmlElementWrapper(name = "nis_servers")
    @XmlElement(required = true, name = "nis_server")
    public List<String> getNisServers() {
        return nisServers;
    }

    public void setNisServers(List<String> nisServers) {
        this.nisServers = nisServers;
    }

    @XmlElement(required = true, name = "auth_password")
    @Length(min = 8, max = 15)
    public String getAuthPassword() {
        return authPassword;
    }

    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

}
