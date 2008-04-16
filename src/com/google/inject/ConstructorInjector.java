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

import com.google.inject.internal.ConstructionProxy;
import com.google.inject.internal.ErrorMessages;

import java.lang.reflect.InvocationTargetException;

/**
 * Injects constructors.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ConstructorInjector<T> {

  final Class<T> implementation;
  final InjectorImpl.SingleMemberInjector[] memberInjectors;
  final InjectorImpl.SingleParameterInjector<?>[] parameterInjectors;
  final ConstructionProxy<T> constructionProxy;

  ConstructorInjector(InjectorImpl injector, Class<T> implementation) {
    this.implementation = implementation;
    constructionProxy = injector.reflection.getConstructionProxy(implementation);
    parameterInjectors = createParameterInjector(injector, constructionProxy);
    memberInjectors = injector.injectors.get(implementation)
        .toArray(new InjectorImpl.SingleMemberInjector[0]);
  }

  /**
   * Used to create an invalid injector.
   */
  private ConstructorInjector() {
    implementation = null;
    memberInjectors = null;
    parameterInjectors = null;
    constructionProxy = null;
  }

  InjectorImpl.SingleParameterInjector<?>[] createParameterInjector(
      InjectorImpl injector, ConstructionProxy<T> constructionProxy) {
    try {
      return constructionProxy.getParameters().isEmpty()
          ? null // default constructor.
          : injector.getParametersInjectors(
              constructionProxy.getMember(),
              constructionProxy.getParameters());
    }
    catch (InjectorImpl.MissingDependencyException e) {
      e.handle(injector.errorHandler);
      return null;
    }
  }

  /**
   * Construct an instance. Returns {@code Object} instead of {@code T} because
   * it may return a proxy.
   */
  Object construct(InternalContext context, Class<?> expectedType) {
    ConstructionContext<T> constructionContext
        = context.getConstructionContext(this);

    // We have a circular reference between constructors. Return a proxy.
    if (constructionContext.isConstructing()) {
      // TODO (crazybob): if we can't proxy this object, can we proxy the
      // other object?
      return constructionContext.createProxy(expectedType);
    }

    // If we're re-entering this factory while injecting fields or methods,
    // return the same instance. This prevents infinite loops.
    T t = constructionContext.getCurrentReference();
    if (t != null) {
      return t;
    }

    try {
      // First time through...
      constructionContext.startConstruction();
      try {
        Object[] parameters
            = InjectorImpl.getParameters(context, parameterInjectors);
        t = constructionProxy.newInstance(parameters);
        constructionContext.setProxyDelegates(t);
      }
      finally {
        constructionContext.finishConstruction();
      }

      // Store reference. If an injector re-enters this factory, they'll
      // get the same reference.
      constructionContext.setCurrentReference(t);

      // Inject fields and methods.
      for (InjectorImpl.SingleMemberInjector injector : memberInjectors) {
        injector.inject(context, t);
      }

      return t;
    }
    catch (InvocationTargetException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      throw new ProvisionException(cause,
          ErrorMessages.ERROR_INJECTING_CONSTRUCTOR);
    }
    finally {
      constructionContext.removeCurrentReference();
    }
  }

  /**
   * Returns an invalid constructor. This enables us to keep running and
   * reporting legitimate errors.
   */
  static <T> ConstructorInjector<T> invalidConstructor() {
    return new ConstructorInjector<T>() {
      Object construct(InternalContext context, Class<?> expectedType) {
        throw new UnsupportedOperationException();
      }
      public T get() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
