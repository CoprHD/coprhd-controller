
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Exception complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Exception">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Exception" type="{http://www.w3.org/2001/XMLSchema}anyType" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Exception", propOrder = {
    "exception"
})
@XmlSeeAlso({
    LostEvent.class,
    NotImplemented.class,
    SnapshotTooMany.class,
    Timeout.class,
    PermissionDenied.class,
    NotSupported.class,
    TooMany.class,
    VasaProviderBusy.class,
    InvalidCertificate.class,
    InvalidLogin.class,
    NotFound.class,
    InvalidSession.class,
    OutOfResource.class,
    ResourceInUse.class,
    InvalidProfile.class,
    LostAlarm.class,
    NotCancellable.class,
    InvalidArgument.class,
    InvalidStatisticsContext.class,
    ActivateProviderFailed.class,
    StorageFault.class,
    IncompatibleVolume.class,
    InactiveProvider.class
})
public class Exception {

    @XmlElementRef(name = "Exception", namespace = "http://com.vmware.vim.vasa/2.0/xsd", type = JAXBElement.class, required = false)
    protected JAXBElement<Object> exception;

    /**
     * Gets the value of the exception property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link Object }{@code >}
     *     
     */
    public JAXBElement<Object> getException() {
        return exception;
    }

    /**
     * Sets the value of the exception property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link Object }{@code >}
     *     
     */
    public void setException(JAXBElement<Object> value) {
        this.exception = value;
    }

}
