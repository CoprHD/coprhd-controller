
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
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
 *         &lt;element name="vvolHandle" type="{http://vvol.data.vasa.vim.vmware.com/xsd}VirtualVolumeHandle" maxOccurs="unbounded"/>
 *         &lt;element name="unbindContext" type="{http://www.w3.org/2001/XMLSchema}string"/>
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
    "vvolHandle",
    "unbindContext"
})
@XmlRootElement(name = "unbindVirtualVolume")
public class UnbindVirtualVolume {

    @XmlElement(required = true)
    protected List<VirtualVolumeHandle> vvolHandle;
    @XmlElement(required = true)
    protected String unbindContext;

    /**
     * Gets the value of the vvolHandle property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vvolHandle property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVvolHandle().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VirtualVolumeHandle }
     * 
     * 
     */
    public List<VirtualVolumeHandle> getVvolHandle() {
        if (vvolHandle == null) {
            vvolHandle = new ArrayList<VirtualVolumeHandle>();
        }
        return this.vvolHandle;
    }

    /**
     * Gets the value of the unbindContext property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUnbindContext() {
        return unbindContext;
    }

    /**
     * Sets the value of the unbindContext property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUnbindContext(String value) {
        this.unbindContext = value;
    }

}
