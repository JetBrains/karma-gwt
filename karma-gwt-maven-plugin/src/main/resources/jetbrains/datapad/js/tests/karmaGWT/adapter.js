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

;(function (window) {

  function runtests(testSuite, karma, dryRun) {

    function report(testName) {
      console.log('running ' + testSuite.name + ':' + testName);
      var startTime = new Date().getTime();
      return function reportIt(error) {
        if (error && error.stack) {
          console.error(error.stack);
        }
        karma.result({
              id: '',
              description: testSuite.name + ':' + testName,
              suite: [],
              success: !error,
              skipped: false,
              time: new Date().getTime() - startTime,
              log: error ? [error.message || error.toString()] : []
            }
        );
      }
    }

    function runtests() {
      return Promise.all(testSuite.tests, function (test) {
        if (!dryRun) {
          return test().after(report(test.testName));
        } else {
          testCounter++;
        }
      });
    }

    if (dryRun) {
      testCounter += 2; //before and after steps
      return runtests();
    }

    function executeThenReport(step, stepName) {
      var reportIt = report(stepName);
      return step().then(reportIt).catch(reportIt);
    }

    function afterAll(error) {
      return executeThenReport(testSuite.afterAll, 'afterAll').then(function () {
        if (error) {
          throw error;
        }
      });
    }

    function beforeAll() {
      return executeThenReport(testSuite.beforeAll, 'beforeAll')
    }

    //-sasha-todo: fillin the rest of the befores/afters
    return beforeAll().then(runtests).after(afterAll);
  }

  function loadScript(url, container) {
    return new Promise(function (accept, reject) {
      var script = document.createElement('script');
      script.onerror = reject;
      script.onload = accept;
      script.src = url;
      container.appendChild(script);
    });
  }

  function testModuleRunner(config, karma, dryRun) {


    return function (moduleURI) {

      console.log((dryRun ? "loading test module : " : "running test module : ") + moduleURI);

      return new Promise(function (accept) {

        function destroyModule() {
          testsRunning = false;
          if (contextIframe != null) {
            document.body.removeChild(contextIframe);
            contextIframe = null;
          }
        }

        function failTesting(message) {
          return function () {
            destroyModule();
            karma.error(message);
          };
        }

        var contextIframe = document.createElement('iframe'), contentWindow;

        contextIframe.src = 'gwt-test-context.html';
        contextIframe.style.boxSizing = 'border-box';
        contextIframe.style.borderColor = "transparent";
        contextIframe.oneror = failTesting("failed loading context iframe");
        contextIframe.onload = function() {

          var bodyStyle = getComputedStyle(contextIframe.contentDocument.body);
          var iStyle = getComputedStyle(contextIframe);
          var borderWidth = getValue(iStyle, 'border-width') * 2;

          contextIframe.width = window.innerWidth - getBodyStyle('margin-left') - getBodyStyle('margin-right') - borderWidth + 'px';
          contextIframe.height = window.innerHeight - getBodyStyle('margin-bottom') - getBodyStyle('margin-top') - borderWidth + 'px';

          initContext();

          loadScript(moduleURI, contentWindow.document.body).then(function(){
            console.log("test runner loaded");
          }).catch(failTesting("failed loading script"));

          function getValue(style, attr) {
            return parseInt(style[attr]);
          }

          function getBodyStyle(attr) {
            return getValue(bodyStyle, attr)
          }

        };
        document.body.appendChild(contextIframe);

        function initContext() {

          console.log("initializing context");

          contentWindow = contextIframe.contentWindow;

          if (config.testingContext) {
            config.testingContext.forEach(function (str) {
              contentWindow.eval(str);
            })
          }

          contentWindow.addEventListener('beforeunload', function () {
            if (testsRunning) failTesting("rejecting the test because it reloaded the page")();
          }, true);

          contentWindow.logError = function (message) {
            console.error(message);
          };

          contentWindow.simpleLog = function (message) {
            console.log(message);
          };

          contentWindow.getPromiseFactory = function () {
            return Promise;
          };

          contentWindow.__run_tests = function (testSuites, testModule) {

            function _runTests() {
              testsRunning = true;
              return Promise.all(testSuites, function (testSuite) {
                console.log((dryRun ? "loading test suite : " : "running test suite : ") + testSuite.name);
                return runtests(testSuite, karma, dryRun);
              });
            }

            function _afterTests() {
              destroyModule();
              accept();
            }

            if (!testsRunning) {
              return _runTests().after(_afterTests);
            } else {
              failTesting("test module " + testModule + " cannot inherit another test module.")();
            }

          };

        }

      });
    };
  }

  var testCounter = 0, testsRunning;


  function createStartFn(karmaRunner) {
    return function () {

      function runModules(dryRun) {

        if (!dryRun) {
          karmaRunner.info({total: testCounter})
        }

        var allModulesRanPromised = Promise.all(karmaRunner.config.gwt.__testModules,
            testModuleRunner(karmaRunner.config.gwt, karmaRunner, dryRun));
        if (!dryRun) {
          return allModulesRanPromised.then(karmaRunner.complete, karmaRunner.error);
        } else {
          return allModulesRanPromised.catch(karmaRunner.error);
        }

      }

      runModules(true).then(runModules);
    };
  }

  window.__karma__.start = createStartFn(window.__karma__);

})(typeof window !== 'undefined' ? window : global);
