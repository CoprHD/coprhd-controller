
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for BuiltinGenericTypesEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="BuiltinGenericTypesEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="VMW_RANGE"/>
 *     &lt;enumeration value="VMW_SET"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "BuiltinGenericTypesEnum", namespace = "http://types.capability.policy.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum BuiltinGenericTypesEnum {

    VMW_RANGE,
    VMW_SET;

    public String value() {
        return name();
    }

    public static BuiltinGenericTypesEnum fromValue(String v) {
        return valueOf(v);
    }

}
