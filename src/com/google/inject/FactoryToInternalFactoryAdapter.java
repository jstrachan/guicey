/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

/**
 * @author crazybob@google.com (Bob Lee)
*/
class FactoryToInternalFactoryAdapter<T> implements Factory<T> {

  private final ContainerImpl container;

  private final InternalFactory<? extends T> internalFactory;

  public FactoryToInternalFactoryAdapter(ContainerImpl container,
      InternalFactory<? extends T> internalFactory) {
    this.container = container;
    this.internalFactory = internalFactory;
  }

  public T get() {
    return container.callInContext(
        new ContainerImpl.ContextualCallable<T>() {
      public T call(InternalContext context) {
        return internalFactory.get(context);
      }
    });
  }

  public String toString() {
    return internalFactory.toString();
  }
}
