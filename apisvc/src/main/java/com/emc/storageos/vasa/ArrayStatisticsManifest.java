
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayStatisticsManifest complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayStatisticsManifest">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="arrayId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="entityStatisticsManifest" type="{http://statistics.data.vasa.vim.vmware.com/xsd}EntityStatisticsManifest" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayStatisticsManifest", namespace = "http://statistics.data.vasa.vim.vmware.com/xsd", propOrder = {
    "arrayId",
    "entityStatisticsManifest"
})
public class ArrayStatisticsManifest {

    @XmlElement(required = true)
    protected String arrayId;
    @XmlElement(required = true)
    protected List<EntityStatisticsManifest> entityStatisticsManifest;

    /**
     * Gets the value of the arrayId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getArrayId() {
        return arrayId;
    }

    /**
     * Sets the value of the arrayId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setArrayId(String value) {
        this.arrayId = value;
    }

    /**
     * Gets the value of the entityStatisticsManifest property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the entityStatisticsManifest property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEntityStatisticsManifest().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link EntityStatisticsManifest }
     * 
     * 
     */
    public List<EntityStatisticsManifest> getEntityStatisticsManifest() {
        if (entityStatisticsManifest == null) {
            entityStatisticsManifest = new ArrayList<EntityStatisticsManifest>();
        }
        return this.entityStatisticsManifest;
    }

}
