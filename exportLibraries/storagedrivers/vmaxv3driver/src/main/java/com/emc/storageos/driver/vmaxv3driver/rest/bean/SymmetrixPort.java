/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.bean;

/**
 * Java bean class for "sloprovisioning/symmetrix/{}/director/{}/port/{}" GET method
 * JSON result deserialization.
 *
 * Created by gang on 6/24/16.
 */
public class SymmetrixPort {
    private String port_interface;
    private String director_status;
    private Integer num_of_cores;
    private SymmetrixPortKey symmetrixPortKey;
    private String port_status;
    private String type;
    private Integer num_of_hypers;

    @Override
    public String toString() {
        return "SymmetrixPort{" +
            "port_interface='" + port_interface + '\'' +
            ", director_status='" + director_status + '\'' +
            ", num_of_cores=" + num_of_cores +
            ", symmetrixPortKey=" + symmetrixPortKey +
            ", port_status='" + port_status + '\'' +
            ", type='" + type + '\'' +
            ", num_of_hypers=" + num_of_hypers +
            '}';
    }

    public String getPort_interface() {
        return port_interface;
    }

    public void setPort_interface(String port_interface) {
        this.port_interface = port_interface;
    }

    public String getDirector_status() {
        return director_status;
    }

    public void setDirector_status(String director_status) {
        this.director_status = director_status;
    }

    public Integer getNum_of_cores() {
        return num_of_cores;
    }

    public void setNum_of_cores(Integer num_of_cores) {
        this.num_of_cores = num_of_cores;
    }

    public SymmetrixPortKey getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    public void setSymmetrixPortKey(SymmetrixPortKey symmetrixPortKey) {
        this.symmetrixPortKey = symmetrixPortKey;
    }

    public String getPort_status() {
        return port_status;
    }

    public void setPort_status(String port_status) {
        this.port_status = port_status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getNum_of_hypers() {
        return num_of_hypers;
    }

    public void setNum_of_hypers(Integer num_of_hypers) {
        this.num_of_hypers = num_of_hypers;
    }
}
