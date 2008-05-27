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

package com.google.inject.spi;

import static com.google.inject.internal.Objects.nonNull;

import java.io.Serializable;

/**
 * A message. Contains a source pointing to the code which resulted
 * in this message and a text message.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class Message implements Serializable {

  private final String source;
  private final String message;

  public Message(Object source, String message) {
    this.source = nonNull(source, "source").toString();
    this.message = nonNull(message, "message");
  }

  public Message(String message) {
    this(SourceProviders.UNKNOWN_SOURCE, message);
  }

  /**
   * Returns a string representation of the source object. 
   */
  public String getSource() {
    return source;
  }

  /**
   * Gets the error message text.
   */
  public String getMessage() {
    return message;
  }

  public String toString() {
    return source + " " + message;
  }

  public int hashCode() {
    return source.hashCode() * 31 + message.hashCode();
  }

  public boolean equals(Object o) {
    if (!(o instanceof Message)) {
      return false;
    }
    Message e = (Message) o;
    return source.equals(e.source) && message.equals(e.message);
  }
}
