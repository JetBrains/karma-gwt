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

package jetbrains.datapad.js.tests.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;

import java.util.List;

public class KarmaTestSuiteRunner {

  private String myTestModule;
  private KarmaTestSuite[] myTestSuites;

  private KarmaTest myCurrentlyRunningTest;

  public static void run(KarmaTestSuite... testSuites) {
    new KarmaTestSuiteRunner("ANONYMOUS", testSuites).run();
  }

  public static void run(String testModule, KarmaTestSuite... testSuites) {
    new KarmaTestSuiteRunner(testModule, testSuites).run();
  }

  private KarmaTestSuiteRunner(String testModule, KarmaTestSuite... testSuites) {
    myTestModule = testModule;
    myTestSuites = testSuites;
  }

  public void run() {
    run(this, myTestSuites);
  }

  private JavaScriptObject[] getTests(final KarmaTestSuite testSuite) {
    List<KarmaTest> karmaTests = testSuite.registerTests();
    JavaScriptObject[] tests = new JavaScriptObject[karmaTests.size()];
    for (int i = 0; i < tests.length; i ++) {
      tests[i] = karmaTests.get(i).connect(this);
    }
    return tests;
  }

  private JavaScriptObject getAfterEach(final KarmaTestSuite testSuite) {
    return new KarmaTest("after:each") {
      @Override
      protected void run() {
        testSuite.after(this);
      }
    }.connect(KarmaTestSuiteRunner.this);
  }

  private JavaScriptObject getAfterAll(final KarmaTestSuite testSuite) {
    return new KarmaTest("after:all") {
      @Override
      protected void run() {
        testSuite.afterAll(this);
      }
    }.connect(KarmaTestSuiteRunner.this);
  }

  private JavaScriptObject getBeforeEach(final KarmaTestSuite testSuite) {
    return new KarmaTest("before:each") {
      @Override
      protected void run() {
        testSuite.before(this);
      }
    }.connect(KarmaTestSuiteRunner.this);
  }

  private JavaScriptObject getBeforeAll(final KarmaTestSuite testSuite) {
    return new KarmaTest("before:all") {
      @Override
      protected void run() {
        testSuite.beforeAll(this);
      }
    }.connect(KarmaTestSuiteRunner.this);
  }

  private void setupErrorHandler() {
    GWT.setUncaughtExceptionHandler(new GWT.UncaughtExceptionHandler() {
      @Override
      public void onUncaughtException(Throwable throwable) {
        myCurrentlyRunningTest.fail(throwable);
      }
    });
  }


  public static native void log(String text) /*-{
    $wnd.simpleLog(text);
  }-*/;


  public static native void error(String text) /*-{
    $wnd.logError(text);
  }-*/;

  static void printStackTrace(Throwable event) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(event).append("\n");
    for (StackTraceElement stackTraceElement : event.getStackTrace()) {
      stringBuilder.append("        at ").append(stackTraceElement).append("\n");
    }
    error(stringBuilder.toString());
  }

  private native void run(KarmaTestSuiteRunner runner, KarmaTestSuite[] testSuites) /*-{

    function validate(f, message) {
      if (!f) {
        throw new Error(message);
      }
    }

    validate($wnd.getPromiseFactory, "getPromiseFactory is not defined");
    validate($wnd.__run_tests, "__run_tests must be defined");

    this.@jetbrains.datapad.js.tests.client.KarmaTestSuiteRunner::setupErrorHandler()();

    var map = [], testSuite;
    for (var i = 0; i < testSuites.length; i++) {
      testSuite = testSuites[i];
      map[i] = {
        name: testSuite.@jetbrains.datapad.js.tests.client.KarmaTestSuite::getName()(),
        beforeAll: runner.@jetbrains.datapad.js.tests.client.KarmaTestSuiteRunner::getBeforeAll(*)(testSuite),
        before: runner.@jetbrains.datapad.js.tests.client.KarmaTestSuiteRunner::getBeforeEach(*)(testSuite),
        afterAll: runner.@jetbrains.datapad.js.tests.client.KarmaTestSuiteRunner::getBeforeAll(*)(testSuite),
        after: runner.@jetbrains.datapad.js.tests.client.KarmaTestSuiteRunner::getAfterEach(*)(testSuite),
        tests: runner.@jetbrains.datapad.js.tests.client.KarmaTestSuiteRunner::getTests(*)(testSuite)
      };
    }
    $wnd.__run_tests(map);

  }-*/;

  void setRunningTest(KarmaTest runningTest) {
    myCurrentlyRunningTest = runningTest;
  }

}
