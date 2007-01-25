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

import java.util.Map;
import java.util.HashMap;

/**
 * Internal context. Used to coordinate injections and support circular
 * dependencies.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class InternalContext {

  final ContainerImpl container;
  Map<Object, ConstructionContext<?>> constructionContexts;
  ExternalContext<?> externalContext;

  InternalContext(ContainerImpl container) {
    this.container = container;
  }

  public Container getContainer() {
    return container;
  }

  ContainerImpl getContainerImpl() {
    return container;
  }

  @SuppressWarnings("unchecked")
  <T> ConstructionContext<T> getConstructionContext(Object key) {
    if (constructionContexts == null) {
      constructionContexts = new HashMap<Object, ConstructionContext<?>>();
      ConstructionContext<T> constructionContext = new ConstructionContext<T>();
      constructionContexts.put(key, constructionContext);
      return constructionContext;
    } else {
      ConstructionContext<T> constructionContext =
          (ConstructionContext<T>) constructionContexts.get(key);
      if (constructionContext == null) {
        constructionContext = new ConstructionContext<T>();
        constructionContexts.put(key, constructionContext);
      }
      return constructionContext;
    }
  }

  @SuppressWarnings("unchecked")
  <T> ExternalContext<T> getExternalContext() {
    return (ExternalContext<T>) externalContext;
  }

  void setExternalContext(ExternalContext<?> externalContext) {
    this.externalContext = externalContext;
  }
}
