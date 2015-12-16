
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for RemoteReplicationEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="RemoteReplicationEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="ActiveActive"/>
 *     &lt;enumeration value="ActivePassive"/>
 *     &lt;enumeration value="None"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "RemoteReplicationEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum RemoteReplicationEnum {

    @XmlEnumValue("ActiveActive")
    ACTIVE_ACTIVE("ActiveActive"),
    @XmlEnumValue("ActivePassive")
    ACTIVE_PASSIVE("ActivePassive"),
    @XmlEnumValue("None")
    NONE("None");
    private final String value;

    RemoteReplicationEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static RemoteReplicationEnum fromValue(String v) {
        for (RemoteReplicationEnum c: RemoteReplicationEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
