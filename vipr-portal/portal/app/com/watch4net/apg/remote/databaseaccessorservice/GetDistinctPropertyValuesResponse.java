
package com.watch4net.apg.remote.databaseaccessorservice;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for getDistinctPropertyValuesResponse complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="getDistinctPropertyValuesResponse">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="distinct-properties" type="{http://www.watch4net.com/APG/Remote/DatabaseAccessorService}DistinctPropertyValues" maxOccurs="unbounded" minOccurs="0" form="qualified"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getDistinctPropertyValuesResponse", propOrder = {
    "distinctProperties"
})
public class GetDistinctPropertyValuesResponse {

    @XmlElement(name = "distinct-properties", nillable = true)
    protected List<DistinctPropertyValues> distinctProperties;

    /**
     * Gets the value of the distinctProperties property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the distinctProperties property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDistinctProperties().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DistinctPropertyValues }
     * 
     * 
     */
    public List<DistinctPropertyValues> getDistinctProperties() {
        if (distinctProperties == null) {
            distinctProperties = new ArrayList<DistinctPropertyValues>();
        }
        return this.distinctProperties;
    }

}
