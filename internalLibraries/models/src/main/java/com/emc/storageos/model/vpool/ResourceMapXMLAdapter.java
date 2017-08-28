/* Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

public class ResourceMapXMLAdapter extends XmlAdapter<ListOfResourceEntry,Map<NamedRelatedResourceRep,List<NamedRelatedResourceRep>>> {
    @Override
    public Map<NamedRelatedResourceRep,List<NamedRelatedResourceRep>> unmarshal(ListOfResourceEntry loe)
        throws Exception {
        Map<NamedRelatedResourceRep,List<NamedRelatedResourceRep>> map = new HashMap();
        for(ResourceEntry entry : loe.getList() ) {
            map.put(entry.getKey(), entry.getList() );
        }
        return map;
    }

    @Override
    public ListOfResourceEntry marshal(Map<NamedRelatedResourceRep,List<NamedRelatedResourceRep>> map)
        throws Exception {
        ListOfResourceEntry loe = new ListOfResourceEntry();
        for(Map.Entry<NamedRelatedResourceRep,List<NamedRelatedResourceRep>> mapEntry : map.entrySet()) {
            ResourceEntry entry = new ResourceEntry();
            entry.setKey( mapEntry.getKey() );
            entry.getList().addAll( mapEntry.getValue() );
            loe.getList().add(entry);
        }
        return loe;
    }
}

