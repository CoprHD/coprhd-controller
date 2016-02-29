
package com.watch4net.apg.remote.databaseaccessorservice;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for TimeSerie complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="TimeSerie">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="tv" type="{http://www.watch4net.com/APG/Remote/DatabaseAccessorService}TimeSerieValue" maxOccurs="unbounded" minOccurs="0" form="qualified"/>
 *       &lt;/sequence>
 *       &lt;attribute name="fields" use="required" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="length" use="required" type="{http://www.w3.org/2001/XMLSchema}int" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TimeSerie", propOrder = {
    "tv"
})
public class TimeSerie {

    protected List<TimeSerieValue> tv;
    @XmlAttribute(name = "fields", required = true)
    protected int fields;
    @XmlAttribute(name = "id")
    protected String id;
    @XmlAttribute(name = "length", required = true)
    protected int length;

    /**
     * Gets the value of the tv property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the tv property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getTv().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link TimeSerieValue }
     * 
     * 
     */
    public List<TimeSerieValue> getTv() {
        if (tv == null) {
            tv = new ArrayList<TimeSerieValue>();
        }
        return this.tv;
    }

    /**
     * Gets the value of the fields property.
     * 
     */
    public int getFields() {
        return fields;
    }

    /**
     * Sets the value of the fields property.
     * 
     */
    public void setFields(int value) {
        this.fields = value;
    }

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Gets the value of the length property.
     * 
     */
    public int getLength() {
        return length;
    }

    /**
     * Sets the value of the length property.
     * 
     */
    public void setLength(int value) {
        this.length = value;
    }

}
