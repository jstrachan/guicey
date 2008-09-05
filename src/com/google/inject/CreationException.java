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
import com.google.inject.internal.Errors;
import com.google.inject.spi.Message;
import java.util.Collection;
import java.util.List;

/**
 * Thrown when errors occur while creating a {@link Injector}. Includes a list
 * of encountered errors. Typically, a client should catch this exception, log
 * it, and stop execution.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class CreationException extends RuntimeException {

  private final List<Message> errorMessages;

  /**
   * Constructs a new exception for the given errors.
   */
  public CreationException(Collection<? extends Message> errorMessages) {
    this.errorMessages = ImmutableList.copyOf(errorMessages);

    // find a cause
    for (Message message : errorMessages) {
      if (message.getCause() != null) {
        initCause(message.getCause());
        break;
      }
    }
  }

  /**
   * Gets the error messages which resulted in this exception.
   */
  public Collection<Message> getErrorMessages() {
    return errorMessages;
  }

  @Override public String getMessage() {
    return Errors.format("Guice configuration errors", errorMessages);
  }

  private static final long serialVersionUID = 0;
}
