
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GenericTypeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="GenericTypeInfo">
 *   &lt;complexContent>
 *     &lt;extension base="{http://capability.policy.data.vasa.vim.vmware.com/xsd}TypeInfo">
 *       &lt;sequence>
 *         &lt;element name="genericTypeName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GenericTypeInfo", namespace = "http://capability.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "genericTypeName"
})
public class GenericTypeInfo
    extends TypeInfo
{

    @XmlElement(required = true)
    protected String genericTypeName;

    /**
     * Gets the value of the genericTypeName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getGenericTypeName() {
        return genericTypeName;
    }

    /**
     * Sets the value of the genericTypeName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setGenericTypeName(String value) {
        this.genericTypeName = value;
    }

}
