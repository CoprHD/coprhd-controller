/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.test;

import java.net.MalformedURLException;
import java.net.URL;

import org.springframework.beans.BeanUtils;
import org.testng.annotations.Test;

public class BeanUtilTest {
	
	
	public static class TestClass{
		public String getA() {
			return a;
		}
		public void setA(String a) {
			this.a = a;
		}
		public int getB() {
			return b;
		}
		public void setB(int b) {
			this.b = b;
		}
		public Object getC() {
			return c;
		}
		public void setC(Object c) {
			this.c = c;
		}
		String a;
		int b;
		Object c;
		TestClass2 class2;
		public TestClass2 getClass2() {
			return class2;
		}
		public void setClass2(TestClass2 class2) {
			this.class2 = class2;
		}
		
		
	}
	
	public static class TestClass2{
		
		int a;

		public int getA() {
			return a;
		}

		public void setA(int a) {
			this.a = a;
		}
		
	}
	
	
	@Test(groups="runByDefault")
	public void testBeanUtil(){
		
		TestClass bean = new TestClass();
		TestClass2 bean2 = new TestClass2();
		bean.setClass2(bean2);
		
		System.out.println("Prop type: class2 " + BeanUtils.findPropertyType("class2", new Class<?>[]{TestClass.class}));
		System.out.println("Prop type: a " + BeanUtils.findPropertyType("a", new Class<?>[]{TestClass.class}));
		System.out.println("Prop type: b " + BeanUtils.findPropertyType("b", new Class<?>[]{TestClass.class}));
		System.out.println("Prop type: c " + BeanUtils.findPropertyType("c", new Class<?>[]{TestClass.class}));
		
	}

	@Test(groups="disabled")
	public void testURL() throws MalformedURLException{
		
		URL url = new URL("http", "10.247.84.170", 80, "/nuova");
		System.out.println(url.toString());
		
	}	
	
}
