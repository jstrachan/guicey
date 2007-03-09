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

package com.google.inject.binder;

import java.lang.annotation.Annotation;

/**
 * Specifies the annotation for a constant binding.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface AnnotatedConstantBindingBuilder {

  /**
   * Specifies the marker annotation type for this binding.
   */
  ConstantBindingBuilder annotatedWith(
      Class<? extends Annotation> annotationType);

  /**
   * Specifies an annotation value for this binding.
   */
  ConstantBindingBuilder annotatedWith(Annotation annotation);
}
