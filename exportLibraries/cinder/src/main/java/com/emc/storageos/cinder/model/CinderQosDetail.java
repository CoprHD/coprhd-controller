package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;
import java.util.Map;

public class CinderQosDetail{

	@XmlElement(name = "qos_specs")
	public CinderQos qos_spec = new CinderQos();

    @XmlElement(name = "links")
    public QosLinks links = new QosLinks();

    public class QosLinks {
        public Map<String, String> links;
    }
}

