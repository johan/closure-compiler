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
 * Test for {@link UnreachableCodeElimination}.
 *
*
 */
public class UnreachableCodeEliminationTest extends CompilerTestCase {
  private boolean removeNoOpStatements = true;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new UnreachableCodeElimination(compiler, removeNoOpStatements);
  }

  @Override public void setUp() throws Exception {
    super.setUp();
    removeNoOpStatements = true;
  }

  public void testRemoveUnreachableCode() {
    // switch statement with stuff after "return"
    test("function foo(){switch(foo){case 1:x=1;return;break;" +
         "case 2:{x=2;return;break}default:}}",
         "function foo(){switch(foo){case 1:x=1;return;" +
         "case 2:{x=2;return}default:}}");

    // if/else statements with returns
    test("function bar(){if(foo)x=1;else if(bar){return;x=2}" +
         "else{x=3;return;x=4}return;x=5}",
         "function bar(){if(foo)x=1;else if(bar){return}" +
         "else{x=3;return}return}");

    // if statements without blocks
    test("function foo(){if(x==3)return;x=4;y++;while(y==4){return;x=3}}",
         "function foo(){if(x==3)return;x=4;y++;while(y==4){return}}");

    // for/do/while loops
    test("function baz(){for(i=0;i<n;i++){x=3;break;x=4}" +
         "do{x=2;break;x=4}while(x==4);" +
         "while(i<4){x=3;return;x=6}}",
         "function baz(){for(i=0;i<n;){x=3;break}" +
         "do{x=2;break}while(x==4);" +
         "while(i<4){x=3;return}}");

    // return statements on the same level as conditionals
    test("function foo(){if(x==3){return}return;while(y==4){x++;return;x=4}}",
         "function foo(){if(x==3){return}return}");

    // return statements on the same level as conditionals
    test("function foo(){return 3;for(;y==4;){x++;return;x=4}}",
         "function foo(){return 3}");

    // try/catch statements
    test("function foo(){try{x=3;return x+1;x=5}catch(e){x=4;return;x=5}}",
         "function foo(){try{x=3;return x+1}catch(e){x=4;return}}");

    // try/finally statements
    test("function foo(){try{x=3;return x+1;x=5}finally{x=4;return;x=5}}",
         "function foo(){try{x=3;return x+1}finally{x=4;return}}");

    // try/catch/finally statements
    test("function foo(){try{x=3;return x+1;x=5}catch(e){x=3;return;x=2}" +
         "finally{x=4;return;x=5}}",

         "function foo(){try{x=3;return x+1}catch(e){x=3;return}" +
         "finally{x=4;return}}");

    // test a combination of blocks
    test("function foo(){x=3;if(x==4){x=5;return;x=6}else{x=7}return;x=3}",
         "function foo(){x=3;if(x==4){x=5;return}else{x=7}return}");

    // test removing multiple statements
    test("function foo() { return 1; var x = 2; var y = 10; return 2;}",
         "function foo() { var y; var x; return 1}");

    test("function foo() { return 1; x = 2; y = 10; return 2;}",
         "function foo(){ return 1}");
  }

  public void testRemoveUselessNameStatements() {
    test("a;", "");
    test("a.b;", "");
    test("a.b.MyClass.prototype.memberName;", "");
  }

  public void testNoRemoveUselessNameStatements() {
    removeNoOpStatements = false;
    testSame("a;");
    testSame("a.b;");
    testSame("a.b.MyClass.prototype.memberName;");
  }

  public void testRemoveDo() {
    test("do { print(1); break } while(1)", "do { print(1); break } while(1)");
    test("while(1) { break; do { print(1); break } while(1) }",
         "while(1) { break; do {} while(1) }");
  }

  public void testRemoveUselessLiteralValueStatements() {
    test("true;", "");
    test("'hi';", "");
    test("if (x) 1;", "");
    test("while (x) 1;", "while (x);");
    test("do 1; while (x);", "do ; while (x);");
    test("for (;;) 1;", "for (;;);");
    test("switch(x){case 1:true;case 2:'hi';default:true}",
         "switch(x){case 1:case 2:default:}");
  }

  public void testConditionalDeadCode() {
    test("function f() { if (1) return; else return; x = 1}",
        "function f() { if (1) return; else return; }");
  }

  public void testSwitchCase() {
    test("function f() { switch(x) { default: return; foo()}}",
         "function f() { switch(x) { default: return;}}");
    test("function f() { switch(x) { default: return; case 1: foo(); bar()}}",
         "function f() { switch(x) { default: return; case 1: foo(); bar()}}");
    test("function f() { switch(x) { default: return; case 1: return; bar()}}",
         "function f() { switch(x) { default: return; case 1: return;}}");
  }

  public void testTryCatchFinally() {
    testSame("try {foo()} catch (e) {bar()}");
    testSame("try { try {foo()} catch (e) {bar()}} catch (x) {bar()}");
    test("try {var x = 1} catch (e) {e()}", "{var x = 1}");
    test("try {var x = 1} catch (e) {e()} finally {x()}",
        " try {var x = 1}                 finally {x()}");
    test("try {var x = 1} catch (e) {e()} finally {}",
        "     {var x = 1}");
    testSame("try {var x = 1} finally {x()}");
    test("try {var x = 1} finally {}", "{var x = 1}");
    test("function f() { return; try{var x = 1}catch(e){} }",
        "function f() { var x; return; {}}");
  }

  public void testRemovalRequiresRedeclaration() {
    test("while(1) { break; var x = 1}", "var x; while(1) { break } ");
    test("while(1) { break; var x=1; var y=1}",
        "var y; var x; while(1) { break } ");
  }
}
