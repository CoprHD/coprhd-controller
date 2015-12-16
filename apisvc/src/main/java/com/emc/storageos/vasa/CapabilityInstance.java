
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CapabilityInstance complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CapabilityInstance">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="capabilityId" type="{http://capability.policy.data.vasa.vim.vmware.com/xsd}CapabilityId"/>
 *         &lt;element name="constraint" type="{http://capability.policy.data.vasa.vim.vmware.com/xsd}ConstraintInstance" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CapabilityInstance", namespace = "http://capability.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "capabilityId",
    "constraint"
})
public class CapabilityInstance {

    @XmlElement(required = true)
    protected CapabilityId capabilityId;
    @XmlElement(required = true)
    protected List<ConstraintInstance> constraint;

    /**
     * Gets the value of the capabilityId property.
     * 
     * @return
     *     possible object is
     *     {@link CapabilityId }
     *     
     */
    public CapabilityId getCapabilityId() {
        return capabilityId;
    }

    /**
     * Sets the value of the capabilityId property.
     * 
     * @param value
     *     allowed object is
     *     {@link CapabilityId }
     *     
     */
    public void setCapabilityId(CapabilityId value) {
        this.capabilityId = value;
    }

    /**
     * Gets the value of the constraint property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the constraint property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getConstraint().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ConstraintInstance }
     * 
     * 
     */
    public List<ConstraintInstance> getConstraint() {
        if (constraint == null) {
            constraint = new ArrayList<ConstraintInstance>();
        }
        return this.constraint;
    }

}
