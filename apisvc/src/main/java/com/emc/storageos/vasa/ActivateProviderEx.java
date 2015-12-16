
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="activationSpec" type="{http://data.vasa.vim.vmware.com/xsd}ActivationSpec"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "activationSpec"
})
@XmlRootElement(name = "activateProviderEx")
public class ActivateProviderEx {

    @XmlElement(required = true)
    protected ActivationSpec activationSpec;

    /**
     * Gets the value of the activationSpec property.
     * 
     * @return
     *     possible object is
     *     {@link ActivationSpec }
     *     
     */
    public ActivationSpec getActivationSpec() {
        return activationSpec;
    }

    /**
     * Sets the value of the activationSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link ActivationSpec }
     *     
     */
    public void setActivationSpec(ActivationSpec value) {
        this.activationSpec = value;
    }

}
