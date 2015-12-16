
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CounterNameEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="CounterNameEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="TotalIOs"/>
 *     &lt;enumeration value="KBytesTransfered"/>
 *     &lt;enumeration value="IOTimeCounter"/>
 *     &lt;enumeration value="ReadIOs"/>
 *     &lt;enumeration value="ReadHitIOs"/>
 *     &lt;enumeration value="ReadIOTimeCounter"/>
 *     &lt;enumeration value="ReadHitIOTimeCounter"/>
 *     &lt;enumeration value="KBytesRead"/>
 *     &lt;enumeration value="WriteIOs"/>
 *     &lt;enumeration value="WriteHitIOs"/>
 *     &lt;enumeration value="WriteIOTimeCounter"/>
 *     &lt;enumeration value="WriteHitIOTimeCounter"/>
 *     &lt;enumeration value="KBytesWritten"/>
 *     &lt;enumeration value="OtherIOs"/>
 *     &lt;enumeration value="IdleTimeCounter"/>
 *     &lt;enumeration value="StatisticsTime"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "CounterNameEnum", namespace = "http://statistics.data.vasa.vim.vmware.com/xsd")
@XmlEnum
public enum CounterNameEnum {

    @XmlEnumValue("TotalIOs")
    TOTAL_I_OS("TotalIOs"),
    @XmlEnumValue("KBytesTransfered")
    K_BYTES_TRANSFERED("KBytesTransfered"),
    @XmlEnumValue("IOTimeCounter")
    IO_TIME_COUNTER("IOTimeCounter"),
    @XmlEnumValue("ReadIOs")
    READ_I_OS("ReadIOs"),
    @XmlEnumValue("ReadHitIOs")
    READ_HIT_I_OS("ReadHitIOs"),
    @XmlEnumValue("ReadIOTimeCounter")
    READ_IO_TIME_COUNTER("ReadIOTimeCounter"),
    @XmlEnumValue("ReadHitIOTimeCounter")
    READ_HIT_IO_TIME_COUNTER("ReadHitIOTimeCounter"),
    @XmlEnumValue("KBytesRead")
    K_BYTES_READ("KBytesRead"),
    @XmlEnumValue("WriteIOs")
    WRITE_I_OS("WriteIOs"),
    @XmlEnumValue("WriteHitIOs")
    WRITE_HIT_I_OS("WriteHitIOs"),
    @XmlEnumValue("WriteIOTimeCounter")
    WRITE_IO_TIME_COUNTER("WriteIOTimeCounter"),
    @XmlEnumValue("WriteHitIOTimeCounter")
    WRITE_HIT_IO_TIME_COUNTER("WriteHitIOTimeCounter"),
    @XmlEnumValue("KBytesWritten")
    K_BYTES_WRITTEN("KBytesWritten"),
    @XmlEnumValue("OtherIOs")
    OTHER_I_OS("OtherIOs"),
    @XmlEnumValue("IdleTimeCounter")
    IDLE_TIME_COUNTER("IdleTimeCounter"),
    @XmlEnumValue("StatisticsTime")
    STATISTICS_TIME("StatisticsTime");
    private final String value;

    CounterNameEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static CounterNameEnum fromValue(String v) {
        for (CounterNameEnum c: CounterNameEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
