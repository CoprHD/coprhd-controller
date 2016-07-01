/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.bean;

/**
 * Java bean class for "sloprovisioning/symmetrix/{}/director/{}/port/{}" GET method
 * JSON result deserialization(type = "GigE").
 *
 * Created by gang on 6/30/16.
 */
public class SymmetrixPortIscsi implements SymmetrixPort {
    private String director_status;
    private Integer num_of_cores;
    private Boolean aclx;
    private Boolean environ_set;
    private Integer num_of_mapped_vols;
    private Integer num_of_masking_views;
    private String type;
    private Boolean avoid_reset_broadcast;
    private Boolean disable_q_reset_on_ua;
    private Boolean sunapee;
    private String port_status;
    private Boolean volume_set_addressing;
    private Boolean vnx_attached;
    private Boolean scsi_support1;
    private Boolean hp_3000_mode;
    private String identifier;
    private SymmetrixPortKey symmetrixPortKey;
    private Boolean negotiate_reset;
    private Boolean enable_auto_negotive;
    private Boolean spc2_protocol_version;
    private Boolean siemens;
    private String negotiated_speed;
    private Integer num_of_port_groups;
    private Boolean common_serial_number;
    private Boolean scsi_3;
    private Boolean soft_reset;
    private String vcm_state;

    @Override
    public String toString() {
        return "SymmetrixPortIscsi{" +
            "director_status='" + director_status + '\'' +
            ", num_of_cores=" + num_of_cores +
            ", aclx=" + aclx +
            ", environ_set=" + environ_set +
            ", num_of_mapped_vols=" + num_of_mapped_vols +
            ", num_of_masking_views=" + num_of_masking_views +
            ", type='" + type + '\'' +
            ", avoid_reset_broadcast=" + avoid_reset_broadcast +
            ", disable_q_reset_on_ua=" + disable_q_reset_on_ua +
            ", sunapee=" + sunapee +
            ", port_status='" + port_status + '\'' +
            ", volume_set_addressing=" + volume_set_addressing +
            ", vnx_attached=" + vnx_attached +
            ", scsi_support1=" + scsi_support1 +
            ", hp_3000_mode=" + hp_3000_mode +
            ", identifier='" + identifier + '\'' +
            ", symmetrixPortKey=" + symmetrixPortKey +
            ", negotiate_reset=" + negotiate_reset +
            ", enable_auto_negotive=" + enable_auto_negotive +
            ", spc2_protocol_version=" + spc2_protocol_version +
            ", siemens=" + siemens +
            ", negotiated_speed='" + negotiated_speed + '\'' +
            ", num_of_port_groups=" + num_of_port_groups +
            ", common_serial_number=" + common_serial_number +
            ", scsi_3=" + scsi_3 +
            ", soft_reset=" + soft_reset +
            ", vcm_state='" + vcm_state + '\'' +
            '}';
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

    public Boolean getAclx() {
        return aclx;
    }

    public void setAclx(Boolean aclx) {
        this.aclx = aclx;
    }

    public Boolean getEnviron_set() {
        return environ_set;
    }

    public void setEnviron_set(Boolean environ_set) {
        this.environ_set = environ_set;
    }

    public Integer getNum_of_mapped_vols() {
        return num_of_mapped_vols;
    }

    public void setNum_of_mapped_vols(Integer num_of_mapped_vols) {
        this.num_of_mapped_vols = num_of_mapped_vols;
    }

    public Integer getNum_of_masking_views() {
        return num_of_masking_views;
    }

    public void setNum_of_masking_views(Integer num_of_masking_views) {
        this.num_of_masking_views = num_of_masking_views;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getAvoid_reset_broadcast() {
        return avoid_reset_broadcast;
    }

    public void setAvoid_reset_broadcast(Boolean avoid_reset_broadcast) {
        this.avoid_reset_broadcast = avoid_reset_broadcast;
    }

    public Boolean getDisable_q_reset_on_ua() {
        return disable_q_reset_on_ua;
    }

    public void setDisable_q_reset_on_ua(Boolean disable_q_reset_on_ua) {
        this.disable_q_reset_on_ua = disable_q_reset_on_ua;
    }

    public Boolean getSunapee() {
        return sunapee;
    }

    public void setSunapee(Boolean sunapee) {
        this.sunapee = sunapee;
    }

    public String getPort_status() {
        return port_status;
    }

    public void setPort_status(String port_status) {
        this.port_status = port_status;
    }

    public Boolean getVolume_set_addressing() {
        return volume_set_addressing;
    }

    public void setVolume_set_addressing(Boolean volume_set_addressing) {
        this.volume_set_addressing = volume_set_addressing;
    }

    public Boolean getVnx_attached() {
        return vnx_attached;
    }

    public void setVnx_attached(Boolean vnx_attached) {
        this.vnx_attached = vnx_attached;
    }

    public Boolean getScsi_support1() {
        return scsi_support1;
    }

    public void setScsi_support1(Boolean scsi_support1) {
        this.scsi_support1 = scsi_support1;
    }

    public Boolean getHp_3000_mode() {
        return hp_3000_mode;
    }

    public void setHp_3000_mode(Boolean hp_3000_mode) {
        this.hp_3000_mode = hp_3000_mode;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public SymmetrixPortKey getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    public void setSymmetrixPortKey(SymmetrixPortKey symmetrixPortKey) {
        this.symmetrixPortKey = symmetrixPortKey;
    }

    public Boolean getNegotiate_reset() {
        return negotiate_reset;
    }

    public void setNegotiate_reset(Boolean negotiate_reset) {
        this.negotiate_reset = negotiate_reset;
    }

    public Boolean getEnable_auto_negotive() {
        return enable_auto_negotive;
    }

    public void setEnable_auto_negotive(Boolean enable_auto_negotive) {
        this.enable_auto_negotive = enable_auto_negotive;
    }

    public Boolean getSpc2_protocol_version() {
        return spc2_protocol_version;
    }

    public void setSpc2_protocol_version(Boolean spc2_protocol_version) {
        this.spc2_protocol_version = spc2_protocol_version;
    }

    public Boolean getSiemens() {
        return siemens;
    }

    public void setSiemens(Boolean siemens) {
        this.siemens = siemens;
    }

    public String getNegotiated_speed() {
        return negotiated_speed;
    }

    public void setNegotiated_speed(String negotiated_speed) {
        this.negotiated_speed = negotiated_speed;
    }

    public Integer getNum_of_port_groups() {
        return num_of_port_groups;
    }

    public void setNum_of_port_groups(Integer num_of_port_groups) {
        this.num_of_port_groups = num_of_port_groups;
    }

    public Boolean getCommon_serial_number() {
        return common_serial_number;
    }

    public void setCommon_serial_number(Boolean common_serial_number) {
        this.common_serial_number = common_serial_number;
    }

    public Boolean getScsi_3() {
        return scsi_3;
    }

    public void setScsi_3(Boolean scsi_3) {
        this.scsi_3 = scsi_3;
    }

    public Boolean getSoft_reset() {
        return soft_reset;
    }

    public void setSoft_reset(Boolean soft_reset) {
        this.soft_reset = soft_reset;
    }

    public String getVcm_state() {
        return vcm_state;
    }

    public void setVcm_state(String vcm_state) {
        this.vcm_state = vcm_state;
    }
}
