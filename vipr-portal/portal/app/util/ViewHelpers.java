/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import play.Play;
import play.data.validation.Error;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViewHelpers {
    public static String renderArgToJSON(String argName) {
        Object modelValue = play.mvc.Scope.RenderArgs.current().get(argName);
        return toJson(modelValue);
    }

    public static String buildModelInitializer(String modelName) {
        return modelName + " = " + renderArgToJSON(modelName);
    }

    public static String buildModelInitializer(List<String> modelNames) {
        return Joiner.on(";").join(Lists.transform(modelNames, new Function<String, String>() {
            public String apply(String input) {
                return buildModelInitializer(input);
            }
        }));
    }

    private static ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String errorsToJson(List<Error> playErrors) {
        Map<String, List<String>> errors = new HashMap<String, List<String>>();
        for (Error error : playErrors) {
            if (!errors.containsKey(error.getKey())) {
                errors.put(error.getKey(), new ArrayList<String>());
            }
            errors.get(error.getKey()).add(error.message());
        }
        return toJson(errors);
    }

    public static Map<String,String> globDirectory(String path) throws IOException {
        Map<String, String> allFiles = new HashMap<String, String>();

        File baseDir = Play.getVirtualFile(path).getRealFile();
        Collection<File> files = FileUtils.listFiles(baseDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);

        int basePathLength = baseDir.getAbsolutePath().length() - path.length();
        for (File file : files) {
            allFiles.put(file.getAbsolutePath().substring(basePathLength), FileUtils.readFileToString(file));
        }
        return allFiles;
    }
}
