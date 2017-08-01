/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.InitiatorAndHostFlagsType;

/**
 * @author fengs5
 *
 */
public class HostFlagsType extends InitiatorAndHostFlagsType {

    private VolumeSetAddressingType volume_set_addressing;
    private DisableQResetOnUAType disable_q_reset_on_ua;
    private EnvironSetType environ_set;
    private AvoidResetBroadcastType avoid_reset_broadcast;
    private OpenVMSType openvms;
    private SCSI3Type scsi_3;
    private SPC2ProtocolVersionType spc2_protocol_version;
    private SCSISupport1Type scsi_support1;
    private boolean consistent_lun;

    /**
     * @return the volume_set_addressing
     */
    public VolumeSetAddressingType getVolume_set_addressing() {
        return volume_set_addressing;
    }

    /**
     * @param volume_set_addressing the volume_set_addressing to set
     */
    public void setVolume_set_addressing(VolumeSetAddressingType volume_set_addressing) {
        this.volume_set_addressing = volume_set_addressing;
    }

    /**
     * @return the disable_q_reset_on_ua
     */
    public DisableQResetOnUAType getDisable_q_reset_on_ua() {
        return disable_q_reset_on_ua;
    }

    /**
     * @param disable_q_reset_on_ua the disable_q_reset_on_ua to set
     */
    public void setDisable_q_reset_on_ua(DisableQResetOnUAType disable_q_reset_on_ua) {
        this.disable_q_reset_on_ua = disable_q_reset_on_ua;
    }

    /**
     * @return the environ_set
     */
    public EnvironSetType getEnviron_set() {
        return environ_set;
    }

    /**
     * @param environ_set the environ_set to set
     */
    public void setEnviron_set(EnvironSetType environ_set) {
        this.environ_set = environ_set;
    }

    /**
     * @return the avoid_reset_broadcast
     */
    public AvoidResetBroadcastType getAvoid_reset_broadcast() {
        return avoid_reset_broadcast;
    }

    /**
     * @param avoid_reset_broadcast the avoid_reset_broadcast to set
     */
    public void setAvoid_reset_broadcast(AvoidResetBroadcastType avoid_reset_broadcast) {
        this.avoid_reset_broadcast = avoid_reset_broadcast;
    }

    /**
     * @return the openvms
     */
    public OpenVMSType getOpenvms() {
        return openvms;
    }

    /**
     * @param openvms the openvms to set
     */
    public void setOpenvms(OpenVMSType openvms) {
        this.openvms = openvms;
    }

    /**
     * @return the scsi_3
     */
    public SCSI3Type getScsi_3() {
        return scsi_3;
    }

    /**
     * @param scsi_3 the scsi_3 to set
     */
    public void setScsi_3(SCSI3Type scsi_3) {
        this.scsi_3 = scsi_3;
    }

    /**
     * @return the spc2_protocol_version
     */
    public SPC2ProtocolVersionType getSpc2_protocol_version() {
        return spc2_protocol_version;
    }

    /**
     * @param spc2_protocol_version the spc2_protocol_version to set
     */
    public void setSpc2_protocol_version(SPC2ProtocolVersionType spc2_protocol_version) {
        this.spc2_protocol_version = spc2_protocol_version;
    }

    /**
     * @return the scsi_support1
     */
    public SCSISupport1Type getScsi_support1() {
        return scsi_support1;
    }

    /**
     * @param scsi_support1 the scsi_support1 to set
     */
    public void setScsi_support1(SCSISupport1Type scsi_support1) {
        this.scsi_support1 = scsi_support1;
    }

    /**
     * @return the consistent_lun
     */
    public boolean isConsistent_lun() {
        return consistent_lun;
    }

    /**
     * @param consistent_lun the consistent_lun to set
     */
    public void setConsistent_lun(boolean consistent_lun) {
        this.consistent_lun = consistent_lun;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "HostFlagsType [volume_set_addressing=" + volume_set_addressing + ", disable_q_reset_on_ua=" + disable_q_reset_on_ua
                + ", environ_set=" + environ_set + ", avoid_reset_broadcast=" + avoid_reset_broadcast + ", openvms=" + openvms
                + ", scsi_3=" + scsi_3 + ", spc2_protocol_version=" + spc2_protocol_version + ", scsi_support1=" + scsi_support1
                + ", consistent_lun=" + consistent_lun + "]";
    }

}
