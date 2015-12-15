/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
/* You either get an attach volume request or a detach volume request */

import com.google.gson.annotations.SerializedName;

@XmlRootElement
public class VolumeActionRequest {

    @XmlElement(name = "os-initialize_connection")
    @SerializedName("os-initialize_connection")
    public AttachVolume attach = new AttachVolume();

    @SerializedName("os-terminate_connection")
    @XmlElement(name = "os-terminate_connection")
    public DetachVolume detach = new DetachVolume();

    @SerializedName("os-attach")
    @XmlElement(name = "os-attach")
    public AttachToInstance attachToInstance = new AttachToInstance();

    @SerializedName("os-unreserve")
    @XmlElement(name = "os-unreserve")
    public UnReserveVolume unReserve = new UnReserveVolume();

    @SerializedName("os-reserve")
    @XmlElement(name = "os-reserve")
    public ReserveVolume reserveVol = new ReserveVolume();

    @SerializedName("os-detach")
    @XmlElement(name = "os-detach")
    public ReserveVolume tmp = new ReserveVolume();

    @SerializedName("os-begin_detaching")
    @XmlElement(name = "os-begin_detaching")
    public ReserveVolume tmp1 = new ReserveVolume();

    @SerializedName("os-set_bootable")
    @XmlElement(name = "os-set_bootable")
    public BootableVolume bootVol = new BootableVolume();

    @XmlRootElement
    public class BootableVolume
    {
        public String bootable;
    }

    @SerializedName("os-update_readonly_flag")
    @XmlElement(name = "os-update_readonly_flag")
    public ReadOnlyVolume readonlyVol = new ReadOnlyVolume();

    @XmlRootElement
    public class ReadOnlyVolume
    {
        public String readonly;
    }

    @SerializedName("os-extend")
    @XmlElement(name = "os-extend")
    public ExtendVolume extendVol = new ExtendVolume();

    @XmlRootElement
    public class ExtendVolume
    {
        public Long new_size;
    }

    /*
     * @XmlElement(name="os-initialize_connection")
     * public AttachVolume getAttach() {
     * return attach;
     * }
     * 
     * public void setAttach(AttachVolume attach) {
     * //this.attach = new AttachVolume();
     * this.attach = attach;
     * }
     * 
     * 
     * public AttachToInstance getAttachToInstance() {
     * return attachToInstance;
     * }
     * 
     * public void setAttachToInstance(AttachToInstance attach) {
     * //this.attachToInstance = new AttachToInstance();
     * this.attachToInstance = attach;
     * }
     * 
     * 
     * public DetachVolume getDetach() {
     * return detach;
     * }
     * 
     * public void setDetach(DetachVolume detach) {
     * //this.detach = new DetachVolume();
     * this.detach = detach;
     * }
     * 
     * 
     * public UnReserveVolume getUnReserve() {
     * return unReserve;
     * }
     * 
     * public void setUnReserve(UnReserveVolume unReserve) {
     * //this.unReserve = new UnReserveVolume();
     * this.unReserve = unReserve;
     * this.unReserve.operation = "unreserve";
     * }
     * 
     * 
     * public ReserveVolume getReserveVol() {
     * return reserveVol;
     * }
     * 
     * public void setReserveVol(ReserveVolume reserveVol) {
     * //this.reserveVol = new ReserveVolume();
     * this.reserveVol = reserveVol;
     * this.reserveVol.operation = "reserve";
     * 
     * }
     */

    @XmlRootElement
    public class ReserveVolume
    {
        public String operation = null;
    }

    @XmlRootElement
    public class UnReserveVolume
    {
        public String operation = null;
    }

    @XmlRootElement
    public class AttachVolume {

        public Connector connector;

        // @XmlElement
        public Connector getConnector() {
            return connector;
        }

        public void setConnector(Connector connector) {
            this.connector = connector;
        }

    }

    @XmlRootElement
    public class DetachVolume
    {
        public Connector connector = new Connector();
    }

    @XmlRootElement
    public class AttachToInstance
    {
        public String instance_uuid;
        public String mountpoint;
        public String mode;
    }

    /*
     * @Override
     * public String toString() {
     * StringBuilder builder = new StringBuilder();
     * builder.append("Connector [attach=");
     * builder.append(attach.toString());
     * builder.append(", reserve=");
     * builder.append(reserveVol.toString());
     * builder.append("]");
     * return builder.toString();
     * }
     */

}
