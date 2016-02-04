package com.emc.sa.service.vipr.plugins.tasks;

import java.io.File;  
import java.lang.reflect.Constructor;  
import java.lang.reflect.InvocationTargetException;  
import java.net.MalformedURLException;  
import java.net.URL;  
import java.net.URLClassLoader;

public class ExtensionLoader<C> {
	
	

  public C LoadClass(String directory, String classpath, Class<C> parentClass) throws ClassNotFoundException {
    File pluginsDir = new File( directory);
    for (File jar : pluginsDir.listFiles()) {
      try {
        ClassLoader loader = URLClassLoader.newInstance(
            new URL[] { jar.toURI().toURL() },
            getClass().getClassLoader()
        );
        Class<?> clazz = Class.forName(classpath, true, loader);
        Class<? extends C> newClass = clazz.asSubclass(parentClass);
        // Apparently its bad to use Class.newInstance, so we use 
        // newClass.getConstructor() instead
        Constructor<? extends C> constructor = newClass.getConstructor();
        return constructor.newInstance();

      } catch (ClassNotFoundException e) {
        // There might be multiple JARs in the directory,
        // so keep looking
        continue;
      } catch (MalformedURLException e) {
        e.printStackTrace();
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      } catch (InstantiationException e) {
        e.printStackTrace();
      }
    }
    throw new ClassNotFoundException("Class " + classpath
        + " wasn't found in directory " +  directory);
  }
  
  public static void main(String[] args) {
	  
	  String PluglinLocation="E:/Development/MYPROJECTS/BPMNWorkflow/bpmnworkflowcommon/target";
	  ExtensionLoader<CustomWorkflowPlugin> loader = new ExtensionLoader<CustomWorkflowPlugin>();  
	  try {
		CustomWorkflowPlugin somePlugin = loader.LoadClass(PluglinLocation, "com.emc.storageos.bpmn.common.ExternalPlugin", CustomWorkflowPlugin.class);
	} catch (ClassNotFoundException e) {

		e.printStackTrace();
	}
  }
}