/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.coders.protobuf;

import static com.google.cloud.dataflow.sdk.coders.protobuf.ProtobufUtil.checkProto2Syntax;
import static com.google.cloud.dataflow.sdk.coders.protobuf.ProtobufUtil.getRecursiveDescriptorsForClass;
import static com.google.cloud.dataflow.sdk.coders.protobuf.ProtobufUtil.verifyDeterministic;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import com.google.cloud.dataflow.sdk.coders.Coder.NonDeterministicException;
import com.google.cloud.dataflow.sdk.coders.Proto2CoderTestMessages;
import com.google.cloud.dataflow.sdk.coders.Proto2CoderTestMessages.MessageA;
import com.google.cloud.dataflow.sdk.coders.Proto2CoderTestMessages.MessageB;
import com.google.cloud.dataflow.sdk.coders.Proto2CoderTestMessages.MessageC;
import com.google.cloud.dataflow.sdk.coders.Proto2CoderTestMessages.MessageWithMap;
import com.google.cloud.dataflow.sdk.coders.Proto2CoderTestMessages.ReferencesMessageWithMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.Duration;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link ProtobufUtil}.
 */
@RunWith(JUnit4.class)
public class ProtobufUtilTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  private static final Set<String> MESSAGE_A_ONLY =
      ImmutableSet.of("proto2_coder_test_messages.MessageA");

  private static final Set<String> MESSAGE_B_ONLY =
      ImmutableSet.of("proto2_coder_test_messages.MessageB");

  private static final Set<String> MESSAGE_C_ONLY =
      ImmutableSet.of("proto2_coder_test_messages.MessageC");

  // map fields are actually represented as a nested Message in generated Java code.
  private static final Set<String> WITH_MAP_ONLY =
      ImmutableSet.of(
          "proto2_coder_test_messages.MessageWithMap",
          "proto2_coder_test_messages.MessageWithMap.Field1Entry");

  private static final Set<String> REFERS_MAP_ONLY =
      ImmutableSet.of("proto2_coder_test_messages.ReferencesMessageWithMap");

  // A references A and B.
  private static final Set<String> MESSAGE_A_ALL = Sets.union(MESSAGE_A_ONLY, MESSAGE_B_ONLY);

  // C, only with registered extensions, references A.
  private static final Set<String> MESSAGE_C_EXT = Sets.union(MESSAGE_C_ONLY, MESSAGE_A_ALL);

  // MessageWithMap references A.
  private static final Set<String> WITH_MAP_ALL = Sets.union(WITH_MAP_ONLY, MESSAGE_A_ALL);

  // ReferencesMessageWithMap references MessageWithMap.
  private static final Set<String> REFERS_MAP_ALL = Sets.union(REFERS_MAP_ONLY, WITH_MAP_ALL);

  @Test
  public void testRecursiveDescriptorsMessageA() {
    assertThat(getRecursiveDescriptorFullNames(MessageA.class), equalTo(MESSAGE_A_ALL));
  }

  @Test
  public void testRecursiveDescriptorsMessageB() {
    assertThat(getRecursiveDescriptorFullNames(MessageB.class), equalTo(MESSAGE_B_ONLY));
  }

  @Test
  public void testRecursiveDescriptorsMessageC() {
    assertThat(getRecursiveDescriptorFullNames(MessageC.class), equalTo(MESSAGE_C_ONLY));
  }

  @Test
  public void testRecursiveDescriptorsMessageCWithExtensions() {
    // With extensions, Message C has a reference to Message A and Message B.
    ExtensionRegistry registry = ExtensionRegistry.newInstance();
    Proto2CoderTestMessages.registerAllExtensions(registry);
    assertThat(getRecursiveDescriptorFullNames(MessageC.class, registry), equalTo(MESSAGE_C_EXT));
  }

  @Test
  public void testRecursiveDescriptorsMessageWithMap() {
    assertThat(getRecursiveDescriptorFullNames(MessageWithMap.class), equalTo(WITH_MAP_ALL));
  }

  @Test
  public void testRecursiveDescriptorsReferencesMessageWithMap() {
    assertThat(
        getRecursiveDescriptorFullNames(ReferencesMessageWithMap.class), equalTo(REFERS_MAP_ALL));
  }

  @Test
  public void testVerifyProto2() {
    // Everything in Dataflow's Proto2TestMessages uses Proto2 syntax.
    checkProto2Syntax(MessageA.class, ExtensionRegistry.getEmptyRegistry());
    checkProto2Syntax(MessageB.class, ExtensionRegistry.getEmptyRegistry());
    checkProto2Syntax(MessageC.class, ExtensionRegistry.getEmptyRegistry());
    checkProto2Syntax(MessageWithMap.class, ExtensionRegistry.getEmptyRegistry());
    checkProto2Syntax(ReferencesMessageWithMap.class, ExtensionRegistry.getEmptyRegistry());
  }

  @Test
  public void testAnyIsNotProto2() {
    // Any is a core Protocol Buffers type that uses proto3 syntax.
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(Any.class.getCanonicalName());
    thrown.expectMessage("in file " + Any.getDescriptor().getFile().getName());

    checkProto2Syntax(Any.class, ExtensionRegistry.getEmptyRegistry());
  }

  @Test
  public void testDurationIsNotProto2() {
    // Duration is a core Protocol Buffers type that uses proto3 syntax.
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(Duration.class.getCanonicalName());
    thrown.expectMessage("in file " + Duration.getDescriptor().getFile().getName());

    checkProto2Syntax(Duration.class, ExtensionRegistry.getEmptyRegistry());
  }

  @Test
  public void testDurationIsDeterministic() throws NonDeterministicException {
    // Duration can be encoded deterministically.
    verifyDeterministic(ProtoCoder.of(Duration.class));
  }

  @Test
  public void testMessageWithMapIsNotDeterministic() throws NonDeterministicException {
    String mapFieldName = MessageWithMap.getDescriptor().findFieldByNumber(1).getFullName();
    thrown.expect(NonDeterministicException.class);
    thrown.expectMessage(MessageWithMap.class.getName());
    thrown.expectMessage("transitively includes Map field " + mapFieldName);
    thrown.expectMessage("file " + MessageWithMap.getDescriptor().getFile().getName());

    verifyDeterministic(ProtoCoder.of(MessageWithMap.class));
  }

  @Test
  public void testMessageWithTransitiveMapIsNotDeterministic() throws NonDeterministicException {
    String mapFieldName = MessageWithMap.getDescriptor().findFieldByNumber(1).getFullName();
    thrown.expect(NonDeterministicException.class);
    thrown.expectMessage(ReferencesMessageWithMap.class.getName());
    thrown.expectMessage("transitively includes Map field " + mapFieldName);
    thrown.expectMessage("file " + MessageWithMap.getDescriptor().getFile().getName());

    verifyDeterministic(ProtoCoder.of(ReferencesMessageWithMap.class));
  }

  ////////////////////////////////////////////////////////////////////////////////////////////

  /** Helper used to test the recursive class traversal and print good error messages. */
  private static Set<String> getRecursiveDescriptorFullNames(Class<? extends Message> clazz) {
    return getRecursiveDescriptorFullNames(clazz, ExtensionRegistry.getEmptyRegistry());
  }

  /** Helper used to test the recursive class traversal and print good error messages. */
  private static Set<String> getRecursiveDescriptorFullNames(
      Class<? extends Message> clazz, ExtensionRegistry registry) {
    Set<String> result = new HashSet<>();
    for (GenericDescriptor d : getRecursiveDescriptorsForClass(clazz, registry)) {
      result.add(d.getFullName());
    }
    return result;
  }
}
