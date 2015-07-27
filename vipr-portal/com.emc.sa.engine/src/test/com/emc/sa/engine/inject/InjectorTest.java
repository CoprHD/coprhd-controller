/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.inject;

import java.util.Map;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Maps;

public class InjectorTest {

    private Map<Class<?>, Object> createRegistry(Object... args) {
        Map<Class<?>, Object> registry = Maps.newHashMap();
        for (Object arg : args) {
            registry.put(arg.getClass(), arg);
        }
        return registry;
    }

    private Map<Class<?>, Object> createSingleRegistry(Class<?> clazz, Object value) {
        Map<Class<?>, Object> registry = Maps.newHashMap();
        registry.put(clazz, value);
        return registry;
    }

    private <T> T inject(T target, Map<Class<?>, Object> data) {
        Injector.inject(target, data);
        return target;
    }

    @Test
    public void testSimpleInject() {
        Map<Class<?>, Object> registry = createRegistry(new Foo(), new FooBar());

        UseFoo useFoo = inject(new UseFoo(), registry);
        Assert.assertNotNull(useFoo.foo);
        Assert.assertEquals(useFoo.foo, registry.get(Foo.class));

        UseFooBar useFooBar = inject(new UseFooBar(), registry);
        Assert.assertNotNull(useFooBar.foobar);
        Assert.assertEquals(useFooBar.foobar, registry.get(FooBar.class));
    }

    @Test
    public void testInheritedInject() {
        Map<Class<?>, Object> registry = createRegistry(new Foo(), new FooBar());

        UseFooAndFooBar useFooAndFooBar = inject(new UseFooAndFooBar(), registry);
        Assert.assertNotNull(useFooAndFooBar.foo);
        Assert.assertEquals(useFooAndFooBar.foo, registry.get(Foo.class));
        Assert.assertNotNull(useFooAndFooBar.foobar);
        Assert.assertEquals(useFooAndFooBar.foobar, registry.get(FooBar.class));

        UseFooBarAndFoo useFooBarAndFoo = inject(new UseFooBarAndFoo(), registry);
        Assert.assertNotNull(useFooBarAndFoo.foo);
        Assert.assertEquals(useFooBarAndFoo.foo, registry.get(Foo.class));
        Assert.assertNotNull(useFooBarAndFoo.foobar);
        Assert.assertEquals(useFooAndFooBar.foobar, registry.get(FooBar.class));
    }

    @Test
    public void testInjectByDeclaredType() {
        UseFoo foo = inject(new UseFoo(), createSingleRegistry(Foo.class, new FooBar()));
        Assert.assertEquals(foo.foo.getClass(), FooBar.class);

        UseFoo otherFoo = inject(new UseFoo(), createSingleRegistry(Foo.class, new Object()));
        Assert.assertNull(otherFoo.foo);
    }

    public static class Foo {
    }

    public static class FooBar extends Foo {
    }

    public static class UseFoo {
        @Inject
        protected Foo foo;
    }

    public static class UseFooBar {
        @Inject
        protected FooBar foobar;
    }

    public static class UseFooAndFooBar extends UseFoo {
        @Inject
        protected FooBar foobar;
    }

    public static class UseFooBarAndFoo extends UseFooBar {
        @Inject
        protected Foo foo;
    }
}
