
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DefaultProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DefaultProfile">
 *   &lt;complexContent>
 *     &lt;extension base="{http://profile.policy.data.vasa.vim.vmware.com/xsd}StorageProfile">
 *       &lt;sequence>
 *         &lt;element name="entityType" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DefaultProfile", namespace = "http://profile.policy.data.vasa.vim.vmware.com/xsd", propOrder = {
    "entityType"
})
public class DefaultProfile
    extends StorageProfile
{

    protected List<String> entityType;

    /**
     * Gets the value of the entityType property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the entityType property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEntityType().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getEntityType() {
        if (entityType == null) {
            entityType = new ArrayList<String>();
        }
        return this.entityType;
    }

}
