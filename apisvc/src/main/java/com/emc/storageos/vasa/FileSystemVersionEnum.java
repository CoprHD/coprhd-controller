
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FileSystemVersionEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="FileSystemVersionEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="NFSV3_0"/>
 *     &lt;enumeration value="NFSV4_x"/>
 *     &lt;enumeration value="Other"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "FileSystemVersionEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum FileSystemVersionEnum {

    @XmlEnumValue("NFSV3_0")
    NFSV_3_0("NFSV3_0"),
    @XmlEnumValue("NFSV4_x")
    NFSV_4_X("NFSV4_x"),
    @XmlEnumValue("Other")
    OTHER("Other");
    private final String value;

    FileSystemVersionEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static FileSystemVersionEnum fromValue(String v) {
        for (FileSystemVersionEnum c: FileSystemVersionEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
