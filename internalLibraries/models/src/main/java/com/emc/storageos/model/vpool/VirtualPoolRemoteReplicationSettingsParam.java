/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import java.net.URI;

/**
 * Remote replication settings for virtual pool.
 */
public class VirtualPoolRemoteReplicationSettingsParam {

    /**
     * The remote virtual array.
     */

    private URI varray;

    /**
     * The remote virtual pool.
     */
    private URI vpool;

    public VirtualPoolRemoteReplicationSettingsParam() {
    }

    public VirtualPoolRemoteReplicationSettingsParam(URI varray, URI vpool) {
            this.varray = varray;
            this.vpool = vpool;
        }


        @XmlElement(name = "varray", required = true)
        public URI getVarray() {
            return varray;
        }

        public void setVarray(URI varray) {
            this.varray = varray;
        }

        @XmlElement(name = "vpool")
        public URI getVpool() {
            return vpool;
        }

        public void setVpool(URI vpool) {
            this.vpool = vpool;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((varray == null) ? 0 : varray.hashCode());
            result = prime * result + ((vpool == null) ? 0 : vpool.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            VirtualPoolRemoteReplicationSettingsParam other = (VirtualPoolRemoteReplicationSettingsParam) obj;

            if (varray == null) {
                if (other.varray != null) {
                    return false;
                }
            } else if (!varray.equals(other.varray)) {
                return false;
            }
            if (vpool == null) {
                if (other.vpool != null) {
                    return false;
                }
            } else if (!vpool.equals(other.vpool)) {
                return false;
            }
            return true;
        }
}
