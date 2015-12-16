
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualVolumeTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualVolumeTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Config"/>
 *     &lt;enumeration value="Data"/>
 *     &lt;enumeration value="Swap"/>
 *     &lt;enumeration value="Memory"/>
 *     &lt;enumeration value="Other"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "VirtualVolumeTypeEnum", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum VirtualVolumeTypeEnum {

    @XmlEnumValue("Config")
    CONFIG("Config"),
    @XmlEnumValue("Data")
    DATA("Data"),
    @XmlEnumValue("Swap")
    SWAP("Swap"),
    @XmlEnumValue("Memory")
    MEMORY("Memory"),
    @XmlEnumValue("Other")
    OTHER("Other");
    private final String value;

    VirtualVolumeTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualVolumeTypeEnum fromValue(String v) {
        for (VirtualVolumeTypeEnum c: VirtualVolumeTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
