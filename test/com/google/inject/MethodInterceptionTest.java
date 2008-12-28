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

import com.google.inject.matcher.Matchers;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class MethodInterceptionTest extends TestCase {

  private final MethodInterceptor returnNullInterceptor = new MethodInterceptor() {
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      return null;
    }
  };

  public void testSharedProxyClasses() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.returns(Matchers.only(Foo.class)),
            returnNullInterceptor);
      }
    });

    Injector nullFoosInjector = injector.createChildInjector(new AbstractModule() {
      protected void configure() {
        bind(Interceptable.class);
      }
    });

    Interceptable nullFoos = nullFoosInjector.getInstance(Interceptable.class);
    assertNotNull(nullFoos.bar());
    assertNull(nullFoos.foo());

    Injector nullFoosAndBarsInjector = injector.createChildInjector(new AbstractModule() {
      protected void configure() {
        bind(Interceptable.class);
        bindInterceptor(Matchers.any(), Matchers.returns(Matchers.only(Bar.class)),
            returnNullInterceptor);
      }
    });

    Interceptable bothNull = nullFoosAndBarsInjector.getInstance(Interceptable.class);
    assertNull(bothNull.bar());
    assertNull(bothNull.foo());
    
    assertSame("Child injectors should share proxy classes, otherwise memory leaks!",
        nullFoos.getClass(), bothNull.getClass());
  }
  
  public void testGetThis() {
    final AtomicReference<Object> lastTarget = new AtomicReference<Object>();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.any(), new MethodInterceptor() {
          public Object invoke(MethodInvocation methodInvocation) throws Throwable {
            lastTarget.set(methodInvocation.getThis());
            return methodInvocation.proceed();
          }
        });
      }
    });

    Interceptable interceptable = injector.getInstance(Interceptable.class);
    interceptable.foo();
    assertSame(interceptable, lastTarget.get());
  }

  static class Foo {}
  static class Bar {}

  public static class Interceptable {
    public Foo foo() {
      return new Foo() {};
    }
    public Bar bar() {
      return new Bar() {};
    }
  }
}
