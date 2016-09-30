package com.emc.sa.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.JclObjectFactory;
import org.xeustechnologies.jcl.context.DefaultContextLoader;
import org.xeustechnologies.jcl.context.JclContext;
import org.xeustechnologies.jcl.proxy.CglibProxyProvider;
import org.xeustechnologies.jcl.proxy.ProxyProviderFactory;
import org.xeustechnologies.jcl.test.TestInterface;

import com.emc.sa.descriptor.ServiceDefinitionLoader;
import com.emc.sa.descriptor.TestExternalInterface;


public class ExtentionClassLoader {

	private static String  pathToExternalResources="/opt/storageos/customFlow";
	//private static String  pathToExternalResources="C:/MYTEMP/HACKPOC/TestPoC/";
	public static String getPathToExternalResources() {
		return pathToExternalResources;
	}

	public static void setPathToExternalResources(String pathToExternalResources) {
		ExtentionClassLoader.pathToExternalResources = pathToExternalResources;
	}
	
	static{
		
		addJCLContextLoader();
    }


	
//    public static void getProxy() throws IOException {
//    	TestExternalInterface test = getProxyObject(TestExternalInterface.class);
//        String extMsg = test.sayHello();
//        
//        System.out.println("External MSG "+extMsg);
//        
//        InputStream is = jcl.getResourceAsStream( "CustomSampleService.json" );
//        String theString = IOUtils.toString(is, "utf-8"); 
//        System.out.println("External Resource "+theString);
//    }

    public static InputStream getProxyResourceAsStream(boolean pattern) throws IOException {
 
    	JarClassLoader jcl=JclContext.get();
        InputStream is = jcl.getResourceAsStream( pathToExternalResources );
        //String theString = IOUtils.toString(is, "utf-8"); 
        //System.out.println("External Resource "+theString);
        return is;
    }
   
    public static InputStream getProxyResourceAsStream(String resources) throws IOException {
    	 
    	JarClassLoader jcl=JclContext.get();
        InputStream is = jcl.getResourceAsStream( resources );
        //String theString = IOUtils.toString(is, "utf-8"); 
        //System.out.println("External Resource "+theString);
        return is;
    }
    
	private static synchronized boolean addJCLContextLoader(){
       		JarClassLoader jcl = new JarClassLoader();
       		jcl.add(pathToExternalResources);
       		jcl.getSystemLoader().setOrder(1); // Look in system class loader first
       		jcl.getLocalLoader().setOrder(2); // if not found look in local class loader
       	  jcl.getParentLoader().setOrder(3); // if not found look in parent class loader
       	  jcl.getThreadLoader().setOrder(4); // if not found look in thread context class loader
       	  jcl.getCurrentLoader().setOrder(5);
            DefaultContextLoader context=new DefaultContextLoader(jcl);
            context.loadContext();
            return true;
    }

    
    public static <T> T getProxyObject(Class<T> classzImpl)   {        
    	JarClassLoader jcl=JclContext.get();
        //JarClassLoader jc = new JarClassLoader();
        //jc.add(pathToExternalResources);
        ProxyProviderFactory.setDefaultProxyProvider( new CglibProxyProvider() );
        // Create auto proxies
        JclObjectFactory factory = JclObjectFactory.getInstance( true );
        T object = (T) factory.create( jcl , classzImpl.getCanonicalName()+"Impl" );
        return object;
    }
 
    public static <T> T getProxyObject(String classzImpl)   {        
    	JarClassLoader jcl=JclContext.get();
        //JarClassLoader jc = new JarClassLoader();
        //jc.add(pathToExternalResources);
        ProxyProviderFactory.setDefaultProxyProvider( new CglibProxyProvider() );

        // Create auto proxies
        JclObjectFactory factory = JclObjectFactory.getInstance( true );
        T object = (T) factory.create( jcl , classzImpl );
        return object;
    }
    public static void main(String[] args) throws IOException {
        TestInterface obj = (TestInterface) ExtentionClassLoader.getProxyObject("org.xeustechnologies.jcl.test.Test" );
    	System.out.println("Messge from extension loader "+obj.sayHello());
    	
    	
    	//ExtentionClassLoader.loadExternal(null);
		
	}

}
