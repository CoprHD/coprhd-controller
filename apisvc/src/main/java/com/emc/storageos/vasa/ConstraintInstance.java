
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ConstraintInstance complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ConstraintInstance">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="propertyInstance" type="{http://capability.policy.data.vasa.vim.vmware.com/xsd}PropertyInstance" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ConstraintInstance", namespace = "http://capability.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "propertyInstance"
})
public class ConstraintInstance {

    @XmlElement(required = true)
    protected List<PropertyInstance> propertyInstance;

    /**
     * Gets the value of the propertyInstance property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the propertyInstance property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPropertyInstance().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PropertyInstance }
     * 
     * 
     */
    public List<PropertyInstance> getPropertyInstance() {
        if (propertyInstance == null) {
            propertyInstance = new ArrayList<PropertyInstance>();
        }
        return this.propertyInstance;
    }

}
