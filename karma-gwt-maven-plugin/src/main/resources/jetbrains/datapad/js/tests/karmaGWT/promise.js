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

+function utils() {

  Promise.ids = 0;

  Promise.each = function (list, it) {
    if (!it) it = translate;
    return new Promise(function(accept, reject) {
      +function listProcessor(index) {
        if (list[index]) {
          var result = it(list[index]);
          if (result instanceof Promise) {
            result.then(function(){ listProcessor(index+1); }).catch(reject);
          } else {
            listProcessor(index+1);
          }
        } else {
          accept();
        }
      }(0);
    });
  };

  Promise.all = function (list, it) {
    if (!it) it = translate;
    return new Promise(function(accept, reject) {
      +function listProcessor(index, prevError) {
        if (list[index]) {
          var next = function(newError){ listProcessor(index+1, prevError || newError); };
          var newError = null;
          var result;
          try {
            result = it(list[index]);
            if (result instanceof Promise) {
              result.then(next).catch(next);
            }
          } catch(e) {
            newError = e;
          } finally {
            if (!result || !(result instanceof Promise)) {
              listProcessor(index+1, prevError || newError);
            }
          }
        } else if (!prevError) {
          accept();
        } else {
          reject(prevError);
        }
      }(0);
    });
  };


  function translate(arg) {
    if (arg instanceof Promise) {
      return arg;
    }
    if (typeof arg === "function") {
      return arg();
    }
    return arg;
  }

}();


function Promise(executor, options) {

  this._acceptHandlers = [];
  this._rejectHandlers = [];

  this.state = null;
  this.value = null;

  options = options || {};

  this.id = options.id || Promise.ids++;

  function newPromise(onParentAccepted, onParentRejected, parent, options) {

    if (!onParentAccepted) {
      onParentAccepted = function() { return parent.value; };
    }

    var propagedError = false;
    if (!onParentRejected) {
      onParentRejected = function (error) {
        if (error) {
          throw error;
        } else {
          throw parent.value;
        }
      };
      propagedError = true;
    }

    return new Promise(function executor(accept, reject) {

      function handle(process, value) {
        value = value || parent.value;

        function delegate_to(f) {
          return function (v) {
            f(v);
          }
        }

        try {
          var result = process(value);
          if (result instanceof Promise) {
            result.then(delegate_to(accept), delegate_to(reject));
          } else {
            accept(result);
          }
        } catch (error) {
          if (!propagedError) {
            console.error(error.stack);
          }
          reject(error);
        }

      }

      if (parent.state == 'rejected') {
        handle(onParentRejected);
      } else if (parent.state == 'fulfilled') {
        handle(onParentAccepted);
      } else {

        var timerId, cancelled;

        function _handle(callback) {
          if (!cancelled) {
            if (timerId)  clearTimeout(timerId);
            handle(callback);
          }
        }

        parent._acceptHandlers.push(function pendingAcceptHandler() {
          _handle(onParentAccepted);
        });
        parent._rejectHandlers.push(function pendingRejectHandler() {
          _handle(onParentRejected);
        });

        if (options.timeout) {
          timerId = setTimeout(function () {
            cancelled = true;
            handle(onParentRejected, new Error("parent promise timeout"));
          }, options.timeout);
        }

      }

    }, options);

  }

  this.after = function (onFulfilled) {
    return newPromise(onFulfilled, onFulfilled, this, Object.create(options, {
      id : {value: this.id + ".after" }
    }));
  };
  
  this.then = function (onFulfilled, onRejected) {
    return newPromise(onFulfilled, onRejected, this, Object.create(options, {
      id : {value: this.id + ".then" }
    }));
  };

  this.then_after_max = function (time, onFulfilled, onRejected) {
    return newPromise(onFulfilled, onRejected, this, Object.create(options, {
      id : {value: this.id + ".thenAfter" },
      timeout : {value: time}
    }))
  };

  this.catch = function (onRejected) {
    if (!onRejected) {
      onRejected = function (error) {
        // basically just log the error, but don't fail the chain
        console.error(error.stack ? error.stack : error);
      };
    }
    return newPromise(null, onRejected, this, Object.create(options, {
      id : {value: this.id + ".catch" }
    }));
  };

  function resolve(promise, state, listeners) {
    return function promiseHandler(value) {
      if (!promise.state) {
        promise.value = value;
        promise.state = state;
        listeners = listeners.slice(0);
        for (var i = 0; i < listeners.length; i++) {
          listeners[i]();
        }
      } else {
        console.error("promise " + promise.id + " was previously " + promise.state, new Error().stack);
      }
    }
  }

  var acceptHandler = resolve(this, 'fulfilled', this._acceptHandlers);
  var rejectHandler = resolve(this, 'rejected', this._rejectHandlers);

  try {
    executor(acceptHandler, rejectHandler);
  } catch (error) {
    console.error(error.stack);
    rejectHandler(error);
  }

  return this;
}

