package com.emc.storageos.driver.vmaxv3driver.rest.bean;

/**
 * Java bean class(nested) for "sloprovisioning/symmetrix/{}/director/{}/port/{}" GET method
 * JSON result deserialization.
 *
 * Created by gang on 6/23/16.
 */
public class SymmetrixPortKey {
    private String directorId;
    private String portId;

    @Override
    public String toString() {
        return "SymmetrixPortKey{" +
            "directorId='" + directorId + '\'' +
            ", portId='" + portId + '\'' +
            '}';
    }

    public String getDirectorId() {
        return directorId;
    }

    public void setDirectorId(String directorId) {
        this.directorId = directorId;
    }

    public String getPortId() {
        return portId;
    }

    public void setPortId(String portId) {
        this.portId = portId;
    }
}
