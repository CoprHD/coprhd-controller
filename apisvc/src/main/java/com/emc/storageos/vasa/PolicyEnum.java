
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PolicyEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PolicyEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Schema"/>
 *     &lt;enumeration value="Profile"/>
 *     &lt;enumeration value="Resource"/>
 *     &lt;enumeration value="Placement"/>
 *     &lt;enumeration value="Compliance"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "PolicyEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum PolicyEnum {

    @XmlEnumValue("Schema")
    SCHEMA("Schema"),
    @XmlEnumValue("Profile")
    PROFILE("Profile"),
    @XmlEnumValue("Resource")
    RESOURCE("Resource"),
    @XmlEnumValue("Placement")
    PLACEMENT("Placement"),
    @XmlEnumValue("Compliance")
    COMPLIANCE("Compliance");
    private final String value;

    PolicyEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PolicyEnum fromValue(String v) {
        for (PolicyEnum c: PolicyEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
