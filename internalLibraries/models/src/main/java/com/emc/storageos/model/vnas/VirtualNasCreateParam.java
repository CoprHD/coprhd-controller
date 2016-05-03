/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vnas;

import java.net.URI;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Attributes associated with a Virtual NAS, specified
 * during virtual NAS creation.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "vnas_create")
public class VirtualNasCreateParam {

    private static final long serialVersionUID = -1618701804694842786L;

    // Name of the Virtual NAS
    private String vNasName;

    // List of IP addresses add to VNAS
    private List<String> vnasInterfaces;

    // List of Storage Units add to VNAS
    // base directory for Isilon access zone
    // file system path for VNX VDM
    // volume path for NetApp vFiler
    private List<String> storageUnits;

    // IP Space for virtual NAS
    // Applicable for NetApp
    private String ipSpace;

    // List of protocols add to VNAS
    private Set<String> protocols;

    // Storage System URI
    private URI storageSystem;

    // IP address of administrative host to be associated with VNAS
    private String adminHostIp;

    // Name of administrative host to be associated with VNAS
    private String adminHostName;

    // DNS domain name to be associated with VNAS
    private String dnsDomain;

    // List of DNS servers add to VNAS
    private List<String> dnsServers;

    // NIS domain name to be associated with VNAS
    private String nisDomain;

    // List of NIS servers add to VNAS
    private List<String> nisServers;

    // Root password for the Virtual NAS
    private String authPassword;

    @XmlElement(required = true, name = "vnas_name")
    public String getvNasName() {
        return vNasName;
    }

    public void setvNasName(String vNasName) {
        this.vNasName = vNasName;
    }

    @XmlElementWrapper(name = "vnas_interfaces")
    @XmlElement(required = true, name = "vnas_interface")
    public List<String> getIpAdds() {
        return vnasInterfaces;
    }

    public void setIpAdds(List<String> vnasInterfaces) {
        this.vnasInterfaces = vnasInterfaces;
    }

    @XmlElementWrapper(name = "storage_units")
    @XmlElement(required = true, name = "storage_unit")
    public List<String> getStorageUnits() {
        return storageUnits;
    }

    public void setStorageUnits(List<String> addStorageUnits) {
        this.storageUnits = addStorageUnits;
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

    @XmlElementWrapper(name = "protocols")
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(Set<String> addProtocols) {
        this.protocols = addProtocols;
    }

    @XmlElement(name = "ip_space")
    public String getIpSpace() {
        return ipSpace;
    }

    public void setIpSpace(String ipSpace) {
        this.ipSpace = ipSpace;
    }

}
