/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.machinetags;

import java.util.Arrays;
import java.util.List;

public abstract class MultiValueMachineTag {

	public static final String NAMESPACE = "isa";
	
	protected final MachineTagsCollection tags = new MachineTagsCollection();

	public MultiValueMachineTag(MachineTag... machineTags) {
		tags.addAll(Arrays.asList(machineTags));
	}

	public MachineTagsCollection getTags() {
		return tags;
	}

	public MachineTag getTag(String key) {
		MachineTagsCollection candidates = tags.findAll(getNamespace(), key);
		if ( candidates.size() > 1 ) {
			throw new IllegalStateException("Exactly one tag with the key ["+key+"] is allowed.");
		}
		return candidates.get(0);
	}
	
	public abstract String getNamespace();
	
	public abstract List<String> getRelatedTags();
	
	public Integer index() {
		return tags.get(0).index;
	}
}
