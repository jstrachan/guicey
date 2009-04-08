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
import com.google.inject.internal.Lists;
import com.google.inject.spi.InjectableTypeListenerBinding;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * Members injectors by type.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class MembersInjectorStore {
  private final InjectorImpl injector;
  private final ImmutableList<InjectableTypeListenerBinding> injectableTypeListenerBindings;

  private final FailableCache<TypeLiteral<?>, MembersInjectorImpl<?>> cache
      = new FailableCache<TypeLiteral<?>, MembersInjectorImpl<?>>() {
    @Override protected MembersInjectorImpl<?> create(TypeLiteral<?> type, Errors errors)
        throws ErrorsException {
      return createWithListeners(type, errors);
    }
  };

  MembersInjectorStore(InjectorImpl injector,
      List<InjectableTypeListenerBinding> injectableTypeListenerBindings) {
    this.injector = injector;
    this.injectableTypeListenerBindings = ImmutableList.copyOf(injectableTypeListenerBindings);
  }

  /**
   * Returns true if any type listeners are installed. Other code may take shortcuts when there
   * aren't any type listeners.
   */
  public boolean hasTypeListeners() {
    return !injectableTypeListenerBindings.isEmpty();
  }

  /**
   * Returns a new complete members injector with injection listeners registered.
   */
  @SuppressWarnings("unchecked") // the MembersInjector type always agrees with the passed type
  public <T> MembersInjectorImpl<T> get(TypeLiteral<T> key, Errors errors) throws ErrorsException {
    return (MembersInjectorImpl<T>) cache.get(key, errors);
  }

  /**
   * Creates a new members injector and attaches both injection listeners and method aspects.
   */
  private <T> MembersInjectorImpl<T> createWithListeners(TypeLiteral<T> type, Errors errors)
      throws ErrorsException {
    int numErrorsBefore = errors.size();

    Set<InjectionPoint> injectionPoints;
    try {
      injectionPoints = InjectionPoint.forInstanceMethodsAndFields(type);
    } catch (ConfigurationException e) {
      errors.merge(e.getErrorMessages());
      injectionPoints = e.getPartialValue();
    }
    ImmutableList<SingleMemberInjector> injectors = getInjectors(injectionPoints, errors);
    errors.throwIfNewErrors(numErrorsBefore);

    EncounterImpl<T> encounter = new EncounterImpl<T>(errors, injector.lookups);
    for (InjectableTypeListenerBinding typeListener : injectableTypeListenerBindings) {
      if (typeListener.getTypeMatcher().matches(type)) {
        try {
          typeListener.getListener().hear(type, encounter);
        } catch (RuntimeException e) {
          errors.errorNotifyingTypeListener(typeListener, type, e);
        }
      }
    }
    encounter.invalidate();
    errors.throwIfNewErrors(numErrorsBefore);

    return new MembersInjectorImpl<T>(injector, type, injectors, encounter.getInjectionListeners(),
        encounter.getAspects());
  }

  /**
   * Returns the injectors for the specified injection points.
   */
  ImmutableList<SingleMemberInjector> getInjectors(
      Set<InjectionPoint> injectionPoints, Errors errors) {
    List<SingleMemberInjector> injectors = Lists.newArrayList();
    for (InjectionPoint injectionPoint : injectionPoints) {
      try {
        Errors errorsForMember = injectionPoint.isOptional()
            ? new Errors(injectionPoint)
            : errors.withSource(injectionPoint);
        SingleMemberInjector injector = injectionPoint.getMember() instanceof Field
            ? new SingleFieldInjector(this.injector, injectionPoint, errorsForMember)
            : new SingleMethodInjector(this.injector, injectionPoint, errorsForMember);
        injectors.add(injector);
      } catch (ErrorsException ignoredForNow) {
        // ignored for now
      }
    }
    return ImmutableList.copyOf(injectors);
  }
}
