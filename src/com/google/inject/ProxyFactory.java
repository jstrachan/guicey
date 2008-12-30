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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.internal.BytecodeGen;
import com.google.inject.internal.BytecodeGen.Visibility;
import static com.google.inject.internal.BytecodeGen.newEnhancer;
import com.google.inject.internal.ReferenceCache;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastConstructor;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Proxies classes applying interceptors to methods.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ProxyFactory implements ConstructionProxyFactory {

  final List<MethodAspect> methodAspects;
  final ConstructionProxyFactory defaultFactory;

  ProxyFactory(List<MethodAspect> methodAspects) {
    this.methodAspects = methodAspects;
    defaultFactory = new DefaultConstructionProxyFactory();
  }

  @SuppressWarnings("unchecked") // the constructed T is the same as the injection point's T
  public <T> ConstructionProxy<T> get(InjectionPoint injectionPoint) {
    return (ConstructionProxy<T>) constructionProxies.get(injectionPoint);
  }

  /** Cached construction proxies for each injection point */
  ReferenceCache<InjectionPoint, ConstructionProxy> constructionProxies
      = new ReferenceCache<InjectionPoint, ConstructionProxy>() {
    protected ConstructionProxy create(InjectionPoint key) {
      return createConstructionProxy(key);
    }
  };

  <T> ConstructionProxy<T> createConstructionProxy(InjectionPoint injectionPoint) {
    @SuppressWarnings("unchecked") // the member of injectionPoint is always a Constructor<T>
    Constructor<T> constructor = (Constructor<T>) injectionPoint.getMember();
    Class<T> declaringClass = constructor.getDeclaringClass();

    // Find applicable aspects. Bow out if none are applicable to this class.
    List<MethodAspect> applicableAspects = Lists.newArrayList();
    for (MethodAspect methodAspect : methodAspects) {
      if (methodAspect.matches(declaringClass)) {
        applicableAspects.add(methodAspect);
      }
    }
    if (applicableAspects.isEmpty()) {
      return defaultFactory.get(injectionPoint);
    }

    // Get list of methods from cglib.
    List<Method> methods = Lists.newArrayList();
    Enhancer.getMethods(declaringClass, null, methods);

    // Create method/interceptor holders and record indices.
    List<MethodInterceptorsPair> methodInterceptorsPairs = Lists.newArrayList();
    for (Method method : methods) {
      methodInterceptorsPairs.add(new MethodInterceptorsPair(method));
    }

    // PUBLIC if all the methods we're intercepting are public. This impacts which classloader we
    // should use for loading the enhanced class
    Visibility visibility = Visibility.PUBLIC;

    // Iterate over aspects and add interceptors for the methods they apply to
    boolean anyMatched = false;
    for (MethodAspect methodAspect : applicableAspects) {
      for (MethodInterceptorsPair pair : methodInterceptorsPairs) {
        if (methodAspect.matches(pair.method)) {
          visibility = visibility.and(Visibility.forMember(pair.method));
          pair.addAll(methodAspect.interceptors());
          anyMatched = true;
        }
      }
    }
    if (!anyMatched) {
      // not test-covered
      return defaultFactory.get(injectionPoint);
    }

    @SuppressWarnings("unchecked")
    Class<? extends Callback>[] callbackTypes = new Class[methods.size()];
    Arrays.fill(callbackTypes, net.sf.cglib.proxy.MethodInterceptor.class);

    // Create the proxied class. We're careful to ensure that all enhancer state is not-specific to
    // this injector. Otherwise, the proxies for each injector will waste Permgen memory
    Enhancer enhancer = newEnhancer(declaringClass, visibility);
    enhancer.setCallbackFilter(new IndicesCallbackFilter(declaringClass, methods));
    enhancer.setCallbackTypes(callbackTypes);

    return new ProxyConstructor<T>(methods, methodInterceptorsPairs, enhancer, injectionPoint);
  }

  private static final net.sf.cglib.proxy.MethodInterceptor NO_OP_METHOD_INTERCEPTOR
      = new net.sf.cglib.proxy.MethodInterceptor() {
    public Object intercept(
        Object proxy, Method method, Object[] arguments, MethodProxy methodProxy)
        throws Throwable {
      return methodProxy.invokeSuper(proxy, arguments);
    }
  };

  /**
   * Constructs instances that participate in AOP.
   */
  private static class ProxyConstructor<T> implements ConstructionProxy<T> {
    private final Class<?> enhanced;
    private final InjectionPoint injectionPoint;
    private final Constructor<T> constructor;

    private final Callback[] callbacks;
    private final FastConstructor fastConstructor;
    private final ImmutableMap<Method, List<MethodInterceptor>> methodInterceptors;

    @SuppressWarnings("unchecked") // the constructor promises to construct 'T's
    ProxyConstructor(List<Method> methods, List<MethodInterceptorsPair> methodInterceptorsPairs,
        Enhancer enhancer, InjectionPoint injectionPoint) {
      this.enhanced = enhancer.createClass(); // this returns a cached class if possible
      this.injectionPoint = injectionPoint;
      this.constructor = (Constructor<T>) injectionPoint.getMember();

      ImmutableMap.Builder<Method, List<MethodInterceptor>> interceptorsMapBuilder = null; // lazy

      this.callbacks = new Callback[methods.size()];
      for (int i = 0; i < methods.size(); i++) {
        MethodInterceptorsPair pair = methodInterceptorsPairs.get(i);

        if (!pair.hasInterceptors()) {
          callbacks[i] = NO_OP_METHOD_INTERCEPTOR;
          continue;
        }

        if (interceptorsMapBuilder == null) {
          interceptorsMapBuilder = ImmutableMap.builder();
        }
        interceptorsMapBuilder.put(pair.method, ImmutableList.copyOf(pair.interceptors));
        callbacks[i] = new InterceptorStackCallback(pair.method, pair.interceptors);
      }

      FastClass fastClass = BytecodeGen.newFastClass(enhanced, Visibility.forMember(constructor));
      this.fastConstructor = fastClass.getConstructor(constructor.getParameterTypes());
      this.methodInterceptors = interceptorsMapBuilder != null
          ? interceptorsMapBuilder.build()
          : ImmutableMap.<Method, List<MethodInterceptor>>of();
    }

    @SuppressWarnings("unchecked") // the constructor promises to produce 'T's
    public T newInstance(Object[] arguments) throws InvocationTargetException {
      Enhancer.registerCallbacks(enhanced, callbacks);
      try {
        return (T) fastConstructor.newInstance(arguments);
      } finally {
        Enhancer.registerCallbacks(enhanced, null);
      }
    }

    public InjectionPoint getInjectionPoint() {
      return injectionPoint;
    }

    public Constructor<T> getConstructor() {
      return constructor;
    }

    public Map<Method, List<MethodInterceptor>> getMethodInterceptors() {
      return methodInterceptors;
    }
  }

  private static class MethodInterceptorsPair {
    final Method method;
    List<MethodInterceptor> interceptors;

    public MethodInterceptorsPair(Method method) {
      this.method = method;
    }

    void addAll(List<MethodInterceptor> interceptors) {
      if (this.interceptors == null) {
        this.interceptors = Lists.newArrayList();
      }
      this.interceptors.addAll(interceptors);
    }

    boolean hasInterceptors() {
      return interceptors != null;
    }
  }

  /**
   * A callback filter that maps methods to unique IDs. We define equals and hashCode using the
   * declaring class so that enhanced classes can be shared between injectors.
   */
  private static class IndicesCallbackFilter implements CallbackFilter {
    final Class<?> declaringClass;
    final Map<Method, Integer> indices;

    public IndicesCallbackFilter(Class<?> declaringClass, List<Method> methods) {
      this.declaringClass = declaringClass;
      final Map<Method, Integer> indices = Maps.newHashMap();
      for (int i = 0; i < methods.size(); i++) {
        Method method = methods.get(i);
        indices.put(method, i);
      }

      this.indices = indices;
    }

    public int accept(Method method) {
      return indices.get(method);
    }

    @Override public boolean equals(Object o) {
      return o instanceof IndicesCallbackFilter &&
          ((IndicesCallbackFilter) o).declaringClass == declaringClass;
    }

    @Override public int hashCode() {
      return declaringClass.hashCode();
    }
  }
}
