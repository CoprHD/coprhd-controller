/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.vipr.primitives;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.emc.apidocs.ApiDoclet;
import com.emc.apidocs.DocReporter;
import com.emc.apidocs.KnownPaths;
import com.emc.apidocs.model.ApiService;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.JavaFile;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.RootDoc;

/**
 * Doclet class that leverages the ApiDoclet to scan the ViPR API to create
 * primitives
 */
public class PrimitiveDoclet {

    private static final String CONTENT_OPTION = "-c";
    private static final String OUTPUT_OPTION = "-d";
    private static final String SOURCE_DIR = "src/main/generated";
    private static final String README = "src/conf/README";
    private static final String ReadMePreamble = 
            "This file contains the class names of all of the primitives\n" +
            "that were generated for vipr operations.\n" +
            "In order to add a vipr primitive to the whitelist of supported\n" +
            "primitives the class name should be added to the list in spring "+
            "config file: src/conf/sa-vipr-operations.xml\n";
    
    private static String outputDirectory;
    private static String contentDirectory;
    
    public static boolean start(RootDoc root) {
        KnownPaths.init(contentDirectory, outputDirectory);
        final List<ApiService> services = ApiDoclet.findApiServices(root.classes());
        final Path readMe = new File(outputDirectory + README).toPath();
        
        final Iterable<JavaFile> files = ApiPrimitiveMaker.makePrimitives(services);
        ImmutableList.Builder<String> lines = ImmutableList.<String>builder();
        lines.add(ReadMePreamble);
        for (final JavaFile file : files) {
            try {
                lines.add("<value>"+file.packageName+"."+file.typeSpec.name+"</value>");
                file.writeTo(new File(outputDirectory + SOURCE_DIR));
            } catch (IOException e) {
                throw new RuntimeException("Failed to write to output folder",
                        e);
            }
        }
        try {
            Files.createDirectories(readMe.getParent());
            Files.write(readMe, lines.build(), Charset.forName("UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write README",
                    e);
        }
        return true;
    }

    /** Required by Doclet, otherwise it does not process Generics correctly */
    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }

    /** Required by Doclet to check command line options */
    public static int optionLength(String option) {
        if (option.equals(OUTPUT_OPTION) || option.equals(CONTENT_OPTION)) {
            return 2;
        }
        
        return 1;
    }

    /** Required by Doclet to process the command line options */
    public static synchronized boolean validOptions(String options[][],
            DocErrorReporter reporter) {
        DocReporter.init(reporter);
        DocReporter.printWarning("Processing Options");
        boolean valid = true;
        boolean outputOptionFound = false;
        boolean contentOptionFound = false;

        // Make sure we have an OUTPUT option
        for (int i = 0; i < options.length; i++) {
            if (options[i][0].equals(OUTPUT_OPTION)) {
                outputOptionFound = true;
                valid = checkOutputOption(options[i][1], reporter);
            }
            else if (options[i][0].equals(CONTENT_OPTION)) {
                contentOptionFound = true;
                valid = checkContentOption(options[i][1], reporter);

            }
        }

        if (!outputOptionFound) {
            reporter.printError("Output dir option " + OUTPUT_OPTION
                    + " not specified");
        }
        if (!contentOptionFound) {
            reporter.printError("Content dir option " + CONTENT_OPTION
                    + " not specified");
        }

        DocReporter.printWarning("Finished Processing Options");

        return valid && outputOptionFound && contentOptionFound;
    }

    private static synchronized boolean checkContentOption(String contentDir, DocErrorReporter reporter) {
        File contentDirFile = new File(contentDir);
        if (!contentDirFile.exists()) {
            reporter.printError("Content directory (" + CONTENT_OPTION + ") not found :" + contentDirFile.getAbsolutePath());
            return false;
        }

        if (!contentDirFile.isDirectory()) {
            reporter.printError("Content directory (" + CONTENT_OPTION + ") is not a directory :" + contentDirFile.getAbsolutePath());
            return false;
        }
        contentDirectory = contentDir;
        if (!contentDirectory.endsWith("/")) {
            contentDirectory = contentDirectory + "/";
        }
        reporter.printWarning("Content Directory " + contentDirectory);

        return true;
    }
    
    private static synchronized boolean checkOutputOption(String value,
            DocErrorReporter reporter) {
        File file = new File(value);
        if (!file.exists()) {
            reporter.printError("Output directory (" + OUTPUT_OPTION
                    + ") not found :" + file.getAbsolutePath());
            return false;
        }

        if (!file.isDirectory()) {
            reporter.printError("Output directory (" + OUTPUT_OPTION
                    + ") is not a directory :" + file.getAbsolutePath());
            return false;
        }

        outputDirectory = value;
        if (!outputDirectory.endsWith("/")) {
            outputDirectory = outputDirectory + "/";
        }

        reporter.printWarning("Output Directory " + outputDirectory);

        return true;
    }
}
