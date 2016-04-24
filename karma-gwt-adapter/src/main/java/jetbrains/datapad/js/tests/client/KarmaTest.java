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

import com.google.gwt.core.client.JavaScriptObject;

public abstract class KarmaTest {

  KarmaTest(String name) {
    this(name, null);
  }

  <T extends Exception> KarmaTest(String name, Class<T> expectedException) {
    myName = name;
    myExpectedException = expectedException;
  }

  protected abstract void run() throws Throwable;

  private String myName;
  private Class myExpectedException;
  private boolean myResultAsync = false;
  private JavaScriptObject myAcceptFunction;
  private JavaScriptObject myRejectFunction;

  public void succeed() {
    if (myExpectedException == null) {
      exec(myAcceptFunction, null);
    } else {
      exec(myRejectFunction, new Throwable("expected exception, but did not see one"));
    }
  }

  public void fail(Throwable failure) {
    if (myExpectedException == null) {
      KarmaTestSuiteRunner.printStackTrace(failure);
      exec(myRejectFunction, failure);
    } else {
      if (myExpectedException.equals(failure.getClass())) {
        exec(myAcceptFunction, null);
      } else {
        exec(myRejectFunction, new Throwable("received wrong type of exception: " + failure.getClass()));
      }
    }
  }

  public void fail(String message) {
    fail(new Throwable(message));
  }

  void makeAsync() {
    myResultAsync = true;
  }

  private static native void exec(JavaScriptObject functionObject, Object item) /*-{
    if (functionObject && typeof functionObject === "function") {
      functionObject(item);
    } else {
      throw new Error("functionObject is not a function");
    }
  }-*/;

  private void dispatch(KarmaTestSuiteRunner testSuiteRunner, JavaScriptObject accept, JavaScriptObject reject) {
    try {
      testSuiteRunner.setRunningTest(this);
      myAcceptFunction = accept;
      myRejectFunction = reject;
      run();
      if (!myResultAsync) {
        succeed();
      }
    } catch (Throwable exception) {
      fail(exception);
    }
  }

  JavaScriptObject connect(KarmaTestSuiteRunner testSuiteRunner) {
    return connect(testSuiteRunner, myName);
  }

  private native JavaScriptObject connect(KarmaTestSuiteRunner testSuiteRunner, String testName) /*-{
    var $this = this,
        test = function () {
          // so that instanceof opeartor works
          var Promise = $wnd.getPromiseFactory();
          return new Promise(function(accept, reject) {
            // any exceptions should and will propagate to Promise
            $this.@jetbrains.datapad.js.tests.client.KarmaTest::dispatch(*)(testSuiteRunner, accept, reject);
          });
        };

    test.testName = testName;
    return test;
  }-*/;

}
