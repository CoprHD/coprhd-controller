
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BuiltinTypesEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="BuiltinTypesEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="XSD_LONG"/>
 *     &lt;enumeration value="XSD_INT"/>
 *     &lt;enumeration value="XSD_STRING"/>
 *     &lt;enumeration value="XSD_BOOLEAN"/>
 *     &lt;enumeration value="XSD_DOUBLE"/>
 *     &lt;enumeration value="XSD_DATETIME"/>
 *     &lt;enumeration value="XSD_DURATION"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "BuiltinTypesEnum", namespace = "http://types.capability.policy.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum BuiltinTypesEnum {

    XSD_LONG,
    XSD_INT,
    XSD_STRING,
    XSD_BOOLEAN,
    XSD_DOUBLE,
    XSD_DATETIME,
    XSD_DURATION;

    public String value() {
        return name();
    }

    public static BuiltinTypesEnum fromValue(String v) {
        return valueOf(v);
    }

}
