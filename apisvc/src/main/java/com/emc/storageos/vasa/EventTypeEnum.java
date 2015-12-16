
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EventTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="EventTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="System"/>
 *     &lt;enumeration value="Config"/>
 *     &lt;enumeration value="Policy"/>
 *     &lt;enumeration value="ConfigProtocolEndpoint"/>
 *     &lt;enumeration value="Rebind"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "EventTypeEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum EventTypeEnum {

    @XmlEnumValue("System")
    SYSTEM("System"),
    @XmlEnumValue("Config")
    CONFIG("Config"),
    @XmlEnumValue("Policy")
    POLICY("Policy"),
    @XmlEnumValue("ConfigProtocolEndpoint")
    CONFIG_PROTOCOL_ENDPOINT("ConfigProtocolEndpoint"),
    @XmlEnumValue("Rebind")
    REBIND("Rebind");
    private final String value;

    EventTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static EventTypeEnum fromValue(String v) {
        for (EventTypeEnum c: EventTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
