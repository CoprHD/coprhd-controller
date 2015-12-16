
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InBandBindCapabilityEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="InBandBindCapabilityEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="None"/>
 *     &lt;enumeration value="LocalBind"/>
 *     &lt;enumeration value="GlobalBind"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "InBandBindCapabilityEnum", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum InBandBindCapabilityEnum {

    @XmlEnumValue("None")
    NONE("None"),
    @XmlEnumValue("LocalBind")
    LOCAL_BIND("LocalBind"),
    @XmlEnumValue("GlobalBind")
    GLOBAL_BIND("GlobalBind");
    private final String value;

    InBandBindCapabilityEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static InBandBindCapabilityEnum fromValue(String v) {
        for (InBandBindCapabilityEnum c: InBandBindCapabilityEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
