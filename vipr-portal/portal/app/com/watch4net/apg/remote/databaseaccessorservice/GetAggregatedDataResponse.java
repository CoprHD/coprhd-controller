
package com.watch4net.apg.remote.databaseaccessorservice;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for getAggregatedDataResponse complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="getAggregatedDataResponse">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="timeserie" type="{http://www.watch4net.com/APG/Remote/DatabaseAccessorService}TimeSerie" maxOccurs="unbounded" minOccurs="0" form="qualified"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getAggregatedDataResponse", propOrder = {
    "timeserie"
})
public class GetAggregatedDataResponse {

    @XmlElement(nillable = true)
    protected List<TimeSerie> timeserie;

    /**
     * Gets the value of the timeserie property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the timeserie property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getTimeserie().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link TimeSerie }
     * 
     * 
     */
    public List<TimeSerie> getTimeserie() {
        if (timeserie == null) {
            timeserie = new ArrayList<TimeSerie>();
        }
        return this.timeserie;
    }

}
