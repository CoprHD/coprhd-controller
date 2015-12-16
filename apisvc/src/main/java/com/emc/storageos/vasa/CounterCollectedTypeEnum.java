
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CounterCollectedTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="CounterCollectedTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Cumulative"/>
 *     &lt;enumeration value="Absolute"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "CounterCollectedTypeEnum", namespace = "http://statistics.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum CounterCollectedTypeEnum {

    @XmlEnumValue("Cumulative")
    CUMULATIVE("Cumulative"),
    @XmlEnumValue("Absolute")
    ABSOLUTE("Absolute");
    private final String value;

    CounterCollectedTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static CounterCollectedTypeEnum fromValue(String v) {
        for (CounterCollectedTypeEnum c: CounterCollectedTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
