
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CounterUnitEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="CounterUnitEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Number"/>
 *     &lt;enumeration value="KiloByte"/>
 *     &lt;enumeration value="MilliSecond"/>
 *     &lt;enumeration value="Timestamp"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "CounterUnitEnum", namespace = "http://statistics.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum CounterUnitEnum {

    @XmlEnumValue("Number")
    NUMBER("Number"),
    @XmlEnumValue("KiloByte")
    KILO_BYTE("KiloByte"),
    @XmlEnumValue("MilliSecond")
    MILLI_SECOND("MilliSecond"),
    @XmlEnumValue("Timestamp")
    TIMESTAMP("Timestamp");
    private final String value;

    CounterUnitEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static CounterUnitEnum fromValue(String v) {
        for (CounterUnitEnum c: CounterUnitEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
