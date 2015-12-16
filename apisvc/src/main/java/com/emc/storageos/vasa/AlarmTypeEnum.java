
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AlarmTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="AlarmTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="SpaceCapacity"/>
 *     &lt;enumeration value="Capability"/>
 *     &lt;enumeration value="StorageObject"/>
 *     &lt;enumeration value="Object"/>
 *     &lt;enumeration value="Compliance"/>
 *     &lt;enumeration value="Manageability"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "AlarmTypeEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum AlarmTypeEnum {

    @XmlEnumValue("SpaceCapacity")
    SPACE_CAPACITY("SpaceCapacity"),
    @XmlEnumValue("Capability")
    CAPABILITY("Capability"),
    @XmlEnumValue("StorageObject")
    STORAGE_OBJECT("StorageObject"),
    @XmlEnumValue("Object")
    OBJECT("Object"),
    @XmlEnumValue("Compliance")
    COMPLIANCE("Compliance"),
    @XmlEnumValue("Manageability")
    MANAGEABILITY("Manageability");
    private final String value;

    AlarmTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static AlarmTypeEnum fromValue(String v) {
        for (AlarmTypeEnum c: AlarmTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
