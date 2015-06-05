/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import play.mvc.results.RenderJson;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Json {

	/**
	 * Serializes the given object to JSON, ignoring any fields marked with {@link JsonIgnore}
	 */
	public static String toSafeJson(Object object) {
		Gson gson = new GsonBuilder().addSerializationExclusionStrategy(new IgnoreExclusionStrategy())
				 .create();

    	return gson.toJson(object);
	}

    /**
     * Renders a Controller response as JSON, ignoring any fields marked with {@link JsonIgnore}
     */
    public static void renderSafeJson(Object object) {
        throw new RenderJson(toSafeJson(object));
    }

    public static String toPrettyJson(Object object) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(object);
    }

    /**
     * Renders a Controller response as JSON, ignoring any fields marked with {@link JsonIgnore}
     */
    public static void renderPrettyJson(Object object) {
        throw new RenderJson(toPrettyJson(object));
    }


    /**
	 * Signifies that the specified field should NOT be included during Json Serialization 
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
	public @interface JsonIgnore {
	}
	
    private static class IgnoreExclusionStrategy implements ExclusionStrategy {
		@Override
		public boolean shouldSkipClass(Class<?> classAttrs) {
			return false;
		}

		@Override
		public boolean shouldSkipField(FieldAttributes fieldAttrs) {
			return fieldAttrs.getAnnotation(JsonIgnore.class) != null;
		}
    }
}
