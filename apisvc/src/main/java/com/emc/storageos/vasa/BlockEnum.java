
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BlockEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="BlockEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="FC"/>
 *     &lt;enumeration value="ISCSI"/>
 *     &lt;enumeration value="FCoE"/>
 *     &lt;enumeration value="Other"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "BlockEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum BlockEnum {

    FC("FC"),
    ISCSI("ISCSI"),
    @XmlEnumValue("FCoE")
    F_CO_E("FCoE"),
    @XmlEnumValue("Other")
    OTHER("Other");
    private final String value;

    BlockEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static BlockEnum fromValue(String v) {
        for (BlockEnum c: BlockEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
