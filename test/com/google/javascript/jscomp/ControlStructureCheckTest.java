/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

/**
 * Test for the control structure verification.
 *
*
 */
public class ControlStructureCheckTest extends CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new ControlStructureCheck(compiler);
  }

  public void testWhile() {
    assertNoError("while(1) { break; }");
  }

  public void testNextedWhile() {
    assertNoError("while(1) { while(1) { break; } }");
  }

  public void testBreak() {
    assertInvalidBreak("break;");
  }

  public void testContinue() {
    assertInvalidContinue("continue;");
  }

  public void testBreakCrossFunction() {
    assertInvalidBreak("while(1) { function f() { break; } }");
  }

  public void testBreakCrossFunctionInFor() {
    assertInvalidBreak("while(1) {for(var f = function () { break; };;) {}}");
  }

  public void testContinueToSwitch() {
    assertInvalidContinue("switch(1) {case(1): continue; }");
  }

  public void testContinueToSwitchWithNoCases() {
    assertNoError("switch(1){}");
  }

  public void testContinueToSwitchWithTwoCases() {
    assertInvalidContinue("switch(1){case(1):break;case(2):continue;}");
  }

  public void testContinueToSwitchWithDefault() {
    assertInvalidContinue("switch(1){case(1):break;case(2):default:continue;}");
  }

  public void testContinueToLabelSwitch() {
    assertInvalidLabelContinue(
        "while(1) {a: switch(1) {case(1): continue a; }}");
  }

  public void testContinueOutsideSwitch() {
    assertNoError("b: while(1) { a: switch(1) { case(1): continue b; } }");
  }

  public void testContinueNotCrossFunction() {
    assertNoError("a:switch(1){case(1):function f(){a:while(1){continue a;}}}");
  }

  private void assertNoError(String js) {
    testSame(js);
  }

  private void assertInvalidBreak(String js) {
    assertSomeError(js, ControlStructureCheck.INVALID_BREAK);
  }

  private void assertInvalidContinue(String js) {
    assertSomeError(js, ControlStructureCheck.INVALID_CONTINUE);
  }

  private void assertInvalidLabelContinue(String js) {
    assertSomeError(js, ControlStructureCheck.INVALID_LABEL_CONTINUE);
  }

  /**
   * Tests that either a parse error or the given error is triggered.
   * The new parser is stricter with control structure checks, so it will catch
   * some of these at parse time.
   */
  private void assertSomeError(String js, DiagnosticType error) {
    if (!hasParseError(js)) {
      test(js, js, error);
    }
  }

  private boolean hasParseError(String js) {
    Compiler compiler = new Compiler();
    compiler.parseTestCode(js);
    return compiler.getErrorCount() > 0;
  }
}
