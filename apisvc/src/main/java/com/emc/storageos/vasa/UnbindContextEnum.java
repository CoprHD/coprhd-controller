
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for UnbindContextEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="UnbindContextEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Normal"/>
 *     &lt;enumeration value="RebindStart"/>
 *     &lt;enumeration value="RebindEnd"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "UnbindContextEnum", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum UnbindContextEnum {

    @XmlEnumValue("Normal")
    NORMAL("Normal"),
    @XmlEnumValue("RebindStart")
    REBIND_START("RebindStart"),
    @XmlEnumValue("RebindEnd")
    REBIND_END("RebindEnd");
    private final String value;

    UnbindContextEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static UnbindContextEnum fromValue(String v) {
        for (UnbindContextEnum c: UnbindContextEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
