package com.emc.sa.util;

import java.util.Arrays;

public final class ArrayUtil {

	public static <T> T[] safeArray(T[] source){
		if(source != null){
			return Arrays.copyOf(source, source.length);
		}
		return source;
	}
	
	public static void main(String ... args){
		
		String[] strings = {"one", "two", "three"};
		
		String[] newStrings = ArrayUtil.safeArray(strings);
		
		System.out.println( String.format( "%s %s", strings, newStrings ));
		
	}
	
}
