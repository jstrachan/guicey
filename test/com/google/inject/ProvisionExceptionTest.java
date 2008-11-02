/**
 * Copyright (C) 2007 Google Inc.
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

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.assertSimilarWhenReserialized;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
@SuppressWarnings("UnusedDeclaration")
public class ProvisionExceptionTest extends TestCase {

  public void testExceptionsCollapsed() {
    try {
      Guice.createInjector().getInstance(A.class);
      fail(); 
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "Error injecting constructor",
          "for parameter 0 at com.google.inject.ProvisionExceptionTest$C.setD",
          "for field at com.google.inject.ProvisionExceptionTest$B.c",
          "for parameter 0 at com.google.inject.ProvisionExceptionTest$A");
    }
  }

  /**
   * There's a pass-through of user code in the scope. We want exceptions thrown by Guice to be
   * limited to a single exception, even if it passes through user code.
   */
  public void testExceptionsCollapsedWithScopes() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(B.class).in(Scopes.SINGLETON);
        }
      }).getInstance(A.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertFalse(e.getMessage().contains("custom provider"));
      assertContains(e.getMessage(), "Error injecting constructor",
          "for parameter 0 at com.google.inject.ProvisionExceptionTest$C.setD",
          "for field at com.google.inject.ProvisionExceptionTest$B.c",
          "for parameter 0 at com.google.inject.ProvisionExceptionTest$A");
    }
  }

  public void testMethodInjectionExceptions() {
    try {
      Guice.createInjector().getInstance(E.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "Error injecting method",
          "at " + E.class.getName() + ".setObject(ProvisionExceptionTest.java:");
    }
  }

  public void testBindToProviderInstanceExceptions() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(D.class).toProvider(new DProvider());
        }
      }).getInstance(D.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(),
          "1) Error in custom provider, java.lang.UnsupportedOperationException",
          "at " + ProvisionExceptionTest.class.getName(), ".configure(ProvisionExceptionTest.java");
    }
  }

  /**
   * This test demonstrates that if the user throws a ProvisionException, we wrap it to add context.
   */
  public void testProvisionExceptionsAreWrappedForBindToType() {
    try {
      Guice.createInjector().getInstance(F.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(), "1) User Exception",
          "at " + F.class.getName() + ".<init>(ProvisionExceptionTest.java:");
    }
  }

  public void testProvisionExceptionsAreWrappedForBindToProviderType() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(F.class).toProvider(FProvider.class);
        }
      }).getInstance(F.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(), "1) User Exception",
          "at binding for ", FProvider.class.getName(), ".class(ProvisionExceptionTest.java",
          "at binding for ", F.class.getName(), ".class(ProvisionExceptionTest.java:");
    }
  }

  public void testProvisionExceptionsAreWrappedForBindToProviderInstance() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(F.class).toProvider(new FProvider());
        }
      }).getInstance(F.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(), "1) User Exception",
          "at " + ProvisionExceptionTest.class.getName(), ".configure(ProvisionExceptionTest.java");
    }
  }

  public void testProvisionExceptionIsSerializable() throws IOException {
    try {
      Guice.createInjector().getInstance(A.class);
      fail();
    } catch (ProvisionException expected) {
      assertSimilarWhenReserialized(expected);
    }
  }

  public void testMultipleCauses() {
    try {
      Guice.createInjector().getInstance(G.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(),
          "1) Error injecting method, java.lang.IllegalArgumentException",
          "Caused by: java.lang.IllegalArgumentException: java.lang.UnsupportedOperationException",
          "Caused by: java.lang.UnsupportedOperationException: Unsupported",
          "2) Error injecting method, java.lang.NullPointerException: can't inject second either",
          "Caused by: java.lang.NullPointerException: can't inject second either",
          "2 errors");
    }
  }

  public void testInjectInnerClass() throws Exception {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(InnerClass.class);
      fail();
    } catch (Exception expected) {
      assertContains(expected.getMessage(),
          "Injecting into inner classes is not supported.",
          "at binding for " + InnerClass.class.getName() + ".class(ProvisionExceptionTest.java:");
    }
  }

  public void testInjectLocalClass() throws Exception {
    class LocalClass {}

    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(LocalClass.class);
      fail();
    } catch (Exception expected) {
      assertContains(expected.getMessage(),
          "Injecting into inner classes is not supported.",
          "at binding for " + LocalClass.class.getName() + ".class(ProvisionExceptionTest.java:");
    }
  }

  public void testBindingAnnotationsOnMethodsAndConstructors() {
    try {
      Guice.createInjector().getInstance(MethodWithBindingAnnotation.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), MethodWithBindingAnnotation.class.getName()
          + ".injectMe() is annotated with @", Green.class.getName() + "(), ",
          "but binding annotations should be applied to its parameters instead.",
          "at binding for " + MethodWithBindingAnnotation.class.getName() + ".class");
    }

    try {
      Guice.createInjector().getInstance(ConstructorWithBindingAnnotation.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), ConstructorWithBindingAnnotation.class.getName()
          + ".<init>() is annotated with @", Green.class.getName() + "(), ",
          "but binding annotations should be applied to its parameters instead.",
          "at " + ConstructorWithBindingAnnotation.class.getName() + ".class",
          "at binding for " + ConstructorWithBindingAnnotation.class.getName() + ".class");
    }
  }

  public void testLinkedBindings() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(D.class).to(RealD.class);
      }
    });

    try {
      injector.getInstance(D.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "at " + RealD.class.getName() + ".<init>(ProvisionExceptionTest.java:",
          "at binding for " + RealD.class.getName() + ".class(ProvisionExceptionTest.java:",
          "at binding for " + D.class.getName() + ".class(ProvisionExceptionTest.java:");
    }
  }

  public void testProviderKeyBindings() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(D.class).toProvider(DProvider.class);
      }
    });

    try {
      injector.getInstance(D.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "at binding for " + DProvider.class.getName() + ".class(ProvisionExceptionTest.java:",
          "at binding for " + D.class.getName() + ".class(ProvisionExceptionTest.java:");
    }
  }

  private class InnerClass {}

  static class A {
    @Inject
    A(B b) { }
  }
  static class B {
    @Inject C c;
  }
  static class C {
    @Inject
    void setD(RealD d) { }
  }
  static class E {
    @Inject void setObject(Object o) {
      throw new UnsupportedOperationException();
    }
  }

  static class MethodWithBindingAnnotation {
    @Inject @Green void injectMe(String greenString) {}
  }

  static class ConstructorWithBindingAnnotation {
    @Inject @Green ConstructorWithBindingAnnotation(String greenString) {}
  }

  @Retention(RUNTIME)
  @Target({ FIELD, PARAMETER, CONSTRUCTOR, METHOD })
  @BindingAnnotation
  @interface Green {}

  interface D {}

  static class RealD implements D {
    @Inject RealD() {
      throw new UnsupportedOperationException();
    }
  }

  static class DProvider implements Provider<D> {
    public D get() {
      throw new UnsupportedOperationException();
    }
  }

  static class F {
    @Inject public F() {
      throw new ProvisionException("User Exception", new RuntimeException());
    }
  }

  static class FProvider implements Provider<F> {
    public F get() {
      return new F();
    }
  }

  static class G {
    @Inject void injectFirst() {
      throw new IllegalArgumentException(new UnsupportedOperationException("Unsupported"));
    }
    @Inject void injectSecond() {
      throw new NullPointerException("can't inject second either");
    }
  }
}
