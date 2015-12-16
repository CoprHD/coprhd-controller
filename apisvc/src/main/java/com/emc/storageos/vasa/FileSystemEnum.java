
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FileSystemEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="FileSystemEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="NFS"/>
 *     &lt;enumeration value="Other"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "FileSystemEnum", namespace = "http://data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum FileSystemEnum {

    NFS("NFS"),
    @XmlEnumValue("Other")
    OTHER("Other");
    private final String value;

    FileSystemEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static FileSystemEnum fromValue(String v) {
        for (FileSystemEnum c: FileSystemEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
