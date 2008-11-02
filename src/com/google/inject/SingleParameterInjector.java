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

package com.google.inject;

import com.google.inject.spi.Dependency;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import java.util.List;

/**
 * Resolves a single parameter, to be used in a constructor or method invocation.
 */
class SingleParameterInjector<T> {
  private final Dependency<T> dependency;
  private final InternalFactory<? extends T> factory;

  SingleParameterInjector(Dependency<T> dependency, InternalFactory<? extends T> factory) {
    this.dependency = dependency;
    this.factory = factory;
  }

  private T inject(Errors errors, InternalContext context) throws ErrorsException {
    context.setDependency(dependency);
    try {
      return factory.get(errors.withSource(dependency), context, dependency);
    } finally {
      context.setDependency(null);
    }
  }

  /**
   * Returns an array of parameter values.
   */
  static Object[] getAll(Errors errors, InternalContext context,
      List<SingleParameterInjector<?>> parameterInjectors) throws ErrorsException {
    if (parameterInjectors == null) {
      return null;
    }

    int numErrorsBefore = errors.size();
    Object[] parameters = new Object[parameterInjectors.size()];

    int i = 0;
    for (SingleParameterInjector<?> parameterInjector : parameterInjectors) {
      try {
        parameters[i++] = parameterInjector.inject(errors, context);
      } catch (ErrorsException e) {
        errors.merge(e.getErrors());
      }
    }

    errors.throwIfNewErrors(numErrorsBefore);
    return parameters;
  }
}
