
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VasaAssociationObject complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VasaAssociationObject">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="associatedId" type="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="entityId" type="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VasaAssociationObject", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "associatedId",
    "entityId"
})
public class VasaAssociationObject {

    protected List<BaseStorageEntity> associatedId;
    protected List<BaseStorageEntity> entityId;

    /**
     * Gets the value of the associatedId property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the associatedId property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAssociatedId().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link BaseStorageEntity }
     * 
     * 
     */
    public List<BaseStorageEntity> getAssociatedId() {
        if (associatedId == null) {
            associatedId = new ArrayList<BaseStorageEntity>();
        }
        return this.associatedId;
    }

    /**
     * Gets the value of the entityId property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the entityId property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEntityId().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link BaseStorageEntity }
     * 
     * 
     */
    public List<BaseStorageEntity> getEntityId() {
        if (entityId == null) {
            entityId = new ArrayList<BaseStorageEntity>();
        }
        return this.entityId;
    }

}
