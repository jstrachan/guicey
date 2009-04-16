# Guicey

This is a fork of [google guice](http://code.google.com/p/google-guice/) with a number of patches
added to support [GuiceyFruit](http://code.google.com/p/guiceyfruit/)


## Patches applied

* [354](http://code.google.com/p/google-guice/issues/detail?id=354) provide a way to iterate through all objects in a scope
* [343](http://code.google.com/p/google-guice/issues/detail?id=343) OSGi fix: BytecodeGen uses system classloader when applying AOP to system types
* [337](http://code.google.com/p/google-guice/issues/detail?id=337) OSGi fix: AssistedInject needs class load bridging under OSGi
