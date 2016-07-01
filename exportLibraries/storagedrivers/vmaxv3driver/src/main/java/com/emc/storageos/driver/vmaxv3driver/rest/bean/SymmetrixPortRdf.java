/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.bean;

/**
 * Java bean class for "sloprovisioning/symmetrix/{}/director/{}/port/{}" GET method
 * JSON result deserialization(type = "RDF-BI-DIR").
 *
 * Created by gang on 6/30/16.
 */
public class SymmetrixPortRdf implements SymmetrixPort {
    private String prevent_automatic_rdf_link_recovery;
    private String director_status;
    private String identifier;
    private Integer num_of_cores;
    private Boolean rdf_ra_group_attributes_farpoint;
    private SymmetrixPortKey symmetrixPortKey;
    private String ipv6_address;
    private String ipv4_domain_name;
    private String ipv4_address;
    private String type;
    private String rdf_software_compression_supported;
    private String rdf_software_compression;
    private String rdf_hardware_compression_supported;
    private String rdf_hardware_compression;
    private String ipv6_prefix;
    private String port_status;
    private String ipv4_default_gateway;
    private String negotiated_speed;
    private String prevent_ra_online_on_power_up;

    @Override
    public String toString() {
        return "SymmetrixPortRdf{" +
            "prevent_automatic_rdf_link_recovery='" + prevent_automatic_rdf_link_recovery + '\'' +
            ", director_status='" + director_status + '\'' +
            ", identifier='" + identifier + '\'' +
            ", num_of_cores=" + num_of_cores +
            ", rdf_ra_group_attributes_farpoint=" + rdf_ra_group_attributes_farpoint +
            ", symmetrixPortKey=" + symmetrixPortKey +
            ", ipv6_address='" + ipv6_address + '\'' +
            ", ipv4_domain_name='" + ipv4_domain_name + '\'' +
            ", ipv4_address='" + ipv4_address + '\'' +
            ", type='" + type + '\'' +
            ", rdf_software_compression_supported='" + rdf_software_compression_supported + '\'' +
            ", rdf_software_compression='" + rdf_software_compression + '\'' +
            ", rdf_hardware_compression_supported='" + rdf_hardware_compression_supported + '\'' +
            ", rdf_hardware_compression='" + rdf_hardware_compression + '\'' +
            ", ipv6_prefix='" + ipv6_prefix + '\'' +
            ", port_status='" + port_status + '\'' +
            ", ipv4_default_gateway='" + ipv4_default_gateway + '\'' +
            ", negotiated_speed='" + negotiated_speed + '\'' +
            ", prevent_ra_online_on_power_up='" + prevent_ra_online_on_power_up + '\'' +
            '}';
    }

    public String getPrevent_automatic_rdf_link_recovery() {
        return prevent_automatic_rdf_link_recovery;
    }

    public void setPrevent_automatic_rdf_link_recovery(String prevent_automatic_rdf_link_recovery) {
        this.prevent_automatic_rdf_link_recovery = prevent_automatic_rdf_link_recovery;
    }

    public String getDirector_status() {
        return director_status;
    }

    public void setDirector_status(String director_status) {
        this.director_status = director_status;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Integer getNum_of_cores() {
        return num_of_cores;
    }

    public void setNum_of_cores(Integer num_of_cores) {
        this.num_of_cores = num_of_cores;
    }

    public Boolean getRdf_ra_group_attributes_farpoint() {
        return rdf_ra_group_attributes_farpoint;
    }

    public void setRdf_ra_group_attributes_farpoint(Boolean rdf_ra_group_attributes_farpoint) {
        this.rdf_ra_group_attributes_farpoint = rdf_ra_group_attributes_farpoint;
    }

    public SymmetrixPortKey getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    public void setSymmetrixPortKey(SymmetrixPortKey symmetrixPortKey) {
        this.symmetrixPortKey = symmetrixPortKey;
    }

    public String getIpv6_address() {
        return ipv6_address;
    }

    public void setIpv6_address(String ipv6_address) {
        this.ipv6_address = ipv6_address;
    }

    public String getIpv4_domain_name() {
        return ipv4_domain_name;
    }

    public void setIpv4_domain_name(String ipv4_domain_name) {
        this.ipv4_domain_name = ipv4_domain_name;
    }

    public String getIpv4_address() {
        return ipv4_address;
    }

    public void setIpv4_address(String ipv4_address) {
        this.ipv4_address = ipv4_address;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRdf_software_compression_supported() {
        return rdf_software_compression_supported;
    }

    public void setRdf_software_compression_supported(String rdf_software_compression_supported) {
        this.rdf_software_compression_supported = rdf_software_compression_supported;
    }

    public String getRdf_software_compression() {
        return rdf_software_compression;
    }

    public void setRdf_software_compression(String rdf_software_compression) {
        this.rdf_software_compression = rdf_software_compression;
    }

    public String getRdf_hardware_compression_supported() {
        return rdf_hardware_compression_supported;
    }

    public void setRdf_hardware_compression_supported(String rdf_hardware_compression_supported) {
        this.rdf_hardware_compression_supported = rdf_hardware_compression_supported;
    }

    public String getRdf_hardware_compression() {
        return rdf_hardware_compression;
    }

    public void setRdf_hardware_compression(String rdf_hardware_compression) {
        this.rdf_hardware_compression = rdf_hardware_compression;
    }

    public String getIpv6_prefix() {
        return ipv6_prefix;
    }

    public void setIpv6_prefix(String ipv6_prefix) {
        this.ipv6_prefix = ipv6_prefix;
    }

    public String getPort_status() {
        return port_status;
    }

    public void setPort_status(String port_status) {
        this.port_status = port_status;
    }

    public String getIpv4_default_gateway() {
        return ipv4_default_gateway;
    }

    public void setIpv4_default_gateway(String ipv4_default_gateway) {
        this.ipv4_default_gateway = ipv4_default_gateway;
    }

    public String getNegotiated_speed() {
        return negotiated_speed;
    }

    public void setNegotiated_speed(String negotiated_speed) {
        this.negotiated_speed = negotiated_speed;
    }

    public String getPrevent_ra_online_on_power_up() {
        return prevent_ra_online_on_power_up;
    }

    public void setPrevent_ra_online_on_power_up(String prevent_ra_online_on_power_up) {
        this.prevent_ra_online_on_power_up = prevent_ra_online_on_power_up;
    }
}
