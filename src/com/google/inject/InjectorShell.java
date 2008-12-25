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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import static com.google.inject.Scopes.SINGLETON;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.InternalContext;
import com.google.inject.internal.InternalFactory;
import com.google.inject.internal.ProviderInstanceBindingImpl;
import com.google.inject.internal.Scoping;
import com.google.inject.internal.SourceProvider;
import com.google.inject.internal.Stopwatch;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.PrivateEnvironment;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A partially-initialized injector. See {@link InjectorBuilder}, which uses this to build a tree
 * of injectors in batch.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class InjectorShell {

  private final List<Element> elements;
  private final InjectorImpl injector;
  private final PrivateEnvironment privateEnvironment;

  private InjectorShell(Builder builder, List<Element> elements, InjectorImpl injector) {
    this.privateEnvironment = builder.privateEnvironment;
    this.elements = elements;
    this.injector = injector;
  }

  PrivateEnvironment getPrivateEnvironment() {
    return privateEnvironment;
  }

  InjectorImpl getInjector() {
    return injector;
  }

  List<Element> getElements() {
    return elements;
  }

  static class Builder {
    private final List<Element> elements = Lists.newArrayList();
    private final List<Module> modules = Lists.newArrayList();

    /** lazily constructed */
    private State state;

    private InjectorImpl parent;
    private Stage stage;

    /** null unless this exists in a {@link Binder#newPrivateBinder private environment} */
    private PrivateEnvironment privateEnvironment;

    Builder parent(InjectorImpl parent) {
      this.parent = parent;
      this.state = new InheritingState(parent.state);
      return this;
    }

    Builder stage(Stage stage) {
      this.stage = stage;
      return this;
    }

    Builder privateEnvironment(PrivateEnvironment privateEnvironment) {
      this.privateEnvironment = privateEnvironment;
      this.elements.addAll(privateEnvironment.getElements());
      return this;
    }

    void addModules(Iterable<? extends Module> modules) {
      for (Module module : modules) {
        this.modules.add(module);
      }
    }

    /** Synchronize on this before calling {@link #build}. */
    Object lock() {
      return getState().lock();
    }

    /**
     * Creates and returns the injector shells for the current modules. Multiple shells will be
     * returned if any modules contain {@link Binder#newPrivateBinder private environments}. The
     * primary injector will be first in the returned list.
     */
    List<InjectorShell> build(Initializer initializer, BindingProcessor bindingProcessor,
        Map<PrivateEnvironment, InjectorImpl> environmentToInjector,
        Stopwatch stopwatch, Errors errors) {
      checkState(stage != null, "Stage not initialized");
      checkState(privateEnvironment == null || parent != null, "PrivateEnvironment with no parent");
      checkState(state != null, "no state. Did you remember to lock() ?");

      InjectorImpl injector = new InjectorImpl(parent, state, initializer);
      if (privateEnvironment != null) {
        environmentToInjector.put(privateEnvironment, injector);
      }

      // bind Stage and Singleton if this is a top-level injector
      if (parent == null) {
        modules.add(0, new RootModule(stage));
        new TypeConverterBindingProcessor(errors).prepareBuiltInConverters(injector);
      }

      elements.addAll(Elements.getElements(stage, modules));
      stopwatch.resetAndLog("Module execution");

      new MessageProcessor(errors).process(injector, elements);

      InterceptorBindingProcessor interceptors = new InterceptorBindingProcessor(errors);
      interceptors.process(injector, elements);
      interceptors.setupProxyFactory(injector);
      stopwatch.resetAndLog("Interceptors creation");

      new ScopeBindingProcessor(errors).process(injector, elements);
      stopwatch.resetAndLog("Scopes creation");

      new TypeConverterBindingProcessor(errors).process(injector, elements);
      stopwatch.resetAndLog("Converters creation");

      bindInjector(injector);
      bindLogger(injector);
      bindingProcessor.process(injector, elements);
      stopwatch.resetAndLog("Binding creation");

      List<InjectorShell> injectorShells = Lists.newArrayList();
      injectorShells.add(new InjectorShell(this, elements, injector));

      // recursively build child shells
      PrivateEnvironmentProcessor processor = new PrivateEnvironmentProcessor(errors, stage);
      processor.process(injector, elements);
      for (Builder builder : processor.getInjectorShellBuilders()) {
        injectorShells.addAll(
            builder.build(initializer, bindingProcessor, environmentToInjector, stopwatch, errors));
      }
      stopwatch.resetAndLog("Private environment creation");

      return injectorShells;
    }

    private State getState() {
      if (state == null) {
        state = new InheritingState(State.NONE);
      }
      return state;
    }
  }

  /**
   * The Injector is a special case because we allow both parent and child injectors to both have
   * a binding for that key.
   */
  private static void bindInjector(InjectorImpl injector) {
    Key<Injector> key = Key.get(Injector.class);
    InjectorFactory injectorFactory = new InjectorFactory(injector);
    injector.state.putBinding(key,
        new ProviderInstanceBindingImpl<Injector>(injector, key, SourceProvider.UNKNOWN_SOURCE,
            injectorFactory, Scoping.UNSCOPED, injectorFactory,
            ImmutableSet.<InjectionPoint>of()));
  }

  private static class InjectorFactory implements InternalFactory<Injector>, Provider<Injector> {
    private final Injector injector;

    private InjectorFactory(Injector injector) {
      this.injector = injector;
    }

    public Injector get(Errors errors, InternalContext context, Dependency<?> dependency)
        throws ErrorsException {
      return injector;
    }

    public Injector get() {
      return injector;
    }

    public String toString() {
      return "Provider<Injector>";
    }
  }

  /**
   * The Logger is a special case because it knows the injection point of the injected member. It's
   * the only binding that does this.
   */
  private static void bindLogger(InjectorImpl injector) {
    Key<Logger> key = Key.get(Logger.class);
    LoggerFactory loggerFactory = new LoggerFactory();
    injector.state.putBinding(key,
        new ProviderInstanceBindingImpl<Logger>(injector, key,
            SourceProvider.UNKNOWN_SOURCE, loggerFactory, Scoping.UNSCOPED,
            loggerFactory, ImmutableSet.<InjectionPoint>of()));
  }

  private static class LoggerFactory implements InternalFactory<Logger>, Provider<Logger> {
    public Logger get(Errors errors, InternalContext context, Dependency<?> dependency) {
      InjectionPoint injectionPoint = dependency.getInjectionPoint();
      return injectionPoint == null
          ? Logger.getAnonymousLogger()
          : Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }

    public Logger get() {
      return Logger.getAnonymousLogger();
    }

    public String toString() {
      return "Provider<Logger>";
    }
  }

  private static class RootModule implements Module {
    final Stage stage;

    private RootModule(Stage stage) {
      this.stage = checkNotNull(stage, "stage");
    }

    public void configure(Binder binder) {
      binder = binder.withSource(SourceProvider.UNKNOWN_SOURCE);
      binder.bind(Stage.class).toInstance(stage);
      binder.bindScope(Singleton.class, SINGLETON);
    }
  }
}
