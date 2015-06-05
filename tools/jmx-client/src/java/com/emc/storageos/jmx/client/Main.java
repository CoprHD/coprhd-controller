/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.jmx.client;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;

/**
 * This is a simple test class for collecting jetty connection and request statistics from jconsole
 * args are jetty jmx urls like service:jmx:rmi://10.247.99.210:10100/jndi/rmi://10.247.99.210:10101/sos
 */
public class Main {

    public static void main(String[] args) throws Exception {
        MBeanServerConnection mbs;
        if(args[0].equals("reset")){
            for(int i = 1; i < args.length; i++){
                mbs = getServiceConnection(args[i]);
                resetStats(args[i], mbs);
                getConnectorStats(args[i], mbs);
            }
        }else{
            for(String url : args){
                mbs = getServiceConnection(url);
                getConnectorStats(url, mbs);
            }
        }
    }

    private static MBeanServerConnection getServiceConnection(String url) throws IOException {
        JMXServiceURL jmxUrl = new JMXServiceURL(url);
        JMXConnector conn = JMXConnectorFactory.connect(jmxUrl);
        MBeanServerConnection mbs = conn.getMBeanServerConnection();
        return mbs;
    }

    private static void getConnectorStats(String url, MBeanServerConnection mbs)
            throws IOException, MalformedObjectNameException, InstanceNotFoundException,
            IntrospectionException, ReflectionException, MBeanException, AttributeNotFoundException {
        ObjectName connectionName = new ObjectName("bean:name=s3Connector");
        ObjectName statsName = new ObjectName("bean:name=s3Statistics");

        Integer con = (Integer)mbs.getAttribute(connectionName,"Connections");
        Integer mcon = (Integer)mbs.getAttribute(connectionName,"ConnectionsRequestsMax");
        Long min = (Long)mbs.getAttribute(connectionName,"ConnectionsDurationMin");
        Long max = (Long)mbs.getAttribute(connectionName,"ConnectionsDurationMax");
        Long ave = (Long)mbs.getAttribute(connectionName,"ConnectionsDurationAve");
        System.out.print(String.format("%s cons: %d, conmax: %d, min: %d, max: %d, avg: %d\n", url, con, mcon, min, max, ave));

        Integer requests = (Integer)mbs.getAttribute(statsName,"Requests");
        Long rmin = (Long)mbs.getAttribute(statsName,"RequestTimeMin");
        Long rmax = (Long)mbs.getAttribute(statsName,"RequestTimeMax");
        Long rave = (Long)mbs.getAttribute(statsName,"RequestTimeAverage");
        System.out.print(String.format("%s requests: %d, rmin: %d, rmax: %d, ravg: %d\n", url, requests, rmin, rmax, rave));
    }

    private static void resetStats(String url, MBeanServerConnection mbs)
            throws IOException, MalformedObjectNameException, InstanceNotFoundException,
            IntrospectionException, ReflectionException, MBeanException, AttributeNotFoundException {
        ObjectName connectionName = new ObjectName("bean:name=s3Connector");
        ObjectName statsName = new ObjectName("bean:name=s3Statistics");
        mbs.invoke(connectionName, "statsReset",new Object[ ] {}, new String[ ] {});
        mbs.invoke(statsName, "statsReset", new Object[ ] {}, new String[ ] {});
        System.out.print(String.format("%s statistics reset\n", url));
    }
}
