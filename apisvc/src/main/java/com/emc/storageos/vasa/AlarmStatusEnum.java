
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AlarmStatusEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="AlarmStatusEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Red"/>
 *     &lt;enumeration value="Green"/>
 *     &lt;enumeration value="Yellow"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "AlarmStatusEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum AlarmStatusEnum {

    @XmlEnumValue("Red")
    RED("Red"),
    @XmlEnumValue("Green")
    GREEN("Green"),
    @XmlEnumValue("Yellow")
    YELLOW("Yellow");
    private final String value;

    AlarmStatusEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static AlarmStatusEnum fromValue(String v) {
        for (AlarmStatusEnum c: AlarmStatusEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
