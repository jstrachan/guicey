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

import com.google.common.collect.Iterables;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeConverter;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.Date;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class TypeConversionTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation @interface NumericValue {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface BooleanValue {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface EnumValue {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface ClassName {}

  public static class Foo {
    @Inject @BooleanValue Boolean booleanField;
    @Inject @BooleanValue boolean primitiveBooleanField;
    @Inject @NumericValue Byte byteField;
    @Inject @NumericValue byte primitiveByteField;
    @Inject @NumericValue Short shortField;
    @Inject @NumericValue short primitiveShortField;
    @Inject @NumericValue Integer integerField;
    @Inject @NumericValue int primitiveIntField;
    @Inject @NumericValue Long longField;
    @Inject @NumericValue long primitiveLongField;
    @Inject @NumericValue Float floatField;
    @Inject @NumericValue float primitiveFloatField;
    @Inject @NumericValue Double doubleField;
    @Inject @NumericValue double primitiveDoubleField;
    @Inject @EnumValue Bar enumField;
    @Inject @ClassName Class<?> classField;
  }

  public enum Bar {
    TEE, BAZ, BOB
  }

  public void testOneConstantInjection() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(NumericValue.class).to("5");
        bind(Simple.class);
      }
    });

    Simple simple = injector.getInstance(Simple.class);
    assertEquals(5, simple.i);
  }

  static class Simple {
    @Inject @NumericValue int i;
  }

  public void testConstantInjection() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(NumericValue.class).to("5");
        bindConstant().annotatedWith(BooleanValue.class).to("true");
        bindConstant().annotatedWith(EnumValue.class).to("TEE");
        bindConstant().annotatedWith(ClassName.class).to(Foo.class.getName());
      }
    });

    Foo foo = injector.getInstance(Foo.class);

    checkNumbers(
      foo.integerField,
      foo.primitiveIntField,
      foo.longField,
      foo.primitiveLongField,
      foo.byteField,
      foo.primitiveByteField,
      foo.shortField,
      foo.primitiveShortField,
      foo.floatField,
      foo.primitiveFloatField,
      foo.doubleField,
      foo.primitiveDoubleField
    );

    assertEquals(Bar.TEE, foo.enumField);
    assertEquals(Foo.class, foo.classField);
  }

  void checkNumbers(Number... ns) {
    for (Number n : ns) {
      assertEquals(5, n.intValue());
    }
  }

  public void testInvalidInteger() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(NumericValue.class).to("invalid");
      }
    });

    try {
      injector.getInstance(InvalidInteger.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(), "Error converting 'invalid'");
      assertContains(expected.getMessage(), "bound at " + getClass().getName());
      assertContains(expected.getMessage(), "to java.lang.Integer");
    }
  }

  public static class InvalidInteger {
    @Inject @NumericValue Integer integerField;
  }

  public void testInvalidCharacter() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(NumericValue.class).to("invalid");
      }
    });

    try {
      injector.getInstance(InvalidCharacter.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(), "Error converting 'invalid'");
      assertContains(expected.getMessage(), "bound at " + getClass().getName());
      assertContains(expected.getMessage(), "to java.lang.Character");
    }
  }

  public static class InvalidCharacter {
    @Inject @NumericValue char foo;
  }

  public void testInvalidEnum() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(NumericValue.class).to("invalid");
      }
    });

    try {
      injector.getInstance(InvalidEnum.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(), "Error converting 'invalid'");
      assertContains(expected.getMessage(), "bound at " + getClass().getName());
      assertContains(expected.getMessage(), "to " + Bar.class.getName());
    }
  }

  public static class InvalidEnum {
    @Inject @NumericValue Bar foo;
  }

  public void testToInstanceIsTreatedLikeConstant() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("5");
        bind(LongHolder.class);
      }
    });
    
    assertEquals(5L, (long) injector.getInstance(LongHolder.class).foo);
  }

  static class LongHolder {
    @Inject Long foo;
  }

  public void testCustomTypeConversion() throws CreationException {
    final Date result = new Date();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        convertToTypes(Matchers.only(TypeLiteral.get(Date.class)) , mockTypeConverter(result));
        bindConstant().annotatedWith(NumericValue.class).to("Today");
        bind(DateHolder.class);
      }
    });

    assertSame(result, injector.getInstance(DateHolder.class).date);
  }

  public void testInvalidCustomValue() throws CreationException {
    Module module = new AbstractModule() {
      protected void configure() {
        convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), failingTypeConverter());
        bindConstant().annotatedWith(NumericValue.class).to("invalid");
        bind(DateHolder.class);
      }
    };

    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException expected) {
      Throwable cause = Iterables.getOnlyElement(expected.getErrorMessages()).getCause();
      assertTrue(cause instanceof UnsupportedOperationException);
      assertContains(expected.getMessage(),
          "Error at " + DateHolder.class.getName() + ".date(TypeConversionTest.java:",
          "Error converting 'invalid' (bound at ", getClass().getName(),
          ".configure(TypeConversionTest.java:", "to java.util.Date",
          "using BrokenConverter which matches only(java.util.Date) ",
          "(bound at " + getClass().getName(), ".configure(TypeConversionTest.java:",
          "Reason: java.lang.UnsupportedOperationException: Cannot convert");
    }
  }

  public void testNullCustomValue() {
    Module module = new AbstractModule() {
      protected void configure() {
        convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), mockTypeConverter(null));
        bindConstant().annotatedWith(NumericValue.class).to("foo");
        bind(DateHolder.class);
      }
    };

    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Error at " + DateHolder.class.getName() + ".date(TypeConversionTest.java:",
          "Received null converting 'foo' (bound at ", getClass().getName(),
          ".configure(TypeConversionTest.java:", "to java.util.Date",
          "using CustomConverter which matches only(java.util.Date) ",
          "(bound at " + getClass().getName(), ".configure(TypeConversionTest.java:");
    }
  }

  public void testCustomValueTypeMismatch() {
    Module module = new AbstractModule() {
      protected void configure() {
        convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), mockTypeConverter(-1));
        bindConstant().annotatedWith(NumericValue.class).to("foo");
        bind(DateHolder.class);
      }
    };

    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Error at " + DateHolder.class.getName() + ".date(TypeConversionTest.java:",
          "Type mismatch converting 'foo' (bound at ", getClass().getName(),
          ".configure(TypeConversionTest.java:", "to java.util.Date",
          "using CustomConverter which matches only(java.util.Date) ",
          "(bound at " + getClass().getName(), ".configure(TypeConversionTest.java:",
          "Converter returned -1.");
    }
  }

  public void testAmbiguousTypeConversion() {
    Module module = new AbstractModule() {
      protected void configure() {
        convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), mockTypeConverter(new Date()));
        convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), mockTypeConverter(new Date()));
        bindConstant().annotatedWith(NumericValue.class).to("foo");
        bind(DateHolder.class);
      }
    };

    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Error at " + DateHolder.class.getName() + ".date(TypeConversionTest.java:",
          "Multiple converters can convert 'foo' (bound at ", getClass().getName(),
          ".configure(TypeConversionTest.java:", "to java.util.Date:",
          "CustomConverter which matches only(java.util.Date)", "and",
          "CustomConverter which matches only(java.util.Date)",
          "Please adjust your type converter configuration to avoid overlapping matches.");
    }
  }

  TypeConverter mockTypeConverter(final Object result) {
    return new TypeConverter() {
      public Object convert(String value, TypeLiteral<?> toType) {
        return result;
      }

      @Override public String toString() {
        return "CustomConverter";
      }
    };
  }

  private TypeConverter failingTypeConverter() {
    return new TypeConverter() {
      public Object convert(String value, TypeLiteral<?> toType) {
        throw new UnsupportedOperationException("Cannot convert");
      }
      @Override public String toString() {
        return "BrokenConverter";
      }
    };
  }

  static class DateHolder {
    @Inject @NumericValue Date date;
  }
}
