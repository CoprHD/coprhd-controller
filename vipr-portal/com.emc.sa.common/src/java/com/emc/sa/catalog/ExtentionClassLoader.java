package com.emc.sa.catalog;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.JclObjectFactory;
import org.xeustechnologies.jcl.context.DefaultContextLoader;
import org.xeustechnologies.jcl.context.JclContext;
import org.xeustechnologies.jcl.proxy.CglibProxyProvider;
import org.xeustechnologies.jcl.proxy.ProxyProviderFactory;

import com.emc.sa.descriptor.ServiceDefinitionLoader;
import com.emc.sa.descriptor.TestExternalInterface;

public class ExtentionClassLoader {


	private static boolean extentionLoaded = addJCLContextLoader("C:/HDRIVE/ECLIPSE-WS/CoPRHD-WS/JCL-master/Test");
	
    public static void getProxy(String pathToExternalResources) throws IOException {
    	TestExternalInterface test = getProxyObject(TestExternalInterface.class);
        String extMsg = test.sayHello();
        
        System.out.println("External MSG "+extMsg);
        
//        InputStream is = jcl.getResourceAsStream( "CustomSampleService.json" );
//        String theString = IOUtils.toString(is, "utf-8"); 
//        System.out.println("External Resource "+theString);
    }

    public static InputStream getProxyResourceAsStream(String pathToExternalResources, boolean pattern) throws IOException {
 
    	JarClassLoader jcl=JclContext.get();
        InputStream is = jcl.getResourceAsStream( pathToExternalResources );
        //String theString = IOUtils.toString(is, "utf-8"); 
        //System.out.println("External Resource "+theString);
        return is;
    }
   
    public static InputStream getProxyResourceAsStream(String pathToExternalResources) throws IOException {
    	 
    	JarClassLoader jcl=JclContext.get();
        InputStream is = jcl.getResourceAsStream( pathToExternalResources );
        //String theString = IOUtils.toString(is, "utf-8"); 
        //System.out.println("External Resource "+theString);
        return is;
    }
    
    private static synchronized boolean addJCLContextLoader(String pathToExternalResources){
       if (!extentionLoaded){
       		JarClassLoader jcl = new JarClassLoader();
       		jcl.add(pathToExternalResources);
       		extentionLoaded=true;
            DefaultContextLoader context=new DefaultContextLoader(jcl);
            context.loadContext();
            return true;
       }
       return false;
    }

    
    public static <T> T getProxyObject(Class<T> classzImpl)   {        
    	//addJCLContextLoader(pathToExternalResources);
    	JarClassLoader jcl=JclContext.get();
		
        //JarClassLoader jc = new JarClassLoader();
        //jc.add(pathToExternalResources);
        ProxyProviderFactory.setDefaultProxyProvider( new CglibProxyProvider() );

        // Create auto proxies
        JclObjectFactory factory = JclObjectFactory.getInstance( true );
        T object = (T) factory.create( jcl , classzImpl.getCanonicalName()+"Impl" );
        return object;
    }
    
    public static void main(String[] args) throws IOException {
    	ExtentionClassLoader.getProxy("C:/HDRIVE/ECLIPSE-WS/CoPRHD-WS/JCL-master/Test");
    	//ExtentionClassLoader.loadExternal(null);
		
	}

}
