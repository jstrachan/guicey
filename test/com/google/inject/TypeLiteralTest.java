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

import static com.google.inject.Asserts.assertEqualsBothWays;
import static com.google.inject.Asserts.assertNotSerializable;
import com.google.inject.util.Types;
import java.io.IOException;
import java.util.List;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class TypeLiteralTest extends TestCase {

  public void testWithParameterizedType() {
    TypeLiteral<List<String>> a = new TypeLiteral<List<String>>() {};
    TypeLiteral<List<String>> b = new TypeLiteral<List<String>>(
        Types.listOf(String.class)) {};
    assertEqualsBothWays(a, b);
  }

  public void testEquality() {
    TypeLiteral<List<String>> t1 = new TypeLiteral<List<String>>() {};
    TypeLiteral<List<String>> t2 = new TypeLiteral<List<String>>() {};
    TypeLiteral<List<Integer>> t3 = new TypeLiteral<List<Integer>>() {};
    TypeLiteral<String> t4 = new TypeLiteral<String>() {};

    assertEqualsBothWays(t1, t2);

    assertFalse(t2.equals(t3));
    assertFalse(t3.equals(t2));

    assertFalse(t2.equals(t4));
    assertFalse(t4.equals(t2));

    TypeLiteral<String> t5 = TypeLiteral.get(String.class);
    assertEqualsBothWays(t4, t5);
  }

  public List<? extends CharSequence> wildcardExtends;

  public void testWithWildcardType() throws NoSuchFieldException, IOException {
    TypeLiteral<?> a = TypeLiteral.get(getClass().getField("wildcardExtends").getGenericType());
    TypeLiteral<?> b = TypeLiteral.get(Types.listOf(Types.subtypeOf(CharSequence.class)));
    assertEqualsBothWays(a, b);
    assertEquals("java.util.List<? extends java.lang.CharSequence>", a.toString());
    assertEquals("java.util.List<? extends java.lang.CharSequence>", b.toString());
    assertNotSerializable(a);
    assertNotSerializable(b);
  }

  public void testMissingTypeParameter() {
    try {
      new TypeLiteral() {};
      fail();
    } catch (RuntimeException e) { /* expected */ }
  }

  public void testTypesInvolvingArraysForEquality() {
    TypeLiteral<String[]> stringArray = new TypeLiteral<String[]>() {};
    assertEquals(stringArray, new TypeLiteral<String[]>() {});

    TypeLiteral<List<String[]>> listOfStringArray
        = new TypeLiteral<List<String[]>>() {};
    assertEquals(listOfStringArray, new TypeLiteral<List<String[]>>() {});
  }

  public void testEqualityOfGenericArrayAndClassArray() {
    TypeLiteral<String[]> arrayAsClass = TypeLiteral.get(String[].class);
    TypeLiteral<String[]> arrayAsType = new TypeLiteral<String[]>() {};
    assertEquals(arrayAsClass, arrayAsType);
  }

  public void testEqualityOfMultidimensionalGenericArrayAndClassArray() {
    TypeLiteral<String[][][]> arrayAsClass = TypeLiteral.get(String[][][].class);
    TypeLiteral<String[][][]> arrayAsType = new TypeLiteral<String[][][]>() {};
    assertEquals(arrayAsClass, arrayAsType);
  }

  public void testTypeLiteralsMustHaveRawTypes() {
    try {
      TypeLiteral.get(Types.subtypeOf(Runnable.class));
      fail();
    } catch (IllegalArgumentException expected) {
      Asserts.assertContains(expected.getMessage(), "Expected a Class, ParameterizedType, or "
          + "GenericArrayType, but <? extends java.lang.Runnable> is of type "
          + "com.google.inject.internal.MoreTypes$WildcardTypeImpl");
    }
  }

  /**
   * Unlike Key, TypeLiteral retains full type information and differentiates
   * between {@code int.class} and {@code Integer.class}.
   */
  public void testDifferentiationBetweenWrappersAndPrimitives() {
    Class[] primitives = new Class[] {
        boolean.class, byte.class, short.class, int.class, long.class,
        float.class, double.class, char.class, void.class
    };
    Class[] wrappers = new Class[] {
        Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
        Float.class, Double.class, Character.class, Void.class
    };

    for (int t = 0; t < primitives.length; t++) {
      @SuppressWarnings("unchecked")
      TypeLiteral primitiveTl = TypeLiteral.get(primitives[t]);
      @SuppressWarnings("unchecked")
      TypeLiteral wrapperTl = TypeLiteral.get(wrappers[t]);

      assertFalse(primitiveTl.equals(wrapperTl));
      assertEquals(primitives[t], primitiveTl.getType());
      assertEquals(wrappers[t], wrapperTl.getType());
      assertEquals(primitives[t], primitiveTl.getRawType());
      assertEquals(wrappers[t], wrapperTl.getRawType());
    }
  }

  public void testSerialization() throws IOException {
    assertNotSerializable(new TypeLiteral<List<String>>() {});
  }
}
