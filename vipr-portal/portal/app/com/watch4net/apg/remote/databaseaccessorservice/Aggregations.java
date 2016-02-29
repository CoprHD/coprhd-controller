
package com.watch4net.apg.remote.databaseaccessorservice;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Aggregations complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Aggregations">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *       &lt;/sequence>
 *       &lt;attribute name="count" type="{http://www.watch4net.com/APG/Remote/DatabaseAccessorService}Aggregation" />
 *       &lt;attribute name="spacial" type="{http://www.watch4net.com/APG/Remote/DatabaseAccessorService}Aggregation" />
 *       &lt;attribute name="temporal" use="required" type="{http://www.watch4net.com/APG/Remote/DatabaseAccessorService}Aggregation" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Aggregations")
public class Aggregations {

    @XmlAttribute(name = "count")
    protected Aggregation count;
    @XmlAttribute(name = "spacial")
    protected Aggregation spacial;
    @XmlAttribute(name = "temporal", required = true)
    protected Aggregation temporal;

    /**
     * Gets the value of the count property.
     * 
     * @return
     *     possible object is
     *     {@link Aggregation }
     *     
     */
    public Aggregation getCount() {
        return count;
    }

    /**
     * Sets the value of the count property.
     * 
     * @param value
     *     allowed object is
     *     {@link Aggregation }
     *     
     */
    public void setCount(Aggregation value) {
        this.count = value;
    }

    /**
     * Gets the value of the spacial property.
     * 
     * @return
     *     possible object is
     *     {@link Aggregation }
     *     
     */
    public Aggregation getSpacial() {
        return spacial;
    }

    /**
     * Sets the value of the spacial property.
     * 
     * @param value
     *     allowed object is
     *     {@link Aggregation }
     *     
     */
    public void setSpacial(Aggregation value) {
        this.spacial = value;
    }

    /**
     * Gets the value of the temporal property.
     * 
     * @return
     *     possible object is
     *     {@link Aggregation }
     *     
     */
    public Aggregation getTemporal() {
        return temporal;
    }

    /**
     * Sets the value of the temporal property.
     * 
     * @param value
     *     allowed object is
     *     {@link Aggregation }
     *     
     */
    public void setTemporal(Aggregation value) {
        this.temporal = value;
    }

}
