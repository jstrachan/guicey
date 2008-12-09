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

package com.google.inject.assistedinject;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.assertEqualsBothWays;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.awt.Color;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class FactoryProvider2Test extends TestCase {

  public void testAssistedFactory() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Double.class).toInstance(5.0d);
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Mustang blueMustang = (Mustang) carFactory.create(Color.BLUE);
    assertEquals(Color.BLUE, blueMustang.color);
    assertEquals(5.0d, blueMustang.engineSize);

    Mustang redMustang = (Mustang) carFactory.create(Color.RED);
    assertEquals(Color.RED, redMustang.color);
    assertEquals(5.0d, redMustang.engineSize);
  }

  public void testAssistedFactoryWithAnnotations() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(int.class).annotatedWith(Names.named("horsePower")).toInstance(250);
        bind(int.class).annotatedWith(Names.named("modelYear")).toInstance(1984);
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, Camaro.class));
      }
    });

    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Camaro blueCamaro = (Camaro) carFactory.create(Color.BLUE);
    assertEquals(Color.BLUE, blueCamaro.color);
    assertEquals(1984, blueCamaro.modelYear);
    assertEquals(250, blueCamaro.horsePower);

    Camaro redCamaro = (Camaro) carFactory.create(Color.RED);
    assertEquals(Color.RED, redCamaro.color);
    assertEquals(1984, redCamaro.modelYear);
    assertEquals(250, redCamaro.horsePower);
  }

  interface Car {}

  interface ColoredCarFactory {
    Car create(Color color);
  }

  public static class Mustang implements Car {
    private final double engineSize;
    private final Color color;

    @Inject
    public Mustang(double engineSize, @Assisted Color color) {
      this.engineSize = engineSize;
      this.color = color;
    }

    public void drive() {}
  }

  public static class Camaro implements Car {
    private final int horsePower;
    private final int modelYear;
    private final Color color;

    @Inject
    public Camaro(
        @Named("horsePower") int horsePower,
        @Named("modelYear") int modelYear,
        @Assisted Color color) {
      this.horsePower = horsePower;
      this.modelYear = modelYear;
      this.color = color;
    }
  }

  interface SummerCarFactory {
    Car create(Color color, boolean convertable);
  }

  public void testFactoryUsesInjectedConstructor() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(float.class).toInstance(140f);
        bind(SummerCarFactory.class).toProvider(
            FactoryProvider.newFactory(SummerCarFactory.class, Corvette.class));
      }
    });

    SummerCarFactory carFactory = injector.getInstance(SummerCarFactory.class);

    Corvette redCorvette = (Corvette) carFactory.create(Color.RED, false);
    assertEquals(Color.RED, redCorvette.color);
    assertEquals(140f, redCorvette.maxMph);
    assertFalse(redCorvette.isConvertable);
  }

  public static class Corvette implements Car {
    private boolean isConvertable;
    private Color color;
    private float maxMph;

    public Corvette(Color color, boolean isConvertable) {
      throw new IllegalStateException("Not an @AssistedInject constructor");
    }

    @Inject
    public Corvette(@Assisted Color color, Float maxMph, @Assisted boolean isConvertable) {
      this.isConvertable = isConvertable;
      this.color = color;
      this.maxMph = maxMph;
    }
  }

  public void testConstructorDoesntNeedAllFactoryMethodArguments() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(SummerCarFactory.class).toProvider(
            FactoryProvider.newFactory(SummerCarFactory.class, Beetle.class));
      }
    });
    SummerCarFactory factory = injector.getInstance(SummerCarFactory.class);

    Beetle beetle = (Beetle) factory.create(Color.RED, true);
    assertSame(Color.RED, beetle.color);
  }

  public static class Beetle implements Car {
    private final Color color;
    @Inject
    public Beetle(@Assisted Color color) {
      this.color = color;
    }
  }

  public void testMethodsAndFieldsGetInjected() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class).toInstance("turbo");
        bind(int.class).toInstance(911);
        bind(double.class).toInstance(50000d);
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, Porshe.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Porshe grayPorshe = (Porshe) carFactory.create(Color.GRAY);
    assertEquals(Color.GRAY, grayPorshe.color);
    assertEquals(50000d, grayPorshe.price);
    assertEquals(911, grayPorshe.model);
    assertEquals("turbo", grayPorshe.name);
  }

  public static class Porshe implements Car {
    private final Color color;
    private final double price;
    private @Inject String name;
    private int model;

    @Inject
    public Porshe(@Assisted Color color, double price) {
      this.color = color;
      this.price = price;
    }

    @Inject void setModel(int model) {
      this.model = model;
    }
  }

  public void testProviderInjection() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class).toInstance("trans am");
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, Firebird.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Firebird blackFirebird = (Firebird) carFactory.create(Color.BLACK);
    assertEquals(Color.BLACK, blackFirebird.color);
    assertEquals("trans am", blackFirebird.modifiersProvider.get());
  }

  public static class Firebird implements Car {
    private final Provider<String> modifiersProvider;
    private final Color color;

    @Inject
    public Firebird(Provider<String> modifiersProvider, @Assisted Color color) {
      this.modifiersProvider = modifiersProvider;
      this.color = color;
    }
  }

  public void testTypeTokenInjection() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(new TypeLiteral<Set<String>>() {}).toInstance(Collections.singleton("Flux Capacitor"));
        bind(new TypeLiteral<Set<Integer>>() {}).toInstance(Collections.singleton(88));
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, DeLorean.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    DeLorean deLorean = (DeLorean) carFactory.create(Color.GRAY);
    assertEquals(Color.GRAY, deLorean.color);
    assertEquals("Flux Capacitor", deLorean.features.iterator().next());
    assertEquals(new Integer(88), deLorean.featureActivationSpeeds.iterator().next());
  }

  public static class DeLorean implements Car {
    private final Set<String> features;
    private final Set<Integer> featureActivationSpeeds;
    private final Color color;

    @Inject
    public DeLorean(
        Set<String> extraFeatures, Set<Integer> featureActivationSpeeds, @Assisted Color color) {
      this.features = extraFeatures;
      this.featureActivationSpeeds = featureActivationSpeeds;
      this.color = color;
    }
  }

  public void testTypeTokenProviderInjection() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(new TypeLiteral<Set<String>>() { }).toInstance(Collections.singleton("Datsun"));
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, Z.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Z orangeZ = (Z) carFactory.create(Color.ORANGE);
    assertEquals(Color.ORANGE, orangeZ.color);
    assertEquals("Datsun", orangeZ.manufacturersProvider.get().iterator().next());
  }

  public static class Z implements Car {
    private final Provider<Set<String>> manufacturersProvider;
    private final Color color;

    @Inject
    public Z(Provider<Set<String>> manufacturersProvider, @Assisted Color color) {
      this.manufacturersProvider = manufacturersProvider;
      this.color = color;
    }
  }

  public static class Prius implements Car {
    @SuppressWarnings("unused")
    final Color color;

    @Inject
    private Prius(@Assisted Color color) {
      this.color = color;
    }
  }

  public void testAssistInjectionInNonPublicConstructor() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, Prius.class));
      }
    });
    Car car = injector.getInstance(ColoredCarFactory.class).create(Color.ORANGE);
  }

  public static class ExplodingCar implements Car {
    @Inject
    public ExplodingCar(@Assisted Color color) {
      throw new IllegalStateException("kaboom!");
    }
  }

  public void testExceptionDuringConstruction() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, ExplodingCar.class));
      }
    });
    try {
      injector.getInstance(ColoredCarFactory.class).create(Color.ORANGE);
      fail();
    } catch (IllegalStateException e) {
      assertEquals("kaboom!", e.getMessage());
    }
  }

  public static class DefectiveCar implements Car {
    @Inject
    public DefectiveCar() throws ExplosionException, FireException {
      throw new ExplosionException();
    }
  }

  public static class ExplosionException extends Exception { }
  public static class FireException extends Exception { }

  public interface DefectiveCarFactoryWithNoExceptions {
    Car createCar();
  }

  public interface DefectiveCarFactory {
    Car createCar() throws FireException;
  }

  public interface CorrectDefectiveCarFactory {
    Car createCar() throws FireException, ExplosionException;
  }

  public void testConstructorExceptionsAreThrownByFactory() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(CorrectDefectiveCarFactory.class).toProvider(
            FactoryProvider.newFactory(CorrectDefectiveCarFactory.class, DefectiveCar.class));
      }
    });
    try {
      injector.getInstance(CorrectDefectiveCarFactory.class).createCar();
      fail();
    } catch (FireException e) {
      fail();
    } catch (ExplosionException expected) {
    }
  }

  public static class WildcardCollection {

    public interface Factory {
      WildcardCollection create(Collection<?> items);
    }

    @Inject
    public WildcardCollection(@Assisted Collection<?> items) { }
  }

  public void testWildcardGenerics() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(WildcardCollection.Factory.class).toProvider(
            FactoryProvider.newFactory(WildcardCollection.Factory.class, WildcardCollection.class));
      }
    });
    WildcardCollection.Factory factory = injector.getInstance(WildcardCollection.Factory.class);
    factory.create(Collections.emptyList());
  }

  public static class SteeringWheel {}

  public static class Fiat implements Car {
    @SuppressWarnings("unused")
    private final SteeringWheel steeringWheel;
    @SuppressWarnings("unused")
    private final Color color;

    @Inject
    public Fiat(SteeringWheel steeringWheel, @Assisted Color color) {
      this.steeringWheel = steeringWheel;
      this.color = color;
    }
  }

  public void testFactoryWithImplicitBindings() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, Fiat.class));
      }
    });

    ColoredCarFactory coloredCarFactory = injector.getInstance(ColoredCarFactory.class);
    Fiat fiat = (Fiat) coloredCarFactory.create(Color.GREEN);
    assertEquals(Color.GREEN, fiat.color);
    assertNotNull(fiat.steeringWheel);
  }

  public void testFactoryFailsWithMissingBinding() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(ColoredCarFactory.class).toProvider(
              FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Could not find a suitable constructor in java.lang.Double.",
          "at " + ColoredCarFactory.class.getName() + ".create(FactoryProvider2Test.java");
    }
  }
  
  public void testMethodsDeclaredInObject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(Double.class).toInstance(5.0d);
          bind(ColoredCarFactory.class).toProvider(
              FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
        }
      });

    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    assertEqualsBothWays(carFactory, carFactory);
    assertEquals(ColoredCarFactory.class.getName() + " for " + Mustang.class.getName(),
        carFactory.toString());
  }

  static class Subaru implements Car {
    @Inject @Assisted Provider<Color> colorProvider;
  }

  public void testInjectingProviderOfParameter() {
    Injector injector = Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(ColoredCarFactory.class).toProvider(
              FactoryProvider.newFactory(ColoredCarFactory.class, Subaru.class));
        }
      });

    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);
    Subaru subaru = (Subaru) carFactory.create(Color.RED);

    assertSame(Color.RED, subaru.colorProvider.get());
    assertSame(Color.RED, subaru.colorProvider.get());
  }

  public void testInjectingNullParameter() {
    Injector injector = Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(ColoredCarFactory.class).toProvider(
              FactoryProvider.newFactory(ColoredCarFactory.class, Subaru.class));
        }
      });

    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);
    Subaru subaru = (Subaru) carFactory.create(null);

    assertNull(subaru.colorProvider.get());
    assertNull(subaru.colorProvider.get());
  }

  public void testFactoryUseBeforeInitialization() {
    ColoredCarFactory carFactory = FactoryProvider.newFactory(ColoredCarFactory.class, Subaru.class)
        .get();
    try {
      carFactory.create(Color.RED);
      fail();
    } catch (IllegalStateException expected) {
      assertContains(expected.getMessage(),
          "Factories.create() factories cannot be used until they're initialized by Guice.");
    }
  }

  interface MustangFactory {
    Mustang create(Color color);
  }

  public void testFactoryBuildingConcreteTypes() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(double.class).toInstance(5.0d);
        // note there is no 'thatMakes()' call here:
        bind(MustangFactory.class).toProvider(
            FactoryProvider.newFactory(MustangFactory.class, Mustang.class));
      }
    });
    MustangFactory factory = injector.getInstance(MustangFactory.class);

    Mustang mustang = factory.create(Color.RED);
    assertSame(Color.RED, mustang.color);
    assertEquals(5.0d, mustang.engineSize);
  }

  static class Fleet {
    @Inject Mustang mustang;
    @Inject Camaro camaro;
  }

  interface FleetFactory {
    Fleet createFleet(Color color);
  }

  public void testInjectDeepIntoConstructedObjects() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(double.class).toInstance(5.0d);
        bind(int.class).annotatedWith(Names.named("horsePower")).toInstance(250);
        bind(int.class).annotatedWith(Names.named("modelYear")).toInstance(1984);
        bind(FleetFactory.class).toProvider(FactoryProvider.newFactory(FleetFactory.class,
            Fleet.class));
      }
    });

    FleetFactory fleetFactory = injector.getInstance(FleetFactory.class);
    Fleet fleet = fleetFactory.createFleet(Color.RED);

    assertSame(Color.RED, fleet.mustang.color);
    assertEquals(5.0d, fleet.mustang.engineSize);
    assertSame(Color.RED, fleet.camaro.color);
    assertEquals(250, fleet.camaro.horsePower);
    assertEquals(1984, fleet.camaro.modelYear);
  }

  interface TwoToneCarFactory {
    Car create(@Assisted("paint") Color paint, @Assisted("fabric") Color fabric);
  }

  static class Maxima implements Car {
    @Inject @Assisted("paint") Color paint;
    @Inject @Assisted("fabric") Color fabric;
  }
  
  public void testDistinctKeys() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(TwoToneCarFactory.class).toProvider(
            FactoryProvider.newFactory(TwoToneCarFactory.class, Maxima.class));
      }
    });

    TwoToneCarFactory factory = injector.getInstance(TwoToneCarFactory.class);
    Maxima maxima = (Maxima) factory.create(Color.BLACK, Color.GRAY);
    assertSame(Color.BLACK, maxima.paint);
    assertSame(Color.GRAY, maxima.fabric);
  }

  interface DoubleToneCarFactory {
    Car create(@Assisted("paint") Color paint, @Assisted("paint") Color morePaint);
  }

  public void testDuplicateKeys() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(DoubleToneCarFactory.class).toProvider(
              FactoryProvider.newFactory(DoubleToneCarFactory.class, Maxima.class));
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "A binding to java.awt.Color annotated with @"
          + Assisted.class.getName() + "(value=paint) was already configured at");
    }
  }

  public void testMethodInterceptorsOnAssistedTypes() {
    final AtomicInteger invocationCount = new AtomicInteger();
    final MethodInterceptor interceptor = new MethodInterceptor() {
      public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        invocationCount.incrementAndGet();
        return methodInvocation.proceed();
      }
    };

    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.any(), interceptor);
        bind(Double.class).toInstance(5.0d);
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
      }
    });

    ColoredCarFactory factory = injector.getInstance(ColoredCarFactory.class);
    Mustang mustang = (Mustang) factory.create(Color.GREEN);
    assertEquals(0, invocationCount.get());
    mustang.drive();
    assertEquals(1, invocationCount.get());
  }

  public void testDefaultAssistedAnnotation() throws NoSuchFieldException {
    assertEqualsBothWays(FactoryProvider2.DEFAULT_ANNOTATION,
        Subaru.class.getDeclaredField("colorProvider").getAnnotation(Assisted.class));
  }
}
