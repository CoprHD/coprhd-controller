/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;

/**
 * An object that represents a single export path between an initiator and a 
 * block object (volume or snapshot). When a volume is successfully exported
 * to an initiator, there will be a minimum of one instance of {@link ITLRestRep}.
 * When multi-pathing is used, there could be as many as the number of paths specified
 * depending on the availability of storage ports.
 * 
 * @author elalih
 *
 */
@XmlRootElement(name = "itl")
public class ITLRestRep {
    private int hlu;
    private ITLBlockObjectRestRep blockObject = new ITLBlockObjectRestRep();
    private ITLInitiatorRestRep initiator = new ITLInitiatorRestRep();
    private ITLStoragePortRestRep storagePort = new ITLStoragePortRestRep();
    private NamedRelatedResourceRep export = new NamedRelatedResourceRep();
    private String sanZoneName;

    /**
     * The storage device containing the targets.
     * @valid none
     * @return ITLBlockObjectRestRep
     */
    @XmlElement(name = "device")
    public ITLBlockObjectRestRep getBlockObject() {
        return blockObject;
    }

    public void setBlockObject(ITLBlockObjectRestRep blockObject) {
        this.blockObject = blockObject;
    }

    /**
     * The Storage Port being used for access to the volume.
     * @valid none
     * @return ITLStoragePortRestRep
     */
    @XmlElement(name = "target")
    public ITLStoragePortRestRep getStoragePort() {
        return storagePort;
    }

    public void setStoragePort(ITLStoragePortRestRep storagePort) {
        this.storagePort = storagePort;
    }

    /**
     * An Initiator that is accessing the volume. 
     * @valid none
     * @return ITLInitiatorRestRep
     */
    @XmlElement(name = "initiator")
    public ITLInitiatorRestRep getInitiator() {
        return initiator;
    }

    public void setInitiator(ITLInitiatorRestRep initiator) {
        this.initiator = initiator;
    }

    /**
     * An ViPR ExportGroup that is providing the initiator - target - lun Mapping.
     * @valid none
     * @return NamedRelatedResourceRep
     */
    @XmlElement(name = "export")
    public NamedRelatedResourceRep getExport() {
        return export;
    }

    public void setExport(NamedRelatedResourceRep export) {
        this.export = export;
    }

    public void setSanZoneName(String sanZoneName) {
        this.sanZoneName = sanZoneName;
    }

    /**
     * A SAN zone name that maps the initiator to the target.
     * @valid String name starting with alpha and containing alpha-number and underscore characters.
     */
    @XmlElement(name = "san_zone_name")
    public String getSanZoneName() {
        return sanZoneName;
    }

    /**
     * An integer value giving the Host Logical Unit number.
     * @valid a positive number
     * @return int
     */
    @XmlElement(name = "hlu")
    public int getHlu() {
        return hlu;
    }

    public void setHlu(int hlu) {
        this.hlu = hlu;
    }
    
    /**
     * Gives information about the Storage Port that is providing access to the volume.
     */
    @XmlAccessorType(XmlAccessType.NONE)
    public static class ITLStoragePortRestRep {
        private URI id;
        private RestLinkRep link;
        private String port;
        private String ipAddress;
        private String tcpPort;

        /**
         * The ViPR URI for the Storage Port.
         * @valid URI
         */
        @XmlElement(name = "id")
        public URI getId() {
            return id;
        }

        public void setId(URI id) {
            this.id = id;
        }

        /**
         * The IP protocol address assigned to the port if any.
         * @valid IPv4 or IPv6 address.
         */
        @XmlElement(name = "ip_address")
        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        /**
         * A REST link to the StoragePort object.
         * @valid none
         */
        @XmlElement(name = "link")
        public RestLinkRep getLink() {
            return link;
        }

        public void setLink(RestLinkRep link) {
            this.link = link;
        }

        /**
         * The port's network address assigned by the Storage Device.
         * Thie could be a Fiber Channel WWN, an iSCSI IQN or EUI value,
         * or an IP address.
         * @valid WWN, IQN, EUI, or IP network address.
         */
        @XmlElement(name = "port")
        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        /**
         * The TCP port number used by the port (if any).
         * @valid Integer value between 1 and 32767.
         */
        @XmlElement(name = "tcp_port")
        public String getTcpPort() {
            return tcpPort;
        }

        public void setTcpPort(String tcpPort) {
            this.tcpPort = tcpPort;
        }
    }
    @XmlAccessorType(XmlAccessType.NONE)
    public static class ITLBlockObjectRestRep {
        private URI id;
        private RestLinkRep link;
        private String wwn;

        /**
         * The ViPR URI of the Storage Device containing the volume.
         * @valid URI
         */
        @XmlElement(name = "id")
        public URI getId() {
            return id;
        }

        public void setId(URI id) {
            this.id = id;
        }

        /**
         * A REST link to the Storage Device object.
         * @valid none
         */
        @XmlElement(name = "link")
        public RestLinkRep getLink() {
            return link;
        }

        public void setLink(RestLinkRep link) {
            this.link = link;
        }

        /**
         * The WWN of the volume.
         * @valid a World Wide Name.
         */
        @XmlElement(name = "wwn")
        public String getWwn() {
            return wwn;
        }

        public void setWwn(String wwn) {
            this.wwn = wwn;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    public static class ITLInitiatorRestRep {
        private URI id;
        private RestLinkRep link;
        private String port;

        /**
         * The ViPR URI for the Initiator accessing the volume.
         * @valid a URI
         */
        @XmlElement(name = "id")
        public URI getId() {
            return id;
        }

        public void setId(URI id) {
            this.id = id;
        }

        /**
         * A REST link to the Initiator that is accessing the volume.
         * @valid none
         */
        @XmlElement(name = "link")
        public RestLinkRep getLink() {
            return link;
        }

        public void setLink(RestLinkRep link) {
            this.link = link;
        }

        /**
         * The address of the initiator. For Fiber Channel, this is a WWN.
         * For iSCSI, this is an IQN or EUI value.
         * @valid WWN, IQN, or EUI value
         */
        @XmlElement(name = "port")
        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }
    }
}
