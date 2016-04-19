/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.file.FileExportUpdateParams.ExportOperationErrorType;

@XmlRootElement
public class ExportRule {

    // Part of payload Model
    private String anon;
    private String secFlavor;
    private List<String> readOnlyHosts;
    private List<String> readWriteHosts;
    private List<String> rootHosts;

    // Not a part of payload model - for internal use only.
    private boolean isToProceed = false;
    private ExportOperationErrorType errorTypeIfNotToProceed;

    public boolean isToProceed() {
        return isToProceed;
    }

    public void setToProceed(boolean isToProceed, ExportOperationErrorType type) {
        this.isToProceed = isToProceed;
        errorTypeIfNotToProceed = type;
    }

    public ExportOperationErrorType getErrorTypeIfNotToProceed() {
        return errorTypeIfNotToProceed;
    }

    @XmlElementWrapper(name = "readOnlyHosts")
    @XmlElement(name = "endPoint")
    public List<String> getReadOnlyHosts() {
        return readOnlyHosts;
    }

    public void setReadOnlyHosts(List<String> readOnlyHosts) {
        this.readOnlyHosts = readOnlyHosts;
    }

    @XmlElementWrapper(name = "readWriteHosts")
    @XmlElement(name = "endPoint")
    public List<String> getReadWriteHosts() {
        return readWriteHosts;
    }

    public void setReadWriteHosts(List<String> readWriteHosts) {
        this.readWriteHosts = readWriteHosts;
    }

    @XmlElementWrapper(name = "rootHosts")
    @XmlElement(name = "endPoint")
    public List<String> getRootHosts() {
        return rootHosts;
    }

    public void setRootHosts(List<String> rootHosts) {
        this.rootHosts = rootHosts;
    }

    /**
     * Security flavor of an export e.g. sys, krb, krbp or krbi
     * 
     * 
     */
    @XmlElement(name = "secFlavor", required = false)
    public String getSecFlavor() {
        return secFlavor;
    }

    public void setSecFlavor(String secFlavor) {
        this.secFlavor = secFlavor;
    }

    /**
     * Anonymous root user mapping e.g. "root", "nobody" or "anyUserName"
     * 
     * 
     */
    @XmlElement(name = "anon", required = false)
    public String getAnon() {
        return anon;
    }

    public void setAnon(String anon) {
        this.anon = anon;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("secFlavor : ").append(
                (secFlavor != null) ? secFlavor : "");
        sb.append("anon : ").append(
                (anon != null) ? anon : "");
        sb.append("readOnlyHosts : ").append(
                (readOnlyHosts != null) ? readOnlyHosts.size() : 0);
        sb.append("readWriteHosts : ").append(
                (readWriteHosts != null) ? readWriteHosts.size() : 0);
        sb.append("rootHosts : ").append(
                (rootHosts != null) ? rootHosts.size() : 0);

        return sb.toString();

    }

}
