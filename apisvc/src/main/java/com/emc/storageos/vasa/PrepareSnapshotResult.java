
package com.emc.storageos.vasa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PrepareSnapshotResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PrepareSnapshotResult">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="parentInfo" type="{http://vvol.data.vasa.vim.vmware.com/xsd}VirtualVolumeInfo"/>
 *         &lt;element name="parentStats" type="{http://vvol.data.vasa.vim.vmware.com/xsd}SpaceStats"/>
 *         &lt;element name="snapshotId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PrepareSnapshotResult", namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", propOrder = {
    "parentInfo",
    "parentStats",
    "snapshotId"
})
public class PrepareSnapshotResult {

    @XmlElement(required = true)
    protected VirtualVolumeInfo parentInfo;
    @XmlElement(required = true)
    protected SpaceStats parentStats;
    @XmlElement(required = true)
    protected String snapshotId;

    /**
     * Gets the value of the parentInfo property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualVolumeInfo }
     *     
     */
    public VirtualVolumeInfo getParentInfo() {
        return parentInfo;
    }

    /**
     * Sets the value of the parentInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualVolumeInfo }
     *     
     */
    public void setParentInfo(VirtualVolumeInfo value) {
        this.parentInfo = value;
    }

    /**
     * Gets the value of the parentStats property.
     * 
     * @return
     *     possible object is
     *     {@link SpaceStats }
     *     
     */
    public SpaceStats getParentStats() {
        return parentStats;
    }

    /**
     * Sets the value of the parentStats property.
     * 
     * @param value
     *     allowed object is
     *     {@link SpaceStats }
     *     
     */
    public void setParentStats(SpaceStats value) {
        this.parentStats = value;
    }

    /**
     * Gets the value of the snapshotId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSnapshotId() {
        return snapshotId;
    }

    /**
     * Sets the value of the snapshotId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSnapshotId(String value) {
        this.snapshotId = value;
    }

}
