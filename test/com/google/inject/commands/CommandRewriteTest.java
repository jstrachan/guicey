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

package com.google.inject.commands;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.name.Names;
import java.util.List;
import junit.framework.TestCase;


/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CommandRewriteTest extends TestCase {

  public void testRewriteBindings() {
    // create a module the binds String.class and CharSequence.class
    Module module = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("Pizza");
        bind(CharSequence.class).toInstance("Wine");
      }
    };

    // record the commands from that module
    CommandRecorder commandRecorder = new CommandRecorder();
    List<Command> commands = commandRecorder.recordCommands(module);

    // create a rewriter that rewrites the binding to 'Wine' with a binding to 'Beer'
    CommandReplayer rewriter = new CommandReplayer() {
      @Override public <T> void replayBind(Binder binder, BindCommand<T> command) {
        if ("Wine".equals(command.getTarget().get())) {
          binder.bind(CharSequence.class).toInstance("Beer");
        } else {
          super.replayBind(binder, command);
        }
      }
    };

    // create a module from the original list of commands and the rewriter
    Module rewrittenModule = rewriter.createModule(commands);

    // it all works
    Injector injector = Guice.createInjector(rewrittenModule);
    assertEquals("Pizza", injector.getInstance(String.class));
    assertEquals("Beer", injector.getInstance(CharSequence.class));
  }

  public void testGetProviderAvailableAtInjectMembersTime() {
    Module module = new AbstractModule() {
      public void configure() {
        final Provider<String> stringProvider = getProvider(String.class);

        bind(String.class).annotatedWith(Names.named("2")).toProvider(new Provider<String>() {
          private String value;

          @Inject void initialize() {
            value = stringProvider.get();
          }

          public String get() {
            return value;
          }
        });

        bind(String.class).toInstance("A");
      }
    };

    // the module works fine normally
    Injector injector = Guice.createInjector(module);
    assertEquals("A", injector.getInstance(Key.get(String.class, Names.named("2"))));

    // and it should also work fine if we rewrite it
    List<Command> commands = new CommandRecorder().recordCommands(module);
    Module replayed = new CommandReplayer().createModule(commands);
    Injector replayedInjector = Guice.createInjector(replayed);
    assertEquals("A", replayedInjector.getInstance(Key.get(String.class, Names.named("2"))));
  }
}
