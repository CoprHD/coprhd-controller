
package com.watch4net.apg.remote.databaseaccessorservice;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for getAggregatedData complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="getAggregatedData">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="filter" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0" form="qualified"/>
 *         &lt;element name="sub-filter" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0" form="qualified"/>
 *         &lt;element name="start-timestamp" type="{http://www.w3.org/2001/XMLSchema}int" form="qualified"/>
 *         &lt;element name="end-timestamp" type="{http://www.w3.org/2001/XMLSchema}int" form="qualified"/>
 *         &lt;element name="time-zone" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0" form="qualified"/>
 *         &lt;element name="time-filter" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0" form="qualified"/>
 *         &lt;element name="period" type="{http://www.w3.org/2001/XMLSchema}int" form="qualified"/>
 *         &lt;element name="aggregations" type="{http://www.watch4net.com/APG/Remote/DatabaseAccessorService}Aggregations" minOccurs="0" form="qualified"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getAggregatedData", propOrder = {
    "filter",
    "subFilter",
    "startTimestamp",
    "endTimestamp",
    "timeZone",
    "timeFilter",
    "period",
    "aggregations"
})
public class GetAggregatedData {

    protected String filter;
    @XmlElement(name = "sub-filter", nillable = true)
    protected List<String> subFilter;
    @XmlElement(name = "start-timestamp")
    protected int startTimestamp;
    @XmlElement(name = "end-timestamp")
    protected int endTimestamp;
    @XmlElement(name = "time-zone")
    protected String timeZone;
    @XmlElement(name = "time-filter")
    protected String timeFilter;
    protected int period;
    protected Aggregations aggregations;

    /**
     * Gets the value of the filter property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFilter() {
        return filter;
    }

    /**
     * Sets the value of the filter property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFilter(String value) {
        this.filter = value;
    }

    /**
     * Gets the value of the subFilter property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the subFilter property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSubFilter().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getSubFilter() {
        if (subFilter == null) {
            subFilter = new ArrayList<String>();
        }
        return this.subFilter;
    }

    /**
     * Gets the value of the startTimestamp property.
     * 
     */
    public int getStartTimestamp() {
        return startTimestamp;
    }

    /**
     * Sets the value of the startTimestamp property.
     * 
     */
    public void setStartTimestamp(int value) {
        this.startTimestamp = value;
    }

    /**
     * Gets the value of the endTimestamp property.
     * 
     */
    public int getEndTimestamp() {
        return endTimestamp;
    }

    /**
     * Sets the value of the endTimestamp property.
     * 
     */
    public void setEndTimestamp(int value) {
        this.endTimestamp = value;
    }

    /**
     * Gets the value of the timeZone property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTimeZone() {
        return timeZone;
    }

    /**
     * Sets the value of the timeZone property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTimeZone(String value) {
        this.timeZone = value;
    }

    /**
     * Gets the value of the timeFilter property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTimeFilter() {
        return timeFilter;
    }

    /**
     * Sets the value of the timeFilter property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTimeFilter(String value) {
        this.timeFilter = value;
    }

    /**
     * Gets the value of the period property.
     * 
     */
    public int getPeriod() {
        return period;
    }

    /**
     * Sets the value of the period property.
     * 
     */
    public void setPeriod(int value) {
        this.period = value;
    }

    /**
     * Gets the value of the aggregations property.
     * 
     * @return
     *     possible object is
     *     {@link Aggregations }
     *     
     */
    public Aggregations getAggregations() {
        return aggregations;
    }

    /**
     * Sets the value of the aggregations property.
     * 
     * @param value
     *     allowed object is
     *     {@link Aggregations }
     *     
     */
    public void setAggregations(Aggregations value) {
        this.aggregations = value;
    }

}
