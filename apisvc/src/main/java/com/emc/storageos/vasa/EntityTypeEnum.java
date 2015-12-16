
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EntityTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="EntityTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="StorageArray"/>
 *     &lt;enumeration value="StorageProcessor"/>
 *     &lt;enumeration value="StoragePort"/>
 *     &lt;enumeration value="StorageLun"/>
 *     &lt;enumeration value="StorageFileSystem"/>
 *     &lt;enumeration value="StorageCapability"/>
 *     &lt;enumeration value="StorageCapabilitySchema"/>
 *     &lt;enumeration value="CapabilitySchema"/>
 *     &lt;enumeration value="CapabilityProfile"/>
 *     &lt;enumeration value="DefaultProfile"/>
 *     &lt;enumeration value="ResourceAssociation"/>
 *     &lt;enumeration value="StorageContainer"/>
 *     &lt;enumeration value="StorageObject"/>
 *     &lt;enumeration value="MessageCatalog"/>
 *     &lt;enumeration value="ProtocolEndpoint"/>
 *     &lt;enumeration value="VirtualVolumeInfo"/>
 *     &lt;enumeration value="BackingStoragePool"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "EntityTypeEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum EntityTypeEnum {

    @XmlEnumValue("StorageArray")
    STORAGE_ARRAY("StorageArray"),
    @XmlEnumValue("StorageProcessor")
    STORAGE_PROCESSOR("StorageProcessor"),
    @XmlEnumValue("StoragePort")
    STORAGE_PORT("StoragePort"),
    @XmlEnumValue("StorageLun")
    STORAGE_LUN("StorageLun"),
    @XmlEnumValue("StorageFileSystem")
    STORAGE_FILE_SYSTEM("StorageFileSystem"),
    @XmlEnumValue("StorageCapability")
    STORAGE_CAPABILITY("StorageCapability"),
    @XmlEnumValue("StorageCapabilitySchema")
    STORAGE_CAPABILITY_SCHEMA("StorageCapabilitySchema"),
    @XmlEnumValue("CapabilitySchema")
    CAPABILITY_SCHEMA("CapabilitySchema"),
    @XmlEnumValue("CapabilityProfile")
    CAPABILITY_PROFILE("CapabilityProfile"),
    @XmlEnumValue("DefaultProfile")
    DEFAULT_PROFILE("DefaultProfile"),
    @XmlEnumValue("ResourceAssociation")
    RESOURCE_ASSOCIATION("ResourceAssociation"),
    @XmlEnumValue("StorageContainer")
    STORAGE_CONTAINER("StorageContainer"),
    @XmlEnumValue("StorageObject")
    STORAGE_OBJECT("StorageObject"),
    @XmlEnumValue("MessageCatalog")
    MESSAGE_CATALOG("MessageCatalog"),
    @XmlEnumValue("ProtocolEndpoint")
    PROTOCOL_ENDPOINT("ProtocolEndpoint"),
    @XmlEnumValue("VirtualVolumeInfo")
    VIRTUAL_VOLUME_INFO("VirtualVolumeInfo"),
    @XmlEnumValue("BackingStoragePool")
    BACKING_STORAGE_POOL("BackingStoragePool");
    private final String value;

    EntityTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static EntityTypeEnum fromValue(String v) {
        for (EntityTypeEnum c: EntityTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
