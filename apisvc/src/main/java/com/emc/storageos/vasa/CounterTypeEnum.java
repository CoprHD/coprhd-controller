
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CounterTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="CounterTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Uint64"/>
 *     &lt;enumeration value="String"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "CounterTypeEnum", namespace = "http://statistics.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum CounterTypeEnum {

    @XmlEnumValue("Uint64")
    UINT_64("Uint64"),
    @XmlEnumValue("String")
    STRING("String");
    private final String value;

    CounterTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static CounterTypeEnum fromValue(String v) {
        for (CounterTypeEnum c: CounterTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
