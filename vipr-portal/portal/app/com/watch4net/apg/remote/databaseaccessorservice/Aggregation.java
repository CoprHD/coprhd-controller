
package com.watch4net.apg.remote.databaseaccessorservice;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Aggregation.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="Aggregation">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="AVG"/>
 *     &lt;enumeration value="MIN"/>
 *     &lt;enumeration value="MAX"/>
 *     &lt;enumeration value="SUM"/>
 *     &lt;enumeration value="LAST"/>
 *     &lt;enumeration value="NBVAL"/>
 *     &lt;enumeration value="LASTTS"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "Aggregation")
@XmlEnum
public enum Aggregation {

    AVG,
    MIN,
    MAX,
    SUM,
    LAST,
    NBVAL,
    LASTTS;

    public String value() {
        return name();
    }

    public static Aggregation fromValue(String v) {
        return valueOf(v);
    }

}
