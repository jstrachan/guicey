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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.PrivateModule;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.AnnotatedElementBuilder;
import com.google.inject.internal.AbstractBindingBuilder;
import com.google.inject.internal.BindingBuilder;
import com.google.inject.internal.ConstantBindingBuilderImpl;
import com.google.inject.internal.Errors;
import com.google.inject.internal.PrivateElementsImpl;
import com.google.inject.internal.ProviderMethodsModule;
import com.google.inject.internal.SourceProvider;
import com.google.inject.matcher.Matcher;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Exposes elements of a module so they can be inspected, validated or {@link ModuleWriter 
 * rewritten}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class Elements {
  private static final BindingTargetVisitor<Object, Object> GET_INSTANCE_VISITOR
      = new DefaultBindingTargetVisitor<Object, Object>() {
    @Override public Object visitInstance(InstanceBinding<?> binding) {
      return binding.getInstance();
    }

    @Override protected Object visitOther(Binding<?> binding) {
      throw new IllegalArgumentException();
    }
  };

  /**
   * Records the elements executed by {@code modules}.
   */
  public static List<Element> getElements(Module... modules) {
    return getElements(Stage.DEVELOPMENT, Arrays.asList(modules));
  }

  /**
   * Records the elements executed by {@code modules}.
   */
  public static List<Element> getElements(Stage stage, Module... modules) {
    return getElements(stage, Arrays.asList(modules));
  }

  /**
   * Records the elements executed by {@code modules}.
   */
  public static List<Element> getElements(Iterable<? extends Module> modules) {
    return getElements(Stage.DEVELOPMENT, modules);
  }

  /**
   * Records the elements executed by {@code modules}.
   */
  public static List<Element> getElements(Stage stage, Iterable<? extends Module> modules) {
    RecordingBinder binder = new RecordingBinder(stage);
    for (Module module : modules) {
      binder.install(module);
    }
    return Collections.unmodifiableList(binder.elements);
  }

  @SuppressWarnings("unchecked")
  static <T> BindingTargetVisitor<T, T> getInstanceVisitor() {
    return (BindingTargetVisitor<T, T>) GET_INSTANCE_VISITOR;
  }

  private static class RecordingBinder implements Binder, PrivateBinder {
    private final Stage stage;
    private final Set<Module> modules;
    private final List<Element> elements;
    private final Object source;
    private final SourceProvider sourceProvider;

    /** The binder where exposed bindings will be created */
    private final RecordingBinder parent;
    private final PrivateElementsImpl privateElements;

    private RecordingBinder(Stage stage) {
      this.stage = stage;
      this.modules = Sets.newHashSet();
      this.elements = Lists.newArrayList();
      this.source = null;
      this.sourceProvider = new SourceProvider().plusSkippedClasses(
          Elements.class, RecordingBinder.class, AbstractModule.class,
          ConstantBindingBuilderImpl.class, AbstractBindingBuilder.class, BindingBuilder.class);
      this.parent = null;
      this.privateElements = null;
    }

    /** Creates a recording binder that's backed by {@code prototype}. */
    private RecordingBinder(
        RecordingBinder prototype, Object source, SourceProvider sourceProvider) {
      checkArgument(source == null ^ sourceProvider == null);

      this.stage = prototype.stage;
      this.modules = prototype.modules;
      this.elements = prototype.elements;
      this.source = source;
      this.sourceProvider = sourceProvider;
      this.parent = prototype.parent;
      this.privateElements = prototype.privateElements;
    }

    /** Creates a private recording binder. */
    private RecordingBinder(RecordingBinder parent, PrivateElementsImpl privateElements) {
      this.stage = parent.stage;
      this.modules = Sets.newHashSet();
      this.elements = privateElements.getElementsMutable();
      this.source = parent.source;
      this.sourceProvider = parent.sourceProvider;
      this.parent = parent;
      this.privateElements = privateElements;
    }

    /*if[AOP]*/
    public void bindInterceptor(
        Matcher<? super Class<?>> classMatcher,
        Matcher<? super Method> methodMatcher,
        MethodInterceptor... interceptors) {
      elements.add(new InterceptorBinding(getSource(), classMatcher, methodMatcher, interceptors));
    }
    /*end[AOP]*/

    public void bindScope(Class<? extends Annotation> annotationType, Scope scope) {
      elements.add(new ScopeBinding(getSource(), annotationType, scope));
    }

    public void requestInjection(Object... instances) {
      for (Object instance : instances) {
        elements.add(new InjectionRequest(getSource(), instance));
      }
    }

    public void requestStaticInjection(Class<?>... types) {
      for (Class<?> type : types) {
        elements.add(new StaticInjectionRequest(getSource(), type));
      }
    }

    public void install(Module module) {
      if (modules.add(module)) {
        Binder binder = this;
        if (module instanceof PrivateModule) {
          binder = binder.newPrivateBinder();
        }

        try {
          module.configure(binder);
        } catch (RuntimeException e) {
          Collection<Message> messages = Errors.getMessagesFromThrowable(e);
          if (!messages.isEmpty()) {
            elements.addAll(messages);
          } else {
            addError(e);
          }
        }
        binder.install(ProviderMethodsModule.forModule(module));
      }
    }

    public Stage currentStage() {
      return stage;
    }

    public void addError(String message, Object... arguments) {
      elements.add(new Message(getSource(), Errors.format(message, arguments)));
    }

    public void addError(Throwable t) {
      String message = "An exception was caught and reported. Message: " + t.getMessage();
      elements.add(new Message(ImmutableList.of(getSource()), message, t));
    }

    public void addError(Message message) {
      elements.add(message);
    }

    public <T> AnnotatedBindingBuilder<T> bind(Key<T> key) {
      return new BindingBuilder<T>(this, elements, getSource(), key);
    }

    public <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
      return bind(Key.get(typeLiteral));
    }

    public <T> AnnotatedBindingBuilder<T> bind(Class<T> type) {
      return bind(Key.get(type));
    }

    public AnnotatedConstantBindingBuilder bindConstant() {
      return new ConstantBindingBuilderImpl<Void>(this, elements, getSource());
    }

    public <T> Provider<T> getProvider(final Key<T> key) {
      final ProviderLookup<T> command = new ProviderLookup<T>(getSource(), key);
      elements.add(command);
      return new Provider<T>() {
        public T get() {
          Provider<T> delegate = command.getDelegate();
          checkState(delegate != null,
              "This provider cannot be used until the Injector has been created.");
          return delegate.get();
        }

        @Override public String toString() {
          return "Provider<" + key.getTypeLiteral() + ">";
        }
      };
    }

    public <T> Provider<T> getProvider(Class<T> type) {
      return getProvider(Key.get(type));
    }

    public void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher,
        TypeConverter converter) {
      elements.add(new TypeConverterBinding(getSource(), typeMatcher, converter));
    }

    public RecordingBinder withSource(final Object source) {
      return new RecordingBinder(this, source, null);
    }

    public RecordingBinder skipSources(Class... classesToSkip) {
      // if a source is specified explicitly, we don't need to skip sources
      if (source != null) {
        return this;
      }

      SourceProvider newSourceProvider = sourceProvider.plusSkippedClasses(classesToSkip);
      return new RecordingBinder(this, null, newSourceProvider);
    }

    public PrivateBinder newPrivateBinder() {
      PrivateElementsImpl privateElements = new PrivateElementsImpl(getSource());
      elements.add(privateElements);
      return new RecordingBinder(this, privateElements);
    }

    public void expose(Key<?> key) {
      exposeInternal(key);
    }

    public AnnotatedElementBuilder expose(Class<?> type) {
      return exposeInternal(Key.get(type));
    }

    public AnnotatedElementBuilder expose(TypeLiteral<?> type) {
      return exposeInternal(Key.get(type));
    }

    private <T> AnnotatedElementBuilder exposeInternal(Key<T> key) {
      if (privateElements == null) {
        addError("Cannot expose %s on a standard binder. "
            + "Exposed bindings are only applicable to private binders.", key);
        return new AnnotatedElementBuilder() {
          public void annotatedWith(Class<? extends Annotation> annotationType) {}
          public void annotatedWith(Annotation annotation) {}
        };
      }

      BindingBuilder<T> exposeBinding = new BindingBuilder<T>(
          this, parent.elements, getSource(), key);

      BindingBuilder.ExposureBuilder<T> builder = exposeBinding.usingKeyFrom(privateElements);
      privateElements.addExposureBuilder(builder);
      return builder;
    }

    protected Object getSource() {
      return sourceProvider != null
          ? sourceProvider.get()
          : source;
    }

    @Override public String toString() {
      return "Binder";
    }
  }
}
