
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for TaskStateEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="TaskStateEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Error"/>
 *     &lt;enumeration value="Queued"/>
 *     &lt;enumeration value="Running"/>
 *     &lt;enumeration value="Success"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "TaskStateEnum", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum TaskStateEnum {

    @XmlEnumValue("Error")
    ERROR("Error"),
    @XmlEnumValue("Queued")
    QUEUED("Queued"),
    @XmlEnumValue("Running")
    RUNNING("Running"),
    @XmlEnumValue("Success")
    SUCCESS("Success");
    private final String value;

    TaskStateEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static TaskStateEnum fromValue(String v) {
        for (TaskStateEnum c: TaskStateEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
