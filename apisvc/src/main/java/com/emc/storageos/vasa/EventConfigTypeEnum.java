
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EventConfigTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="EventConfigTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="New"/>
 *     &lt;enumeration value="Update"/>
 *     &lt;enumeration value="Delete"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "EventConfigTypeEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum EventConfigTypeEnum {

    @XmlEnumValue("New")
    NEW("New"),
    @XmlEnumValue("Update")
    UPDATE("Update"),
    @XmlEnumValue("Delete")
    DELETE("Delete");
    private final String value;

    EventConfigTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static EventConfigTypeEnum fromValue(String v) {
        for (EventConfigTypeEnum c: EventConfigTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
