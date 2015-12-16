
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProtocolEndpointAuthEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ProtocolEndpointAuthEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="System"/>
 *     &lt;enumeration value="Kerberos5"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "ProtocolEndpointAuthEnum", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum ProtocolEndpointAuthEnum {

    @XmlEnumValue("System")
    SYSTEM("System"),
    @XmlEnumValue("Kerberos5")
    KERBEROS_5("Kerberos5");
    private final String value;

    ProtocolEndpointAuthEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ProtocolEndpointAuthEnum fromValue(String v) {
        for (ProtocolEndpointAuthEnum c: ProtocolEndpointAuthEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
