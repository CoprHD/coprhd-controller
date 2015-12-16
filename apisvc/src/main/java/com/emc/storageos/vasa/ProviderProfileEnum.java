
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProviderProfileEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ProviderProfileEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="ProfileBasedManagementProfile"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "ProviderProfileEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum ProviderProfileEnum {

    @XmlEnumValue("ProfileBasedManagementProfile")
    PROFILE_BASED_MANAGEMENT_PROFILE("ProfileBasedManagementProfile");
    private final String value;

    ProviderProfileEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ProviderProfileEnum fromValue(String v) {
        for (ProviderProfileEnum c: ProviderProfileEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
