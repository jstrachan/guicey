/**
 * Copyright (C) 2008 Google Inc.
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
 */

package com.google.inject.spi;

import com.google.inject.Module;
import java.util.List;


/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ModuleWriterTest extends ElementsTest {

  protected void checkModule(Module module, Element.Visitor<?>... visitors) {
    // get some elements to apply
    List<Element> elements = Elements.getElements(module);

    // apply the recorded elements, and record them again!
    List<Element> rewrittenElements
        = Elements.getElements(new ModuleWriter().create(elements));

    // verify that the replayed elements are as expected
    assertEquals(rewrittenElements.size(), visitors.length);
    for (int i = 0; i < visitors.length; i++) {
      Element.Visitor<?> visitor = visitors[i];
      Element element = rewrittenElements.get(i);
      element.acceptVisitor(visitor);
    }
  }
}
