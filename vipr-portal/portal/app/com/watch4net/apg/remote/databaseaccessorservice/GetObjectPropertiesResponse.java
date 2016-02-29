
package com.watch4net.apg.remote.databaseaccessorservice;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for getObjectPropertiesResponse complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="getObjectPropertiesResponse">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="object-properties" type="{http://www.watch4net.com/APG/Remote/DatabaseAccessorService}ObjectPropertyValues" maxOccurs="unbounded" minOccurs="0" form="qualified"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getObjectPropertiesResponse", propOrder = {
    "objectProperties"
})
public class GetObjectPropertiesResponse {

    @XmlElement(name = "object-properties", nillable = true)
    protected List<ObjectPropertyValues> objectProperties;

    /**
     * Gets the value of the objectProperties property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the objectProperties property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getObjectProperties().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ObjectPropertyValues }
     * 
     * 
     */
    public List<ObjectPropertyValues> getObjectProperties() {
        if (objectProperties == null) {
            objectProperties = new ArrayList<ObjectPropertyValues>();
        }
        return this.objectProperties;
    }

}
