/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

/**
 * An interface to expose a cached value in a provider such as for a Scope.
 * A Scope may choose to implement this method so that it can expose the current value in the scope
 *
 * @version $Revision: 1.1 $
 */
public interface CachedValue<T> {
  /**
   * Returns the currently cached value in a scope if its available or supported or null if there is
   * no cached value yet or caching is not supported.
   */
  T getCachedValue();
}
