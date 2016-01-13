// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.ideinfo;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.BuildView.AnalysisResult;
import com.google.devtools.build.lib.analysis.OutputGroupProvider;
import com.google.devtools.build.lib.analysis.actions.FileWriteAction;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo.RuleIdeInfo;
import com.google.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo.RuleIdeInfo.Builder;
import com.google.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo.RuleIdeInfo.Kind;
import com.google.devtools.build.lib.skyframe.AspectValue;
import com.google.protobuf.TextFormat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Tests for Skylark implementation of Android Studio info aspect
 */
@RunWith(JUnit4.class)
public class IntelliJSkylarkAspectTest extends BuildViewTestCase {
  @Before
  public void setupBzl() throws Exception {
    InputStream stream = IntelliJSkylarkAspectTest.class
        .getResourceAsStream("intellij_info.bzl");
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    String line;
    ArrayList<String> contents = new ArrayList<>();
    while ((line = reader.readLine()) != null) {
      contents.add(line);
    }

    scratch.file("intellij_tools/BUILD", "# empty");
    scratch.file("intellij_tools/intellij_info.bzl",
        contents.toArray(new String[contents.size()]));
  }

  @Test
  public void testSimple() throws Exception {
    scratch.file(
        "com/google/example/BUILD",
        "java_library(",
        "    name = 'simple',",
        "    srcs = ['simple/Simple.java']",
        ")");
    AnalysisResult analysisResult = update(
        ImmutableList.of("//com/google/example:simple"),
        ImmutableList.of("intellij_tools/intellij_info.bzl%intellij_info_aspect"),
        false,
        LOADING_PHASE_THREADS,
        true,
        new EventBus()
    );
    Collection<AspectValue> aspects = analysisResult.getAspects();
    assertThat(aspects).hasSize(1);
    AspectValue aspectValue = aspects.iterator().next();
    OutputGroupProvider provider = aspectValue.getConfiguredAspect()
        .getProvider(OutputGroupProvider.class);
    NestedSet<Artifact> outputGroup = provider.getOutputGroup("ide-info-text");
    assertThat(outputGroup.toList()).hasSize(1);
    for (Artifact artifact : outputGroup) {
      Action generatingAction = getGeneratingAction(artifact);
      assertThat(generatingAction).isInstanceOf(FileWriteAction.class);
      String fileContents = ((FileWriteAction) generatingAction).getFileContents();
      Builder builder = RuleIdeInfo.newBuilder();
      TextFormat.getParser().merge(fileContents, builder);
      RuleIdeInfo build = builder.build();
      assertThat(build.getLabel()).isEqualTo("//com/google/example:simple");
      assertThat(build.getKind()).isEqualTo(Kind.JAVA_LIBRARY);
    }

  }
}
