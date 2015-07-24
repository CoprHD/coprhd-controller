package com.emc.sa.util;

import java.util.Arrays;

public final class ArrayUtil {

	public static <T> T[] safeArrayCopy(T[] source){
		if(source != null){
			return Arrays.copyOf(source, source.length);
		}
		return source;
	}
}
