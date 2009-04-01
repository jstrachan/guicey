/**
 * Copyright (C) 2009 Google Inc.
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

import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.FailableCache;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.ImmutableSet;
import static com.google.inject.internal.Iterables.concat;
import com.google.inject.spi.InjectableType;
import com.google.inject.spi.InjectableTypeListenerBinding;
import com.google.inject.spi.InjectionPoint;
import java.util.List;

/**
 * Constructor injectors by type.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class ConstructorInjectorStore {
  private final InjectorImpl injector;
  private final ImmutableList<InjectableTypeListenerBinding> injectableTypeListenerBindings;

  private final FailableCache<TypeLiteral<?>, ConstructorInjector<?>>  cache
      = new FailableCache<TypeLiteral<?>, ConstructorInjector<?>> () {
    @SuppressWarnings("unchecked")
    protected ConstructorInjector<?> create(TypeLiteral<?> type, Errors errors)
        throws ErrorsException {
      return createConstructor(type, errors);
    }
  };

  ConstructorInjectorStore(InjectorImpl injector,
      List<InjectableTypeListenerBinding> injectableTypeListenerBindings) {
    this.injector = injector;
    this.injectableTypeListenerBindings = ImmutableList.copyOf(injectableTypeListenerBindings);
  }

  /**
   * Returns a new complete constructor injector with injection listeners registered.
   */
  @SuppressWarnings("unchecked") // the ConstructorInjector type always agrees with the passed type
  public <T> ConstructorInjector<T> get(TypeLiteral<T> key, Errors errors) throws ErrorsException {
    return (ConstructorInjector<T>) cache.get(key, errors);
  }

  private <T> ConstructorInjector<T> createConstructor(TypeLiteral<T> type, Errors errors)
      throws ErrorsException {
    int numErrorsBefore = errors.size();

    InjectionPoint injectionPoint;
    try {
      injectionPoint = InjectionPoint.forConstructorOf(type);
    } catch (ConfigurationException e) {
      errors.merge(e.getErrorMessages());
      throw errors.toException();
    }

    ImmutableList<SingleParameterInjector<?>> constructorParameterInjectors
        = injector.getParametersInjectors(injectionPoint.getDependencies(), errors);
    MembersInjectorImpl<T> membersInjector
        = injector.membersInjectorStore.createWithoutListeners(type, errors);

    ImmutableSet<InjectionPoint> injectableMembers = membersInjector.getInjectionPoints();

    ProxyFactory<T> proxyFactory = new ProxyFactory<T>(injectionPoint, injector.methodAspects);
    EncounterImpl<T> encounter = new EncounterImpl<T>(errors, injector.lookups);
    InjectableType<T> injectableType = new InjectableType<T>(
        injectionPoint, type, injectableMembers, proxyFactory.getInterceptors());

    for (InjectableTypeListenerBinding typeListener : injectableTypeListenerBindings) {
      if (typeListener.getTypeMatcher().matches(type)) {
        try {
          typeListener.getListener().hear(injectableType, encounter);
        } catch (RuntimeException e) {
          errors.errorNotifyingTypeListener(typeListener, injectableType, e);
        }
      }
    }
    encounter.invalidate();

    // rebuild the proxy factory and injectable type if new interceptors were added
    if (encounter.hasAddedAspects()) {
      proxyFactory = new ProxyFactory<T>(
          injectionPoint, concat(injector.methodAspects, encounter.getAspects()));
      injectableType = new InjectableType<T>(
          injectionPoint, type, injectableMembers, proxyFactory.getInterceptors());
    }

    errors.throwIfNewErrors(numErrorsBefore);

    return new ConstructorInjector<T>(proxyFactory.create(), constructorParameterInjectors,
        membersInjector, encounter.getInjectionListeners(), injectableType);
  }
}
