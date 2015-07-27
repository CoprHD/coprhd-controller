/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs;

import com.google.common.collect.Lists;
import controllers.util.FlashException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import org.apache.commons.lang.StringUtils;
import org.springframework.core.annotation.AnnotationUtils;
import play.Play;
import play.classloading.ApplicationClasses;
import play.classloading.enhancers.Enhancer;
import play.exceptions.ActionNotFoundException;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.mvc.ActionInvoker;
import play.mvc.Controller;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Verifies FlashException redirects to ensure they don't point to an invalid action.
 */
@OnApplicationStart
public class VerifyExceptionAnnotations extends Job {

    public void doJob() throws NotFoundException {
        for(ApplicationClasses.ApplicationClass klass : Play.classes.all()) {
            if (Controller.class.isAssignableFrom(klass.javaClass)) {
                Class controller = klass.javaClass;
                for (Method method : controller.getMethods()) {
                    if (method.isAnnotationPresent(FlashException.class)) {
                        FlashException handler = AnnotationUtils.findAnnotation(method, FlashException.class);
                        assertActionIsValid(controller, method, handler);
                    }
                }
            }
        }
    }

    private void assertActionIsValid(Class controller, Method method, FlashException handler) throws NotFoundException {
        if (!handler.verify()) {
            return;
        }
        String[] actionList = (StringUtils.isEmpty(handler.value())) ? handler.referrer() : new String[] { handler.value() };
        
        for (String action : actionList) {
            //if action is blank we redirect to the referrer, so there's nothing to verify
            if (!action.equals("")) {
                if (!action.contains(".")) {
                    action = controller.getName() + "." + action;
                }

                try {
                    ActionInvoker.getActionMethod(action);
                } catch (ActionNotFoundException e) {
                    UnsupportedOperationException ex = new UnsupportedOperationException("Action method " + action + " couldn't be resolved");

                    List<StackTraceElement> stackTrace = Lists.newArrayList();
                    stackTrace.add(createStackTraceElement(controller, method));
                    Collections.addAll(stackTrace, e.getStackTrace());

                    ex.setStackTrace(stackTrace.toArray(new StackTraceElement[stackTrace.size()]));
                    throw ex;
                }
            }
        }
    }

    /**
     * Use javassist to create a StackTraceElement with line numbers from a class + method.
     */
    private StackTraceElement createStackTraceElement(Class controller, Method method) throws NotFoundException {
        ClassPool pool = Enhancer.newClassPool();
        CtClass cls = pool.get(controller.getCanonicalName());

        return new StackTraceElement(
                controller.getCanonicalName(),
                method.getName(),
                cls.getClassFile2().getSourceFile(),
                cls.getDeclaredMethod(method.getName()).getMethodInfo2().getLineNumber(0) - 1);
    }
}
