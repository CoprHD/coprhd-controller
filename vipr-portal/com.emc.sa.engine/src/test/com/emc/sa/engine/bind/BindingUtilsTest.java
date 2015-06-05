package com.emc.sa.engine.bind;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.util.TextUtils;
import com.google.common.collect.Maps;

public class BindingUtilsTest {
    @Test
    public void testBind() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("value", "Hello");

        Input data = new Input();
        BindingUtils.bind(data, params);
        Assert.assertEquals("Hello", data.value);
    }

    @Test
    public void testEmptyBind() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("value", "");

        Input data = new Input();
        BindingUtils.bind(data, params);
        Assert.assertEquals("", data.value);
    }

    @Test
    public void testBindSubclass() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("value", "Hello");
        params.put("value2", "World");

        Input2 data = new Input2();
        BindingUtils.bind(data, params);
        Assert.assertEquals("Hello", data.value);
        Assert.assertEquals("World", data.value2);
    }

    @Test
    public void testBindEnum() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("value", "YES");
        EnumParam data = new EnumParam();

        BindingUtils.bind(data, params);
        Assert.assertEquals(MyEnum.YES, data.value);
    }

    @Test
    public void testBindList() {
        ListParam data = new ListParam();
        BindingUtils.bind(data, param("value", "one,two"));
        assertListValues(data.value, "one", "two");

        data = new ListParam();
        BindingUtils.bind(data, param("value", "\"one\",\"two\""));
        assertListValues(data.value, "one", "two");

        data = new ListParam();
        BindingUtils.bind(data, param("value", "\"one\",two"));
        assertListValues(data.value, "one", "two");

        data = new ListParam();
        BindingUtils.bind(data, param("value", "\"one,two\",three"));
        assertListValues(data.value, "one,two", "three");

        data = new ListParam();
        BindingUtils.bind(data, param("value", "\"one\"\"two\",three"));
        assertListValues(data.value, "one\"two", "three");

        data = new ListParam();
        BindingUtils.bind(data, param("value", "\" one \", two"));
        assertListValues(data.value, "one", "two");
    }

    private static void assertListValues(List<String> list, String... values) {
        Assert.assertNotNull(values);
        Assert.assertEquals(values.length, list.size());
        for (int i = 0; i < values.length; i++) {
            Assert.assertEquals(values[i], list.get(i));
        }
    }

    @Test
    public void testMissingBinding() {
        Map<String, Object> params = Maps.newHashMap();
        MissingRequiredParam data = new MissingRequiredParam();
        try {
            BindingUtils.bind(data, params);
            Assert.fail("Binding succeeded for missing required parameter");
        }
        catch (BindingException e) {
            // Expected
        }
    }

    @Test
    public void testOptionalBinding() {
        Map<String, Object> params = Maps.newHashMap();
        OptionalParam data = new OptionalParam();

        BindingUtils.bind(data, params);
        Assert.assertNull(data.optional);
    }

    @Test
    public void testUnnamedParam() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("value", "Hello");
        UnnamedParam data = new UnnamedParam();

        BindingUtils.bind(data, params);
        Assert.assertEquals("Hello", data.value);
    }

    @Test
    public void testUriBinding() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("value", "\"urn:storageos:SomeValue\"");
        UriParam data = new UriParam();

        BindingUtils.bind(data, params);
        Assert.assertNotNull(data.value);
        Assert.assertEquals("urn:storageos:SomeValue", data.value.toString());
    }

    @Test
    public void testBindableUninitialized() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("name", "Joe");
        params.put("value", "Hello");
        BindableValue data = new BindableValue();

        BindingUtils.bind(data, params);
        Assert.assertNotNull(data.bind.name);
        Assert.assertEquals(params.get("name"), data.bind.name);
        Assert.assertNotNull(data.bind.value);
        Assert.assertEquals(params.get("value"), data.bind.value);
        Assert.assertFalse(data.bind.initialized);
    }

    @Test
    public void testBindableInitialized() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("name", "Joe");
        params.put("value", "Hello");
        BindableValue data = new BindableValue();
        data.bind = new NameValue();
        data.bind.initialized = true;

        BindingUtils.bind(data, params);
        Assert.assertNotNull(data.bind.name);
        Assert.assertEquals(params.get("name"), data.bind.name);
        Assert.assertNotNull(data.bind.value);
        Assert.assertEquals(params.get("value"), data.bind.value);
        Assert.assertTrue(data.bind.initialized);
    }

    @Test
    public void testBindableArray() {
        testBindableValues(new BindableArray());
        testBindableValuesWithOptional(new BindableArray());
    }

    @Test
    public void testBindableList() {
        testBindableValues(new BindableList());
        testBindableValuesWithOptional(new BindableList());
    }

    protected void testBindableValues(BindableValues<NameValueOptional> data) {
        Map<String, Object> params = Maps.newHashMap();
        params.put("name", TextUtils.formatCSV("one", "two"));
        params.put("value", TextUtils.formatCSV("One", "Two"));

        BindingUtils.bind(data, params);
        Assert.assertEquals(2, data.size());

        Assert.assertEquals("one", data.get(0).name);
        Assert.assertEquals("One", data.get(0).value);

        Assert.assertEquals("two", data.get(1).name);
        Assert.assertEquals("Two", data.get(1).value);
    }

    protected void testBindableValuesWithOptional(BindableValues<NameValueOptional> data) {
        Map<String, Object> params = Maps.newHashMap();
        params.put("name", TextUtils.formatCSV("one", "two", "three"));
        params.put("value", "One");

        BindingUtils.bind(data, params);
        Assert.assertEquals(3, data.size());

        Assert.assertEquals("one", data.get(0).name);
        Assert.assertEquals("One", data.get(0).value);

        Assert.assertEquals("two", data.get(1).name);
        Assert.assertNull(data.get(1).value);

        Assert.assertEquals("three", data.get(2).name);
        Assert.assertNull(data.get(2).value);
    }

    @Test
    public void testBindableAssignmentCompatible() {
        AssignmentCompatible data = new AssignmentCompatible();
        BindingUtils.bind(data, param("tag", "TAG1,TAG2,TAG3"));
        Assert.assertNotNull(data.values);
        Assert.assertEquals(3, data.values.length);
        Assert.assertEquals("TAG1", data.values[0].getValue());
        Assert.assertEquals("TAG2", data.values[1].getValue());
        Assert.assertEquals("TAG3", data.values[2].getValue());
    }

    private static Map<String, Object> param(String name, Object value) {
        Map<String, Object> params = Maps.newHashMap();
        params.put(name, value);
        return params;
    }

    public static class Input {
        @Param("value")
        protected String value;
    }

    public static class Input2 extends Input {
        @Param("value2")
        protected String value2;
    }

    public static class MissingRequiredParam {
        @Param("missing")
        protected String missing;
    }

    public static class OptionalParam {
        @Param(value = "optional", required = false)
        protected String optional;
    }

    public static class UnnamedParam {
        @Param
        protected String value;
    }

    public static class EnumParam {
        @Param
        protected MyEnum value;
    }

    public static enum MyEnum {
        YES, NO
    }

    public static class UriParam {
        @Param
        protected URI value;
    }

    public static class NameValue {
        @Param
        protected String name;
        @Param
        protected String value;
        protected boolean initialized;
    }

    public static class NameValueOptional {
        @Param
        protected String name;
        @Param(required = false)
        protected String value;
    }

    public static class BindableValue {
        @Bindable
        protected NameValue bind;
    }

    public static class ListParam {
        @Param
        protected List<String> value;
    }

    public static interface BindableValues<T> {
        public int size();

        public T get(int index);
    }

    public static class BindableArray implements BindableValues<NameValueOptional> {
        @Bindable(itemType = NameValueOptional.class)
        protected NameValueOptional[] values;

        @Override
        public int size() {
            return values.length;
        }

        @Override
        public NameValueOptional get(int index) {
            return values[index];
        }
    }

    public static class BindableList implements BindableValues<NameValueOptional> {
        @Bindable(itemType = NameValueOptional.class)
        protected List<NameValueOptional> values;

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public NameValueOptional get(int index) {
            return values.get(index);
        }
    }

    public static class AssignmentCompatible {
        @Bindable(itemType = TagImpl.class)
        protected Tag[] values;
    }

    public static interface Tag {
        public String getValue();
    }

    public static class TagImpl implements Tag {
        @Param
        protected String tag;

        @Override
        public String getValue() {
            return tag;
        }
    }
}
