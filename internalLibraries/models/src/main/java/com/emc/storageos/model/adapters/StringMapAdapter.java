/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model.adapters;



import com.emc.storageos.model.StringHashMapEntry;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 *  An JAXB XML adapter from Map<String,String> into  List<StringHashMapEntry>
 *
 *  JAXB automatically converts a Map into a list to marshal it into an XML file.
 *  The elements of a HashMap are "entry" pairs of key/value. If we want to replace the
 *  HashMap elements with names "entry", "key", "value" into elements consistent with our
 *  definition of REST guidelines, we need to translate Maps into Lists ourselves.
 *  This class provide an adapter from Map<String,String>> into List<StringHashMapEntry>
 *  This class should be used in conjuction with XML annotation  "XmlJavaTypeAdapter"
 */
public class StringMapAdapter extends XmlAdapter <List<StringHashMapEntry>,Map<String,String>>
{
    @Override
    public Map<String,String> unmarshal(List<StringHashMapEntry> list) {
        if (list == null) return (HashMap<String,String>) null;
        Map<String,String> map = new HashMap<String, String>(2*list.size());
        for ( StringHashMapEntry entry : list){
            map.put(entry.getName(),entry.getValue());
        }
        return map;
    }

    @Override
    public List<StringHashMapEntry> marshal(Map<String,String> map) {
        if ( map == null ) return  (ArrayList<StringHashMapEntry>) null;
        List<StringHashMapEntry> list = new ArrayList<StringHashMapEntry>();
        if (map != null) {
            for ( Entry<String,String> entry : map.entrySet()) {
                StringHashMapEntry listEnt = new StringHashMapEntry(entry.getKey(),entry.getValue());
                list.add(listEnt);
            }
        }
        return list;
    }




}
