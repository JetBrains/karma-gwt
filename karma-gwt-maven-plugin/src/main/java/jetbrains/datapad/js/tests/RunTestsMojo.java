package jetbrains.datapad.js.tests;

/*
 * Copyright 2012-2016 JetBrains s.r.o
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Mojo(name = "run-tests", defaultPhase = LifecyclePhase.INTEGRATION_TEST)
public class RunTestsMojo extends AbstractMojo {

  private static final String LIB_DIR = "lib";
  private static final String ADAPTER_DIR = "karmaGWT";

  private static final Pattern BASE_PATH = Pattern.compile("%BASE_PATH%");
  private static final Pattern TEST_MODULE = Pattern.compile("'%TEST_MODULE%'");

  @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
  private File outputDirectory;

  @Parameter(defaultValue = "${project.artifactId}")
  private String projectArtifactId;

  @Parameter(defaultValue = "${project.version}")
  private String projectVersion;

  @Parameter(property="testRunner")
  private String testRunner;

  @Parameter(property="testModules", required = true)
  private List<String> testModules;

  private String myTestRunner;
  private Path myOutputPath;
  private String myTestModules;

  public void execute()
      throws MojoExecutionException {

    initVars();
    try {
      initKarmaRunner();
      initKarmaAdapter();
    } catch (URISyntaxException | IOException e) {
      e.printStackTrace();
      throw new MojoExecutionException("failed to prepare KarmaGWT");
    }

  }

  private void initVars() {
    if (testRunner != null) {
      myTestRunner = testRunner;
    } else {
      myTestRunner = projectArtifactId + "-" + projectVersion;
    }
    myOutputPath = outputDirectory.toPath();
    myTestModules = testModules.stream().map(testModule -> "'" + testModule + "'").collect(Collectors.joining(","));
  }

  private void initKarmaRunner() throws URISyntaxException, IOException {
    URI libs = this.getClass().getResource(LIB_DIR).toURI();
    processResources(libs, fs -> fs.provider().getPath(libs), resource -> {
      try (InputStreamReader inputStreamReader = new InputStreamReader(Files.newInputStream(resource));
           BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
        List<String> lines = new ArrayList<>();
        for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
          lines.add(line);
        }
        Files.write(
            myOutputPath.resolve(resource.getFileName().toString()),
            lines.stream().map(s ->
                BASE_PATH.matcher(TEST_MODULE.matcher(s).replaceAll(myTestModules)).replaceAll(myOutputPath.resolve(myTestRunner).toString())).
                collect(Collectors.toList()),
            Charset.defaultCharset(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
      }
    });
  }

  private void initKarmaAdapter() throws URISyntaxException, IOException {
    Path targetDirectory = outputDirectory.toPath().resolve(ADAPTER_DIR);
    URI resourceDirectory = this.getClass().getResource(ADAPTER_DIR).toURI();
    processResources(resourceDirectory, fs -> {
      Files.createDirectories(targetDirectory);
      return fs.provider().getPath(resourceDirectory);
    }, resource -> Files.copy(resource, targetDirectory.resolve(resource.getFileName().toString())));
  }

  private interface ResourceProcessor {
    void process(Path resource) throws IOException;
  }

  private interface ContainerProvider {
    Path getContainer(FileSystem fs) throws URISyntaxException, IOException;
  }

  private void processResources(URI contentUri, ContainerProvider containerProvider, ResourceProcessor processor) throws URISyntaxException, IOException {
    try (
        FileSystem fs = FileSystems.newFileSystem(contentUri, Collections.emptyMap());
    ) {
      Path containerPath = containerProvider.getContainer(fs);
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(containerPath)) {
        for (Path p : ds) {
          processor.process(p);
        }
      }
    }
  }

}
