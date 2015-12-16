
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BackingStoragePoolTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="BackingStoragePoolTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="ThinProvisioningPool"/>
 *     &lt;enumeration value="DeduplicationPool"/>
 *     &lt;enumeration value="ThinAndDeduplicationCombinedPool"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "BackingStoragePoolTypeEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum BackingStoragePoolTypeEnum {

    @XmlEnumValue("ThinProvisioningPool")
    THIN_PROVISIONING_POOL("ThinProvisioningPool"),
    @XmlEnumValue("DeduplicationPool")
    DEDUPLICATION_POOL("DeduplicationPool"),
    @XmlEnumValue("ThinAndDeduplicationCombinedPool")
    THIN_AND_DEDUPLICATION_COMBINED_POOL("ThinAndDeduplicationCombinedPool");
    private final String value;

    BackingStoragePoolTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static BackingStoragePoolTypeEnum fromValue(String v) {
        for (BackingStoragePoolTypeEnum c: BackingStoragePoolTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
