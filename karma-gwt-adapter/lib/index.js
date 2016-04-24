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


var path = require('path');
var fs = require('fs');

function initGwtRunner(emitter,
                       /* config.files */ files,
                       /* config.client.gwt */ gwt_config,
                       /* config.basePath */ basePath) {

  loadFile('/promise.js', true);
  loadFile('/adapter.js', true);

  function loadFile(path, included) {
    files.unshift({
      pattern: __dirname + path,
      included: included,
      served: true,
      watched: false
    });
  }

  emitter.on('file_list_modified', function (files) {

    files.served.push({
      path: '/gwt-test-context.html',
      contentPath: path.normalize(__dirname + '/../test-context.html'),
      isUrl: true,
      mtime: new Date()
    });

    gwt_config.__testModules = [];

    gwt_config.testModules.forEach(function loadTestFile(testFile) {

      if (testFile.startsWith('http:') || testFile.startsWith('https:')) {
        gwt_config.__testModules.push(testFile);
        return;
      }

      var dirpath = path.dirname(path.resolve(basePath, testFile));
      var dirname = path.basename(dirpath);

      gwt_config.__testModules.push('/' + path.join(dirname, path.basename(testFile)));

      fs.readdirSync(dirpath).forEach(function(fileName){
        var contentPath = path.join(dirpath, fileName);
        var fileUrl = '/' + path.join(dirname, fileName);
        if (fs.lstatSync(contentPath).isDirectory()) {
          throw "IllegalStateException: " + testFile + " is located in an unexpected location";
        } else {
          files.served.push({
            path: fileUrl,
            contentPath: contentPath,
            isUrl: true,
            mtime: new Date()
          });
        }
      });
    });

  });


}

module.exports = {
  'framework:gwt': ['factory', initGwtRunner]
};
