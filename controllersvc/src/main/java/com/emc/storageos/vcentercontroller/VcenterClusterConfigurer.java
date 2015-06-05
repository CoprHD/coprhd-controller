/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.vcentercontroller;

import com.vmware.vim25.ClusterConfigSpecEx;

/**
 * Created with IntelliJ IDEA.
 * User: alaplante
 * Date: 10/7/14
 * Time: 1:48 PM
 * To change this template use File | Settings | File Templates.
 */
public interface VcenterClusterConfigurer {
    public ClusterConfigSpecEx configure(Object input) throws Exception;
}
