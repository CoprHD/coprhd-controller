package com.emc.storageosplugin.model.vce;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CommonUtils {
	
	
    public static URI uri(String value)  {
        try {
            return (value != null && value.length() > 0) ? URI.create(value) : null;
        } catch(IllegalArgumentException invalid) {
            return null;
        }
    }

    /**
     * Converts a collection of strings to a list of URIs, null safe.
     * 
     * @param values
     *        the string values.
     * @return the URIs.
     */
    public static List<URI> uris(Collection<String> values) {
        List<URI> results = new ArrayList<URI>();
        if (values != null) {
            for (String value : values) {
                URI uri = uri(value);
                if (uri != null) {
                    results.add(uri);
                }
            }
        }
        return results;
    }


}
