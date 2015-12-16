
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProtocolEndpointTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ProtocolEndpointTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="NFS"/>
 *     &lt;enumeration value="SCSI"/>
 *     &lt;enumeration value="NFS4x"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "ProtocolEndpointTypeEnum", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum ProtocolEndpointTypeEnum {

    NFS("NFS"),
    SCSI("SCSI"),
    @XmlEnumValue("NFS4x")
    NFS_4_X("NFS4x");
    private final String value;

    ProtocolEndpointTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ProtocolEndpointTypeEnum fromValue(String v) {
        for (ProtocolEndpointTypeEnum c: ProtocolEndpointTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
