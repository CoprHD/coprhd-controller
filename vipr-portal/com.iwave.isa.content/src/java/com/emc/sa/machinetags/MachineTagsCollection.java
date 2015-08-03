/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.machinetags;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Set;

public class MachineTagsCollection extends ArrayList<MachineTag> {

    private static final long serialVersionUID = -2298788221259873977L;

    /** locate the first occurrence of a tag with the given namespace, key, and index */
    public MachineTag find(String namespace, String key, Integer index) {
        for (MachineTag searchTag : this) {
            if (searchTag.isTag(namespace, key, index)) {
                return searchTag;
            }
        }
        return null;
    }

    /** locate the first occurrence of a tag with the given namespace, key. Tag must have no index to match. */
    public MachineTag find(String namespace, String key) {
        return find(namespace, key, null);
    }

    /** locate all tags with the given namespace and key. Ignores index. */
    public MachineTagsCollection findAll(String namespace, String key) {
        MachineTagsCollection foundTags = new MachineTagsCollection();
        for (MachineTag searchTag : this) {
            if (searchTag.namespace.equals(namespace) && searchTag.key.equals(key)) {
                foundTags.add(searchTag);
            }
        }
        return foundTags;
    }

    /** find all tags which use the given namespace and index and one of the supplied keys */
    public MachineTagsCollection findTags(String namespace, Integer index, String... keys) {
        MachineTagsCollection foundTags = new MachineTagsCollection();
        for (String key : keys) {
            foundTags.addAll(findOccurrencesOfTag(namespace, index, key));
        }
        return foundTags;
    }

    /** find all tags which use the given namespace, index, and key */
    public MachineTagsCollection findOccurrencesOfTag(String namespace, Integer index, String key) {
        MachineTagsCollection foundTags = new MachineTagsCollection();
        for (MachineTag searchTag : this) {
            if (searchTag.isTag(namespace, key, index)) {
                foundTags.add(searchTag);
            }
        }
        return foundTags;
    }

    /** create a list of raw tag strings that can be used when interfacing with the API directly */
    public Set<String> generateRawTags() {
        final Set<String> rawTags = Sets.newLinkedHashSet();
        for (MachineTag tag : this) {
            rawTags.add(tag.toString());
        }
        return rawTags;
    }

}
