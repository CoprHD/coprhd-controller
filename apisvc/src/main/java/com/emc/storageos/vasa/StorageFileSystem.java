
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StorageFileSystem complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StorageFileSystem">
 *   &lt;complexContent>
 *     &lt;extension base="{http://data.vasa.vim.vmware.com/xsd}BaseStorageEntity">
 *       &lt;sequence>
 *         &lt;element name="backingConfig" type="{http://data.vasa.vim.vmware.com/xsd}BackingConfig" minOccurs="0"/>
 *         &lt;element name="fileSystem" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="fileSystemInfo" type="{http://data.vasa.vim.vmware.com/xsd}FileSystemInfo" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="fileSystemVersion" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="nativeSnapshotSupported" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="thinProvisioningStatus" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StorageFileSystem", namespace = "http://data.vasa.vim.vmware.com/xsd", propOrder = {
    "backingConfig",
    "fileSystem",
    "fileSystemInfo",
    "fileSystemVersion",
    "nativeSnapshotSupported",
    "thinProvisioningStatus"
})
public class StorageFileSystem
    extends BaseStorageEntity
{

    @XmlElementRef(name = "backingConfig", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<BackingConfig> backingConfig;
    @XmlElementRef(name = "fileSystem", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> fileSystem;
    protected List<FileSystemInfo> fileSystemInfo;
    @XmlElementRef(name = "fileSystemVersion", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> fileSystemVersion;
    protected Boolean nativeSnapshotSupported;
    @XmlElementRef(name = "thinProvisioningStatus", namespace = "http://data.vasa.vim.vmware.com/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<String> thinProvisioningStatus;

    /**
     * Gets the value of the backingConfig property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link BackingConfig }{@code >}
     *     
     */
    public JAXBElement<BackingConfig> getBackingConfig() {
        return backingConfig;
    }

    /**
     * Sets the value of the backingConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link BackingConfig }{@code >}
     *     
     */
    public void setBackingConfig(JAXBElement<BackingConfig> value) {
        this.backingConfig = value;
    }

    /**
     * Gets the value of the fileSystem property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getFileSystem() {
        return fileSystem;
    }

    /**
     * Sets the value of the fileSystem property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setFileSystem(JAXBElement<String> value) {
        this.fileSystem = value;
    }

    /**
     * Gets the value of the fileSystemInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the fileSystemInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFileSystemInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link FileSystemInfo }
     * 
     * 
     */
    public List<FileSystemInfo> getFileSystemInfo() {
        if (fileSystemInfo == null) {
            fileSystemInfo = new ArrayList<FileSystemInfo>();
        }
        return this.fileSystemInfo;
    }

    /**
     * Gets the value of the fileSystemVersion property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getFileSystemVersion() {
        return fileSystemVersion;
    }

    /**
     * Sets the value of the fileSystemVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setFileSystemVersion(JAXBElement<String> value) {
        this.fileSystemVersion = value;
    }

    /**
     * Gets the value of the nativeSnapshotSupported property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isNativeSnapshotSupported() {
        return nativeSnapshotSupported;
    }

    /**
     * Sets the value of the nativeSnapshotSupported property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setNativeSnapshotSupported(Boolean value) {
        this.nativeSnapshotSupported = value;
    }

    /**
     * Gets the value of the thinProvisioningStatus property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getThinProvisioningStatus() {
        return thinProvisioningStatus;
    }

    /**
     * Sets the value of the thinProvisioningStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setThinProvisioningStatus(JAXBElement<String> value) {
        this.thinProvisioningStatus = value;
    }

}
