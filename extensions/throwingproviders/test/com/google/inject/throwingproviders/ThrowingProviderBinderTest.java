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

package com.google.inject.throwingproviders;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ThrowingProviderBinderTest extends TestCase {

  private final TypeLiteral<RemoteProvider<String>> remoteProviderOfString
      = new TypeLiteral<RemoteProvider<String>>() { };
  private final MockRemoteProvider<String> mockRemoteProvider = new MockRemoteProvider<String>();
  private final TestScope testScope = new TestScope();
  private Injector injector = Guice.createInjector(new AbstractModule() {
    protected void configure() {
      ThrowingProviderBinder.create(binder())
          .bind(RemoteProvider.class, String.class)
          .to(mockRemoteProvider)
          .in(testScope);
    }
  });

  public void testExceptionsThrown() {
    RemoteProvider<String> remoteProvider = 
        injector.getInstance(Key.get(remoteProviderOfString));

    mockRemoteProvider.throwOnNextGet("kaboom!");
    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertEquals("kaboom!", expected.getMessage());
    }
  }

  public void testValuesScoped() throws RemoteException {
    RemoteProvider<String> remoteProvider = 
        injector.getInstance(Key.get(remoteProviderOfString));

    mockRemoteProvider.setNextToReturn("A");
    assertEquals("A", remoteProvider.get());

    mockRemoteProvider.setNextToReturn("B");
    assertEquals("A", remoteProvider.get());

    testScope.beginNewScope();
    assertEquals("B", remoteProvider.get());
  }

  public void testExceptionsScoped() {
    RemoteProvider<String> remoteProvider = 
        injector.getInstance(Key.get(remoteProviderOfString));

    mockRemoteProvider.throwOnNextGet("A");
    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertEquals("A", expected.getMessage());
    }
    
    mockRemoteProvider.throwOnNextGet("B");
    try {
      remoteProvider.get();
      fail();
    } catch (RemoteException expected) {
      assertEquals("A", expected.getMessage());
    }
  }
  
  public void testAnnotations() throws RemoteException {
    final MockRemoteProvider<String> mockRemoteProviderA = new MockRemoteProvider<String>();
    mockRemoteProviderA.setNextToReturn("A");
    final MockRemoteProvider<String> mockRemoteProviderB = new MockRemoteProvider<String>();
    mockRemoteProviderB.setNextToReturn("B");

    injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        ThrowingProviderBinder.create(binder())
            .bind(RemoteProvider.class, String.class)
            .annotatedWith(Names.named("a"))
            .to(mockRemoteProviderA);

        ThrowingProviderBinder.create(binder())
            .bind(RemoteProvider.class, String.class)
            .to(mockRemoteProviderB);
      }
    });

    assertEquals("A", 
        injector.getInstance(Key.get(remoteProviderOfString, Names.named("a"))).get());

    assertEquals("B", 
        injector.getInstance(Key.get(remoteProviderOfString)).get());

  }
  
  public void testUndeclaredExceptions() throws RemoteException {
    RemoteProvider<String> remoteProvider = 
        injector.getInstance(Key.get(remoteProviderOfString));

    mockRemoteProvider.throwOnNextGet(new IndexOutOfBoundsException("A"));
    try {
      remoteProvider.get();
      fail();
    } catch (RuntimeException e) {
      assertEquals("A", e.getCause().getMessage());
    }

    // undeclared exceptions shouldn't be scoped
    mockRemoteProvider.throwOnNextGet(new IndexOutOfBoundsException("B"));
    try {
      remoteProvider.get();
      fail();
    } catch (RuntimeException e) {
      assertEquals("B", e.getCause().getMessage());
    }
  }

  public void testThrowingProviderSubclassing() throws RemoteException {
    final SubMockRemoteProvider aProvider = new SubMockRemoteProvider();
    aProvider.setNextToReturn("A");

    injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        ThrowingProviderBinder.create(binder())
            .bind(RemoteProvider.class, String.class)
            .to(aProvider);
      }
    });

    assertEquals("A",
        injector.getInstance(Key.get(remoteProviderOfString)).get());
  }

  static class SubMockRemoteProvider extends MockRemoteProvider<String> { }

  public void testBindingToNonInterfaceType() throws RemoteException {
    try {
      injector = Guice.createInjector(new AbstractModule() {
        protected void configure() {
          ThrowingProviderBinder.create(binder())
              .bind(MockRemoteProvider.class, String.class)
              .to(mockRemoteProvider);
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(expected.getMessage(), "is not a compliant interface");
    }
  }
  
  public void testBindingToSubSubInterface() throws RemoteException {
    try {
      injector = Guice.createInjector(new AbstractModule() {
        protected void configure() {
          ThrowingProviderBinder.create(binder())
              .bind(SubRemoteProvider.class, String.class);
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(expected.getMessage(), "is not a compliant interface");
    }
  }

  interface SubRemoteProvider extends RemoteProvider<String> { }

  public void testBindingToInterfaceWithExtraMethod() throws RemoteException {
    try {
      injector = Guice.createInjector(new AbstractModule() {
        protected void configure() {
          ThrowingProviderBinder.create(binder())
              .bind(RemoteProviderWithExtraMethod.class, String.class);
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(expected.getMessage(), "is not a compliant interface");
    }
  }

  interface RemoteProviderWithExtraMethod<T> extends ThrowingProvider<T, RemoteException> {
    T get(T defaultValue) throws RemoteException;
  }

  interface RemoteProvider<T> extends ThrowingProvider<T, RemoteException> { }
  
  static class MockRemoteProvider<T> implements RemoteProvider<T> {
    Exception nextToThrow;
    T nextToReturn;
    
    public void throwOnNextGet(String message) {
      throwOnNextGet(new RemoteException(message));
    }

    public void throwOnNextGet(Exception nextToThrow) {
      this.nextToThrow = nextToThrow;
    }

    public void setNextToReturn(T nextToReturn) {
      this.nextToReturn = nextToReturn;
    }
    
    public T get() throws RemoteException {
      if (nextToThrow instanceof RemoteException) {
        throw (RemoteException) nextToThrow;
      } else if (nextToThrow instanceof RuntimeException) {
        throw (RuntimeException) nextToThrow;
      } else if (nextToThrow == null) {
        return nextToReturn;
      } else {
        throw new AssertionError("nextToThrow must be a runtime or remote exception");
      }
    }
  }

  public void testBindingToInterfaceWithBoundValueType() throws RemoteException {
    injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        ThrowingProviderBinder.create(binder())
            .bind(StringRemoteProvider.class, String.class)
            .to(new StringRemoteProvider() {
              public String get() throws RemoteException {
                return "A";
              }
            });
      }
    });
    
    assertEquals("A", injector.getInstance(StringRemoteProvider.class).get());
  }

  interface StringRemoteProvider extends ThrowingProvider<String, RemoteException> { }

  public void testBindingToInterfaceWithGeneric() throws RemoteException {
    injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        ThrowingProviderBinder.create(binder())
            .bind(RemoteProvider.class, new TypeLiteral<List<String>>() { }.getType())
            .to(new RemoteProvider<List<String>>() {
              public List<String> get() throws RemoteException {
                return Arrays.asList("A", "B");
              }
            });
      }
    });

    Key<RemoteProvider<List<String>>> key
        = Key.get(new TypeLiteral<RemoteProvider<List<String>>>() { });
    assertEquals(Arrays.asList("A", "B"), injector.getInstance(key).get());
  }
}
