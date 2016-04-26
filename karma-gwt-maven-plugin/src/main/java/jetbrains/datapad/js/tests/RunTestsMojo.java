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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


@Mojo(name = "run-tests", defaultPhase = LifecyclePhase.INTEGRATION_TEST)
public class RunTestsMojo extends AbstractMojo {

  private enum Resource {
    LIB("lib"), ADAPTER("karmaGWT", "karma-gwt");

    private final String myResourceName;
    private final String myInstallName;

    Resource(String resourceName) {
      this(resourceName, resourceName);
    }

    Resource(String resourceName, String installName) {
      myResourceName = resourceName;
      myInstallName = installName;
    }
  }

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

  @Parameter(property="testModules")
  private List<String> testModules;

  @Parameter(property="configPath")
  private File configPath;

  @Parameter(property="basePath")
  private String basePath;

  @Parameter(property="karmaBin")
  private File karma;

  private Path karmaSetupPath;

  private Path myKarmaConfig;

  private String myTestModules;

  public void execute()
      throws MojoExecutionException {

    initVars();
    runAction(this::setupKarma, "failed to install Karma");
    runAction(this::runKarma, "failed at karma");

  }

  private void initVars() throws MojoExecutionException {
    if (configPath == null && (testModules == null || testModules.isEmpty())) {
      throw new MojoExecutionException("either provide configurations path or testModules");
    }
    if (basePath == null) {
      if (testRunner != null) {
        basePath = outputDirectory.toPath().resolve(testRunner).toString();
      } else {
        basePath = outputDirectory.toPath().resolve(projectArtifactId + "-" + projectVersion).toString();
      }
    }
    if (karma == null) {
      karmaSetupPath = outputDirectory.toPath().getParent();
      karma = karmaSetupPath.toFile();
    } else {
      karmaSetupPath = karma.toPath();
    }
    myKarmaConfig = karmaSetupPath.resolve("karma.conf.js");
    myTestModules = testModules.stream().map(testModule -> "'" + testModule + "'").collect(Collectors.joining(","));
    validatePath(Paths.get(basePath).toAbsolutePath(), "basePath '" + basePath + "' doesn't exist");
    validatePath(karmaSetupPath.toAbsolutePath(), "karmaSetupPath '" + karmaSetupPath + "' doesn't exist");
  }

  private void validatePath(Path path, String errorMessage) throws MojoExecutionException {
    if (!path.toFile().exists()) {
      throw new MojoExecutionException(errorMessage);
    }
  }

  private boolean setupKarma() throws URISyntaxException, IOException, InterruptedException {
    if (configPath == null) {
      URI libs = this.getClass().getResource(Resource.LIB.myResourceName).toURI();
      processResources(libs, fs -> fs.provider().getPath(libs), resource -> {
        try (InputStreamReader inputStreamReader = new InputStreamReader(Files.newInputStream(resource));
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
          List<String> lines = new ArrayList<>();
          for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
            lines.add(line);
          }
          String rFileName = resource.getFileName().toString();
          Files.write(karmaSetupPath.resolve(rFileName), lines.stream().map(s -> BASE_PATH.matcher(updateTestModules(s)).replaceAll(basePath)).collect(Collectors.toList()),
              Charset.defaultCharset());
        }
      });
    } else {
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(configPath.toPath())) {
        for (Path p : ds) {
          Files.copy(p, karmaSetupPath.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
    return runProcess("npm", "install");
  }

  private String updateTestModules(String s) {
    return TEST_MODULE.matcher(s).replaceAll(myTestModules);
  }

  private boolean runProcess(String... command) throws IOException, InterruptedException {
    getLog().info(String.join(" ", Arrays.asList(command)));
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    Process installDepProcess = processBuilder.inheritIO().directory(karma).start();
    return installDepProcess.waitFor() == 0;
  }

  private boolean runKarma() throws URISyntaxException, IOException, InterruptedException {
    Path targetDirectory = karmaSetupPath.resolve(Paths.get("node_modules", Resource.ADAPTER.myInstallName));
    Path karma = karmaSetupPath.resolve(Paths.get("node_modules", ".bin", "karma"));
    Files.createDirectories(targetDirectory);
    URI resourceDirectory = this.getClass().getResource(Resource.ADAPTER.myResourceName).toURI();
    processResources(resourceDirectory, fs -> fs.provider().getPath(resourceDirectory),
        resource -> Files.copy(resource, targetDirectory.resolve(resource.getFileName().toString()), REPLACE_EXISTING));
    return runProcess(karma.toAbsolutePath().toString(), "start", myKarmaConfig.toAbsolutePath().toString());
  }

  private interface ResourceProcessor {
    void process(Path resource) throws IOException;
  }

  private interface ContainerProvider {
    Path getContainer(FileSystem fs) throws URISyntaxException, IOException;
  }

  private void processResources(URI contentUri, ContainerProvider containerProvider, ResourceProcessor processor) throws URISyntaxException, IOException {
    try (
        FileSystem fs = FileSystems.newFileSystem(contentUri, Collections.emptyMap())
    ) {
      Path containerPath = containerProvider.getContainer(fs);
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(containerPath)) {
        for (Path p : ds) {
          processor.process(p);
        }
      }
    }
  }

  private interface Action {
    boolean run() throws URISyntaxException, IOException, InterruptedException;
  }

  private void runAction(Action action, String errorMessage) throws MojoExecutionException {
    try {
      if (!action.run()) {
        throw new MojoExecutionException(errorMessage);
      }
    } catch (URISyntaxException | IOException | InterruptedException e) {
      e.printStackTrace();
      throw new MojoExecutionException(errorMessage);
    }
  }

}
