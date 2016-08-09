package com.emc.storageos.model.file;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.vipr.model.catalog.AssetOption;

/**
 * Holds the mount attributes for operations
 * 
 * @author yelkaa
 * 
 */
public class MountInfo {
    private URI hostId;
    private URI fsId;
    private String mountPath;
    private String subDirectory;
    private String securityType;

    // private String tag;

    @XmlElement(name = "host")
    @JsonProperty("host")
    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }

    @XmlElement(name = "filesystem")
    @JsonProperty("filesystem")
    public URI getFsId() {
        return fsId;
    }

    public void setFsId(URI fsId) {
        this.fsId = fsId;
    }

    @XmlElement(name = "mount_path")
    @JsonProperty("mount_path")
    public String getMountPath() {
        return mountPath;
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }

    @XmlElement(name = "sub_directory")
    @JsonProperty("sub_directory")
    public String getSubDirectory() {
        return subDirectory;
    }

    public void setSubDirectory(String subDirectory) {
        this.subDirectory = subDirectory;
    }

    @XmlElement(name = "security_type")
    @JsonProperty("security_type")
    public String getSecurityType() {
        return securityType;
    }

    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }

    /*
     * @XmlElement(name = "tag")
     * 
     * @JsonProperty("tag")
     * public String getTag() {
     * return tag;
     * }
     * 
     * public void setTag(String tag) {
     * this.tag = tag;
     * }
     */
    public String getMountString() {
        StringBuffer strMount = new StringBuffer();

        String subDirPath = "";
        if (getSubDirectory() != null && getSubDirectory().equalsIgnoreCase("!nodir")) {
            subDirPath = "/" + getSubDirectory();
        }
        strMount.append(getHostId()).append(";")
                .append(getFsId()).append(";")
                .append(getSecurityType()).append(";")
                .append(getMountPath()).append(";")
                .append(subDirPath);

        return strMount.toString();
    }

    public static MountInfo getMountInfo(String strMountInfo) {
        if (strMountInfo != null && !strMountInfo.isEmpty()) {
            MountInfo mountInfo = new MountInfo();

            String[] mountAttrs = strMountInfo.split(";");

            if (mountAttrs.length > 0) {
                mountInfo.setHostId(URIUtil.uri(mountAttrs[0]));
            }
            if (mountAttrs.length > 1) {
                mountInfo.setFsId(URIUtil.uri(mountAttrs[1]));
            }
            if (mountAttrs.length > 2) {
                mountInfo.setSecurityType(mountAttrs[2]);
            }

            if (mountAttrs.length > 3) {
                mountInfo.setMountPath(mountAttrs[3]);
            }

            if (mountAttrs.length > 4) {
                mountInfo.setSubDirectory(mountAttrs[4]);
            }
            return mountInfo;
        }
        return null;
    }

}