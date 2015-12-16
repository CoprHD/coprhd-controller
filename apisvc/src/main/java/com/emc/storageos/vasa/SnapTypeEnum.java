
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SnapTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="SnapTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="SnapshotReadOnly"/>
 *     &lt;enumeration value="SnapshotReadWrite"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "SnapTypeEnum", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum SnapTypeEnum {

    @XmlEnumValue("SnapshotReadOnly")
    SNAPSHOT_READ_ONLY("SnapshotReadOnly"),
    @XmlEnumValue("SnapshotReadWrite")
    SNAPSHOT_READ_WRITE("SnapshotReadWrite");
    private final String value;

    SnapTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static SnapTypeEnum fromValue(String v) {
        for (SnapTypeEnum c: SnapTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
