
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ComplianceStatusEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ComplianceStatusEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="compliant"/>
 *     &lt;enumeration value="nonCompliant"/>
 *     &lt;enumeration value="unknown"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "ComplianceStatusEnum", namespace = "http://compliance.policy.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum ComplianceStatusEnum {

    @XmlEnumValue("compliant")
    COMPLIANT("compliant"),
    @XmlEnumValue("nonCompliant")
    NON_COMPLIANT("nonCompliant"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    ComplianceStatusEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ComplianceStatusEnum fromValue(String v) {
        for (ComplianceStatusEnum c: ComplianceStatusEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
