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

import java.util.ArrayList;
import java.util.List;

public abstract class KarmaTestSuite {

  String getName() {
    return this.getClass().getSimpleName();
  }

  protected void beforeAll(KarmaTest karmaTest) {
  }

  protected void afterAll(KarmaTest karmaTest) {
  }

  protected void before(KarmaTest karmaTest) {
  }

  protected void after(KarmaTest karmaTest) {
  }

  List<KarmaTest> registerTests() {
    ArrayList<KarmaTest> tests = new ArrayList<>();
    registerTests(tests);
    return tests;
  }

  protected abstract void registerTests(ArrayList<KarmaTest> tests);
}
