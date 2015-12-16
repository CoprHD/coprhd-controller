
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StorageCatalogEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="StorageCatalogEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Alarm"/>
 *     &lt;enumeration value="Event"/>
 *     &lt;enumeration value="Fault"/>
 *     &lt;enumeration value="Policy"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "StorageCatalogEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum StorageCatalogEnum {

    @XmlEnumValue("Alarm")
    ALARM("Alarm"),
    @XmlEnumValue("Event")
    EVENT("Event"),
    @XmlEnumValue("Fault")
    FAULT("Fault"),
    @XmlEnumValue("Policy")
    POLICY("Policy");
    private final String value;

    StorageCatalogEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static StorageCatalogEnum fromValue(String v) {
        for (StorageCatalogEnum c: StorageCatalogEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
