
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProfileEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ProfileEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="BlockDeviceProfile"/>
 *     &lt;enumeration value="FileSystemProfile"/>
 *     &lt;enumeration value="CapabilityProfile"/>
 *     &lt;enumeration value="StorageObjectProfile"/>
 *     &lt;enumeration value="VirtualVolumeProfile"/>
 *     &lt;enumeration value="VirtualMetroStorageClusterProfile"/>
 *     &lt;enumeration value="StatisticsProfile"/>
 *     &lt;enumeration value="StorageDrsBlockDevice"/>
 *     &lt;enumeration value="StorageDrsFileSystem"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "ProfileEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum ProfileEnum {

    @XmlEnumValue("BlockDeviceProfile")
    BLOCK_DEVICE_PROFILE("BlockDeviceProfile"),
    @XmlEnumValue("FileSystemProfile")
    FILE_SYSTEM_PROFILE("FileSystemProfile"),
    @XmlEnumValue("CapabilityProfile")
    CAPABILITY_PROFILE("CapabilityProfile"),
    @XmlEnumValue("StorageObjectProfile")
    STORAGE_OBJECT_PROFILE("StorageObjectProfile"),
    @XmlEnumValue("VirtualVolumeProfile")
    VIRTUAL_VOLUME_PROFILE("VirtualVolumeProfile"),
    @XmlEnumValue("VirtualMetroStorageClusterProfile")
    VIRTUAL_METRO_STORAGE_CLUSTER_PROFILE("VirtualMetroStorageClusterProfile"),
    @XmlEnumValue("StatisticsProfile")
    STATISTICS_PROFILE("StatisticsProfile"),
    @XmlEnumValue("StorageDrsBlockDevice")
    STORAGE_DRS_BLOCK_DEVICE("StorageDrsBlockDevice"),
    @XmlEnumValue("StorageDrsFileSystem")
    STORAGE_DRS_FILE_SYSTEM("StorageDrsFileSystem");
    private final String value;

    ProfileEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ProfileEnum fromValue(String v) {
        for (ProfileEnum c: ProfileEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
