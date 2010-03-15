/*
 * Copyright 2004 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.List;

public class CodePrinterTest extends TestCase {
  static Node parse(String js) {
    return parse(js, false);
  }

  static Node parse(String js, boolean checkTypes) {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode(js);

    if (checkTypes) {
      CompilerPass typeResolver = new DefaultPassConfig(null)
          .resolveTypes.create(compiler);
      Node externs = new Node(Token.SCRIPT);
      Node externAndJsRoot = new Node(Token.BLOCK, externs, n);
      externAndJsRoot.setIsSyntheticBlock(true);
      typeResolver.process(externs, n);
    }

    assertEquals("Errors for: " + js, 0, compiler.getErrorCount());
    return n;
  }

  String parsePrint(String js, boolean prettyprint, int lineThreshold) {
    return new CodePrinter.Builder(parse(js)).setPrettyPrint(prettyprint)
        .setLineLengthThreshold(lineThreshold).build();
  }

  String parsePrint(String js, boolean prettyprint, boolean lineBreak,
      int lineThreshold) {
    return new CodePrinter.Builder(parse(js)).setPrettyPrint(prettyprint)
        .setLineLengthThreshold(lineThreshold).setLineBreak(lineBreak).build();
  }

  String parsePrint(String js, boolean prettyprint, boolean lineBreak,
      int lineThreshold, boolean outputTypes) {
    return new CodePrinter.Builder(parse(js, true)).setPrettyPrint(prettyprint)
        .setOutputTypes(outputTypes)
        .setLineLengthThreshold(lineThreshold).setLineBreak(lineBreak).build();
  }

  String printNode(Node n) {
    return new CodePrinter.Builder(n).setLineLengthThreshold(
        CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD).build();
  }

  void assertPrintNode(String expectedJs, Node ast) {
    assertEquals(expectedJs, printNode(ast));
  }

  public void testPrint() {
    assertPrint("10 + a + b", "10+a+b");
    assertPrint("10 + (30*50)", "10+30*50");
    assertPrint("with(x) { x + 3; }", "with(x)x+3");
    assertPrint("\"aa'a\"", "\"aa'a\"");
    assertPrint("\"aa\\\"a\"", "'aa\"a'");
    assertPrint("function foo()\n{return 10;}", "function foo(){return 10}");
    assertPrint("a instanceof b", "a instanceof b");
    assertPrint("typeof(a)", "typeof a");
    assertPrint(
        "var foo = x ? { a : 1 } : {a: 3, b:4, \"default\": 5, \"foo-bar\": 6}",
        "var foo=x?{a:1}:{a:3,b:4,\"default\":5,\"foo-bar\":6}");

    // Safari: needs ';' at the end of a throw statement
    assertPrint("function foo(){throw 'error';}",
        "function foo(){throw\"error\";}");
    // Safari 3 needs a "{" around a single function
    assertPrint("if (true) function foo(){return}",
        "if(true){function foo(){return}}");

    assertPrint("var x = 10; { var y = 20; }", "var x=10;var y=20");

    assertPrint("while (x-- > 0);", "while(x-- >0);");
    assertPrint("x-- >> 1", "x-- >>1");

    assertPrint("(function () {})(); ",
        "(function(){})()");

    // Associativity
    assertPrint("var a,b,c,d;a || (b&& c) && (a || d)",
        "var a,b,c,d;a||b&&c&&(a||d)");
    assertPrint("var a,b,c; a || (b || c); a * (b * c); a | (b | c)",
        "var a,b,c;a||b||c;a*b*c;a|b|c");
    assertPrint("var a,b,c; a / b / c;a / (b / c); a - (b - c);",
        "var a,b,c;a/b/c;a/(b/c);a-(b-c)");
    assertPrint("var a,b; a = b = 3;",
        "var a,b;a=b=3");
    assertPrint("var a,b,c,d; a = (b = c = (d = 3));",
        "var a,b,c,d;a=b=c=d=3");
    assertPrint("var a,b,c; a += (b = c += 3);",
        "var a,b,c;a+=b=c+=3");
    assertPrint("var a,b,c; a *= (b -= c);",
        "var a,b,c;a*=b-=c");

    // Break scripts
    assertPrint("'<script>'", "\"<script>\"");
    assertPrint("'</script>'", "\"<\\/script>\"");
    assertPrint("\"</script> </SCRIPT>\"", "\"<\\/script> <\\/SCRIPT>\"");

    assertPrint("'-->'", "\"--\\>\"");
    assertPrint("']]>'", "\"]]\\>\"");
    assertPrint("' --></script>'", "\" --\\><\\/script>\"");

    assertPrint("/--> <\\/script>/g", "/--\\> <\\/script>/g");

    // Precedence
    assertPrint("a ? delete b[0] : 3", "a?delete b[0]:3");
    assertPrint("(delete a[0])/10", "delete a[0]/10");

    // optional '()' for new

    // simple new
    assertPrint("new A", "new A");
    assertPrint("new A()", "new A");
    assertPrint("new A('x')", "new A(\"x\")");

    // calling instance method directly after new
    assertPrint("new A().a()", "(new A).a()");
    assertPrint("(new A).a()", "(new A).a()");

    // this case should be fixed
    assertPrint("new A('y').a()", "(new A(\"y\")).a()");

    // internal class
    assertPrint("new A.B", "new A.B");
    assertPrint("new A.B()", "new A.B");
    assertPrint("new A.B('z')", "new A.B(\"z\")");

    // calling instance method directly after new internal class
    assertPrint("(new A.B).a()", "(new A.B).a()");
    assertPrint("new A.B().a()", "(new A.B).a()");
    // this case should be fixed
    assertPrint("new A.B('w').a()", "(new A.B(\"w\")).a()");

    // Operators: make sure we don't convert binary + and unary + into ++
    assertPrint("x + +y", "x+ +y");
    assertPrint("x - (-y)", "x- -y");
    assertPrint("x++ +y", "x++ +y");
    assertPrint("x-- -y", "x-- -y");
    assertPrint("x++ -y", "x++-y");

    // Label
    assertPrint("foo:for(;;){break foo;}", "foo:for(;;)break foo");
    assertPrint("foo:while(1){continue foo;}", "foo:while(1)continue foo");

    // Object literals.
    assertPrint("({})", "({})");
    assertPrint("var x = {};", "var x={}");
    assertPrint("({}).x", "({}).x");
    assertPrint("({})['x']", "({})[\"x\"]");
    assertPrint("({}) instanceof Object", "({})instanceof Object");
    assertPrint("({}) || 1", "({})||1");
    assertPrint("1 || ({})", "1||{}");
    assertPrint("({}) ? 1 : 2", "({})?1:2");
    assertPrint("0 ? ({}) : 2", "0?{}:2");
    assertPrint("0 ? 1 : ({})", "0?1:{}");
    assertPrint("typeof ({})", "typeof{}");
    assertPrint("f({})", "f({})");

    // Anonymous functions.
    assertPrint("(function(){})", "(function(){})");
    assertPrint("(function(){})()", "(function(){})()");
    assertPrint("(function(){})instanceof Object",
        "(function(){})instanceof Object");
    assertPrint("(function(){}).bind().call()",
        "(function(){}).bind().call()");
    assertPrint("var x = function() { };", "var x=function(){}");
    assertPrint("var x = function() { }();", "var x=function(){}()");
    assertPrint("(function() {}), 2", "(function(){}),2");

    // Named functions
    assertPrint("(function f(){})", "(function f(){})");
    assertPrint("function f(){}", "function f(){}");

    // Make sure we don't treat non-latin character escapes as raw strings.
    assertPrint("({ 'a': 4, '\\u0100': 4 })", "({a:4,\"\\u0100\":4})");

    // Test if statement and for statements with single statements in body.
    assertPrint("if (true) { alert();}", "if(true)alert()");
    assertPrint("if (false) {} else {alert(\"a\");}",
        "if(false);else alert(\"a\")");
    assertPrint("for(;;) { alert();};", "for(;;)alert()");

    assertPrint("do { alert(); } while(true);",
        "do alert();while(true)");
    assertPrint("myLabel: { alert();}",
        "myLabel:alert()");
    assertPrint("myLabel: for(;;) continue myLabel;",
        "myLabel:for(;;)continue myLabel");

    // Test nested var statement
    assertPrint("if (true) var x; x = 4;", "if(true)var x;x=4");

    // Non-latin identifier. Make sure we keep them escaped.
    assertPrint("\\u00fb", "\\u00fb");
    assertPrint("\\u00fa=1", "\\u00fa=1");
    assertPrint("function \\u00f9(){}", "function \\u00f9(){}");
    assertPrint("x.\\u00f8", "x.\\u00f8");
    assertPrint("x.\\u00f8", "x.\\u00f8");
    assertPrint("abc\\u4e00\\u4e01jkl", "abc\\u4e00\\u4e01jkl");

    // Test the right-associative unary operators for spurious parens
    assertPrint("! ! true", "!!true");
    assertPrint("!(!(true))", "!!true");
    assertPrint("typeof(void(0))", "typeof void 0");
    assertPrint("typeof(void(!0))", "typeof void!0");
    assertPrint("+ - + + - + 3", "+-+ +-+3"); // chained unary plus/minus
    assertPrint("+(--x)", "+--x");
    assertPrint("-(++x)", "-++x");

    // needs a space to prevent an ambiguous parse
    assertPrint("-(--x)", "- --x");
    assertPrint("!(~~5)", "!~~5");
    assertPrint("~(a/b)", "~(a/b)");

    // Preserve parens to overcome greedy binding of NEW
    assertPrint("new (foo.bar()).factory(baz)", "new (foo.bar().factory)(baz)");
    assertPrint("new (bar()).factory(baz)", "new (bar().factory)(baz)");
    assertPrint("new (new foobar(x)).factory(baz)",
        "new (new foobar(x)).factory(baz)");

    // Make sure that HOOK is right associative
    assertPrint("a ? b : (c ? d : e)", "a?b:c?d:e");
    assertPrint("a ? (b ? c : d) : e", "a?b?c:d:e");
    assertPrint("(a ? b : c) ? d : e", "(a?b:c)?d:e");

    // Test nested ifs
    assertPrint("if (x) if (y); else;", "if(x)if(y);else;");

    // Test comma.
    assertPrint("a,b,c", "a,b,c");
    assertPrint("(a,b),c", "a,b,c");
    assertPrint("a,(b,c)", "a,b,c");
    assertPrint("x=a,b,c", "x=a,b,c");
    assertPrint("x=(a,b),c", "x=(a,b),c");
    assertPrint("x=a,(b,c)", "x=a,b,c");
    assertPrint("x=a,y=b,z=c", "x=a,y=b,z=c");
    assertPrint("x=(a,y=b,z=c)", "x=(a,y=b,z=c)");
    assertPrint("x=[a,b,c,d]", "x=[a,b,c,d]");
    assertPrint("x=[(a,b,c),d]", "x=[(a,b,c),d]");
    assertPrint("x=[(a,(b,c)),d]", "x=[(a,b,c),d]");
    assertPrint("x=[a,(b,c,d)]", "x=[a,(b,c,d)]");
    assertPrint("var x=(a,b)", "var x=(a,b)");
    assertPrint("var x=a,b,c", "var x=a,b,c");
    assertPrint("var x=(a,b),c", "var x=(a,b),c");
    assertPrint("var x=a,b=(c,d)", "var x=a,b=(c,d)");
    assertPrint("foo(a,b,c,d)", "foo(a,b,c,d)");
    assertPrint("foo((a,b,c),d)", "foo((a,b,c),d)");
    assertPrint("foo((a,(b,c)),d)", "foo((a,b,c),d)");
    assertPrint("f(a+b,(c,d,(e,f,g)))", "f(a+b,(c,d,e,f,g))");
    assertPrint("({}) , 1 , 2", "({}),1,2");
    assertPrint("({}) , {} , {}", "({}),{},{}");

    // EMPTY nodes
    assertPrint("if (x){}", "if(x);");
    assertPrint("if(x);", "if(x);");
    assertPrint("if(x)if(y);", "if(x)if(y);");
    assertPrint("if(x){if(y);}", "if(x)if(y);");
    assertPrint("if(x){if(y){};;;}", "if(x)if(y);");
    assertPrint("if(x){;;function y(){};;}", "if(x){function y(){}}");
  }

  public void testPrintInOperatorInForLoop() {
    // Check for in expression in for's init expression.
    // Check alone, with + (higher precedence), with ?: (lower precedence),
    // and with conditional.
    assertPrint("var a={}; for (var i = (\"length\" in a); i;) {}",
        "var a={};for(var i=(\"length\"in a);i;);");
    assertPrint("var a={}; for (var i = (\"length\" in a) ? 0 : 1; i;) {}",
        "var a={};for(var i=(\"length\"in a)?0:1;i;);");
    assertPrint("var a={}; for (var i = (\"length\" in a) + 1; i;) {}",
        "var a={};for(var i=(\"length\"in a)+1;i;);");
    assertPrint("var a={};for (var i = (\"length\" in a|| \"size\" in a);;);",
        "var a={};for(var i=(\"length\"in a)||(\"size\"in a);;);");
    assertPrint("var a={};for (var i = a || a || (\"size\" in a);;);",
        "var a={};for(var i=a||a||(\"size\"in a);;);");

    // Test works with unary operators and calls.
    assertPrint("var a={}; for (var i = -(\"length\" in a); i;) {}",
        "var a={};for(var i=-(\"length\"in a);i;);");
    assertPrint("var a={};function b_(p){ return p;};" +
        "for(var i=1,j=b_(\"length\" in a);;) {}",
        "var a={};function b_(p){return p}" +
            "for(var i=1,j=b_(\"length\"in a);;);");

    // Test we correctly handle an in operator in the test clause.
    assertPrint("var a={}; for (;(\"length\" in a);) {}",
        "var a={};for(;\"length\"in a;);");
  }

  public void testLiteralProperty() {
    assertPrint("(64).toString()", "(64).toString()");
  }

  private void assertPrint(String js, String expected) {
    assertEquals(expected,
        parsePrint(js, false, CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD));
  }

  // Make sure that the code generator doesn't associate an
  // else clause with the wrong if clause.
  public void testAmbiguousElseClauses() {
    assertPrintNode("if(x)if(y);else;",
        new Node(Token.IF,
            Node.newString(Token.NAME, "x"),
            new Node(Token.BLOCK,
                new Node(Token.IF,
                    Node.newString(Token.NAME, "y"),
                    new Node(Token.BLOCK),

                    // ELSE clause for the inner if
                    new Node(Token.BLOCK)))));

    assertPrintNode("if(x){if(y);}else;",
        new Node(Token.IF,
            Node.newString(Token.NAME, "x"),
            new Node(Token.BLOCK,
                new Node(Token.IF,
                    Node.newString(Token.NAME, "y"),
                    new Node(Token.BLOCK))),

            // ELSE clause for the outer if
            new Node(Token.BLOCK)));

    assertPrintNode("if(x)if(y);else{if(z);}else;",
        new Node(Token.IF,
            Node.newString(Token.NAME, "x"),
            new Node(Token.BLOCK,
                new Node(Token.IF,
                    Node.newString(Token.NAME, "y"),
                    new Node(Token.BLOCK),
                    new Node(Token.BLOCK,
                        new Node(Token.IF,
                            Node.newString(Token.NAME, "z"),
                            new Node(Token.BLOCK))))),

            // ELSE clause for the outermost if
            new Node(Token.BLOCK)));
  }

  public void testLineBreak() {
    // line break after function if in a statement context
    assertLineBreak("function a() {}\n" +
        "function b() {}",
        "function a(){}\n" +
        "function b(){}\n");

    // line break after ; after a function
    assertLineBreak("var a = {};\n" +
        "a.foo = function () {}\n" +
        "function b() {}",
        "var a={};a.foo=function(){};\n" +
        "function b(){}\n");

    // break after comma after a function
    assertLineBreak("var a = {\n" +
        "  b: function() {},\n" +
        "  c: function() {}\n" +
        "};\n" +
        "alert(a);",

        "var a={b:function(){},\n" +
        "c:function(){}};\n" +
        "alert(a)");
  }

  private void assertLineBreak(String js, String expected) {
    assertEquals(expected,
        parsePrint(js, false, true,
            CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD));
  }


  public void testPrettyPrinter() {
    // Ensure that the pretty printer inserts line breaks at appropriate
    // places.
    assertPrettyPrint("(function(){})();","(function() {\n})()");
    assertPrettyPrint("var a = (function() {});alert(a);",
        "var a = function() {\n};\nalert(a)");

    // Check we correctly handle putting brackets around all if clauses so
    // we can put breakpoints inside statements.
    assertPrettyPrint("if (1) {}",
        "if(1);");
    assertPrettyPrint("if (1) {alert(\"\");}",
        "if(1) {\n" +
        "  alert(\"\")\n" +
        "}");
    assertPrettyPrint("if (1)alert(\"\");",
        "if(1) {\n" +
        "  alert(\"\")\n" +
        "}");
    assertPrettyPrint("if (1) {alert();alert();}",
        "if(1) {\n" +
        "  alert();\n" +
        "  alert()\n" +
        "}");

    // Don't add blocks if they weren't there already.
    assertPrettyPrint("label: alert();",
        "label:alert()");

    // But if statements and loops get blocks automagically.
    assertPrettyPrint("if (1) alert();",
        "if(1) {\n" +
        "  alert()\n" +
        "}");
    assertPrettyPrint("for (;;) alert();",
        "for(;;) {\n" +
        "  alert()\n" +
        "}");

    assertPrettyPrint("while (1) alert();",
        "while(1) {\n" +
        "  alert()\n" +
        "}");

    // Do we put else clauses in blocks?
    assertPrettyPrint("if (1) {} else {alert(a);}",
        "if(1);else {\n  alert(a)\n}");

    // Do we add blocks to else clauses?
    assertPrettyPrint("if (1) alert(a); else alert(b);",
        "if(1) {\n" +
        "  alert(a)\n" +
        "}else {\n" +
        "  alert(b)\n" +
        "}");

    // Do we put for bodies in blocks?
    assertPrettyPrint("for(;;) { alert();}",
        "for(;;) {\n" +
         "  alert()\n" +
         "}");
    assertPrettyPrint("for(;;) {}",
        "for(;;);");
    assertPrettyPrint("for(;;) { alert(); alert(); }",
        "for(;;) {\n" +
        "  alert();\n" +
        "  alert()\n" +
        "}");

    // How about do loops?
    assertPrettyPrint("do { alert(); } while(true);",
        "do {\n" +
        "  alert()\n" +
        "}while(true)");

    // label?
    assertPrettyPrint("myLabel: { alert();}",
        "myLabel: {\n" +
        "  alert()\n" +
        "}");

    // Don't move the label on a loop, because then break {label} and
    // continue {label} won't work.
    assertPrettyPrint("myLabel: for(;;) continue myLabel;",
        "myLabel:for(;;) {\n" +
        "  continue myLabel\n" +
        "}");
  }

  public void testTypeAnnotations() {
    assertTypeAnnotations("/** @constructor */ function Foo(){}",
        "/**\n * @constructor\n */\nfunction Foo() {\n}\n");
  }

  public void testTypeAnnotationsAssign() {
    assertTypeAnnotations("/** @constructor */ var Foo = function(){}",
        "/**\n * @constructor\n */\nvar Foo = function() {\n}");
  }

  public void testTypeAnnotationsNamespace() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){}",
        "var a = {};\n/**\n * @constructor\n */\na.Foo = function() {\n}");
  }

  public void testTypeAnnotationsMemberSubclass() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){};"
        + "/** @constructor \n @extends {a.Foo} */ a.Bar = function(){}",
        "var a = {};\n/**\n * @constructor\n */\na.Foo = function() {\n};\n"
        + "/**\n * @extends {a.Foo}\n * @constructor\n */\n"
        + "a.Bar = function() {\n}");
  }

  public void testTypeAnnotationsInterface() {
    assertTypeAnnotations("var a = {};"
        + "/** @interface */ a.Foo = function(){};"
        + "/** @interface \n @extends {a.Foo} */ a.Bar = function(){}",
        "var a = {};\n/**\n * @interface\n */\na.Foo = function() {\n};\n"
        + "/**\n * @extends {a.Foo}\n * @interface\n */\n"
        + "a.Bar = function() {\n}");
  }

  public void testTypeAnnotationsMember() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){}"
        + "/** @param {string} foo\n"
        + "  * @return {number} */\n"
        + "a.Foo.prototype.foo = function(foo) {};"
        + "/** @type {string|undefined} */"
        + "a.Foo.prototype.bar = '';",
        "var a = {};\n"
        + "/**\n * @constructor\n */\na.Foo = function() {\n};\n"
        + "/**\n"
        + " * @param {string} foo\n"
        + " * @return {number}\n"
        + " */\n"
        + "a.Foo.prototype.foo = function(foo) {\n};\n"
        + "/** @type {(string|undefined)} */\n"
        + "a.Foo.prototype.bar = \"\"");
  }

  public void testTypeAnnotationsImplements() {
    assertTypeAnnotations("var a = {};"
        + "/** @constructor */ a.Foo = function(){};\n"
        + "/** @interface */ a.I = function(){};\n"
        + "/** @interface */ a.I2 = function(){};\n"
        + "/** @constructor \n @extends {a.Foo}\n"
        + " * @implements {a.I} \n @implements {a.I2}\n"
        + "*/ a.Bar = function(){}",
        "var a = {};\n"
        + "/**\n * @constructor\n */\na.Foo = function() {\n};\n"
        + "/**\n * @interface\n */\na.I = function() {\n};\n"
        + "/**\n * @interface\n */\na.I2 = function() {\n};\n"
        + "/**\n * @extends {a.Foo}\n * @implements {a.I}\n"
        + " * @implements {a.I2}\n * @constructor\n */\n"
        + "a.Bar = function() {\n}");
  }

  public void testTypeAnnotationsDispatcher1() {
    assertTypeAnnotations(
        "var a = {};\n" +
        "/** \n" +
        " * @constructor \n" +
        " * @javadispatch \n" +
        " */\n" +
        "a.Foo = function(){}",
        "var a = {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @javadispatch\n" +
        " */\n" +
        "a.Foo = function() {\n" +
        "}");
  }

  public void testTypeAnnotationsDispatcher2() {
    assertTypeAnnotations(
        "var a = {};\n" +
        "/** \n" +
        " * @constructor \n" +
        " */\n" +
        "a.Foo = function(){}\n" +
        "/**\n" +
        " * @javadispatch\n" +
        " */\n" +
        "a.Foo.prototype.foo = function() {};",

        "var a = {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " */\n" +
        "a.Foo = function() {\n" +
        "};\n" +
        "/**\n" +
        " * @javadispatch\n" +
        " */\n" +
        "a.Foo.prototype.foo = function() {\n" +
        "}");
  }
  
  public void testU2UFunctionTypeAnnotation() {
    assertTypeAnnotations(
        "/** @type {!Function} */ var x = function() {}",
        "/**\n * @constructor\n */\nvar x = function() {\n}");
  }

  private void assertPrettyPrint(String js, String expected) {
    assertEquals(expected,
        parsePrint(js, true, false,
            CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD));
  }

  private void assertTypeAnnotations(String js, String expected) {
    assertEquals(expected,
        parsePrint(js, true, false,
            CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD, true));
  }

  /**
   * This test case is more involved since we need to run a constant folding
   * pass to get the -4 converted to a negative number, as opposed to a
   * number node with a number 4 attached to the negation unary operator.
   */
  public void testSubtraction() {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode("x - -4");
    assertEquals(0, compiler.getErrorCount());
    NodeTraversal.traverse(compiler, n, new FoldConstants(compiler));

    assertEquals(
        "x- -4",
        new CodePrinter.Builder(n).setLineLengthThreshold(
            CodePrinter.DEFAULT_LINE_LENGTH_THRESHOLD).build());
  }

  public void testLineLength() {
    // list
    assertLineLength("var aba,bcb,cdc",
        "var aba,bcb," +
        "\ncdc");

    // operators, and two breaks
    assertLineLength(
        "\"foo\"+\"bar,baz,bomb\"+\"whee\"+\";long-string\"\n+\"aaa\"",
        "\"foo\"+\"bar,baz,bomb\"+" +
        "\n\"whee\"+\";long-string\"+" +
        "\n\"aaa\"");

    // assignment
    assertLineLength("var abazaba=1234",
        "var abazaba=" +
        "\n1234");

    // statements
    assertLineLength("var abab=1;var bab=2",
        "var abab=1;" +
        "\nvar bab=2");

    // don't break regexes
    assertLineLength("var a=/some[reg](ex),with.*we?rd|chars/i;var b=a",
        "var a=/some[reg](ex),with.*we?rd|chars/i;" +
        "\nvar b=a");

    // don't break strings
    assertLineLength("var a=\"foo,{bar};baz\";var b=a",
        "var a=\"foo,{bar};baz\";" +
        "\nvar b=a");

    // don't break before post inc/dec
    assertLineLength("var a=\"a\";a++;var b=\"bbb\";",
        "var a=\"a\";a++;\n" +
        "var b=\"bbb\"");
  }

  private void assertLineLength(String js, String expected) {
    assertEquals(expected,
        parsePrint(js, false, true, 10));
  }

  public void testParsePrintParse() {
    List<String> parsePrintParseTestCases = ImmutableList.of(
        "3;",
        "var a = b;",
        "var x, y, z;",
        "try { foo() } catch(e) { bar() }",
        "try { foo() } catch(e) { bar() } finally { stuff() }",
        "try { foo() } finally { stuff() }",
        "throw 'me'",
        "function foo(a) { return a + 4; }",
        "function foo() { return; }",
        "var a = function(a, b) { foo(); return a + b; }",
        "b = [3, 4, 'paul', \"Buchhe it\",,5];",
        "v = (5, 6, 7, 8)",
        "d = 34.0; x = 0; y = .3; z = -22",
        "d = -x; t = !x + ~y;",
        "'hi'; /* just a test */ stuff(a,b) \n foo(); // and another \n bar();",
        "a = b++ + ++c; a = b++-++c; a = - --b; a = - ++b;",
        "a++; b= a++; b = ++a; b = a--; b = --a; a+=2; b-=5",
        "a = (2 + 3) * 4;",
        "a = 1 + (2 + 3) + 4;",
        "x = a ? b : c; x = a ? (b,3,5) : (foo(),bar());",
        "a = b | c || d ^ e && f & !g != h << i <= j < k >>> l > m * n % !o",
        "a == b; a != b; a === b; a == b == a; (a == b) == a; a == (b == a);",
        "if (a > b) a = b; if (b < 3) a = 3; else c = 4;",
        "if (a == b) { a++; } if (a == 0) { a++; } else { a --; }",
        "for (var i in a) b += i;",
        "for (var i = 0; i < 10; i++){ b /= 2; if (b == 2)break;else continue;}",
        "for (x = 0; x < 10; x++) a /= 2;",
        "for (;;) a++;",
        "while(true) { blah(); }while(true) blah();",
        "do stuff(); while(a>b);",
        "[0, null, , true, false, this];",
        "s.replace(/absc/, 'X').replace(/ab/gi, 'Y');",
        "new Foo; new Bar(a, b,c);",
        "with(foo()) { x = z; y = t; } with(bar()) a = z;",
        "delete foo['bar']; delete foo;",
        "var x = { 'a':'paul', 1:'3', 2:(3,4) };",
        "switch(a) { case 2: case 3: { stuff(); break; }" +
        "case 4: morestuff(); break; default: done();}",
        "x = foo['bar'] + foo['my stuff'] + foo[bar] + f.stuff;",
        "a.v = b.v; x['foo'] = y['zoo'];",
        "'test' in x; 3 in x; a in x;",
        "'foo\"bar' + \"foo'c\" + 'stuff\\n and \\\\more'",
        "x.__proto__;");

    for (String testCase : parsePrintParseTestCases) {
      Node parse1 = parse(testCase);
      Node parse2 = parse(new CodePrinter.Builder(parse1).build());
      assertTrue(testCase, parse1.checkTreeEqualsSilent(parse2));
    }
  }

  public void testDoLoopIECompatiblity() {
    // Do loops within IFs cause syntax errors in IE6 and IE7.
    assertPrint("function(){if(e1){do foo();while(e2)}else foo()}",
        "function(){if(e1){do foo();while(e2)}else foo()}");

    assertPrint("function(){if(e1)do foo();while(e2)else foo()}",
        "function(){if(e1){do foo();while(e2)}else foo()}");

    assertPrint("if(x){do{foo()}while(y)}else bar()",
        "if(x){do foo();while(y)}else bar()");

    assertPrint("if(x)do{foo()}while(y);else bar()",
        "if(x){do foo();while(y)}else bar()");
  }

  public void testFunctionSafariCompatiblity() {
    // Do loops within IFs cause syntax errors in IE6 and IE7.
    assertPrint("function(){if(e1){function goo(){return true}}else foo()}",
        "function(){if(e1){function goo(){return true}}else foo()}");

    assertPrint("function(){if(e1)function goo(){return true}else foo()}",
        "function(){if(e1){function goo(){return true}}else foo()}");

    assertPrint("if(e1){function goo(){return true}}",
        "if(e1){function goo(){return true}}");

    assertPrint("if(e1)function goo(){return true}",
        "if(e1){function goo(){return true}}");
  }

  public void testExponents() {
    assertPrint("1", "1");
    assertPrint("10", "10");
    assertPrint("100", "100");
    assertPrint("1000", "1E3");
    assertPrint("10000", "1E4");
    assertPrint("100000", "1E5");
    assertPrint("-1", "-1");
    assertPrint("-10", "-10");
    assertPrint("-100", "-100");
    assertPrint("-1000", "-1E3");
    assertPrint("-123412340000", "-12341234E4");
    assertPrint("1000000000000000000", "1E18");
    assertPrint("100000.0", "1E5");
    assertPrint("100000.1", "100000.1");

    assertPrint("0.000001", "1.0E-6");
  }

  public void testDirectEval() {
    assertPrint("eval('1');", "eval(\"1\")");
  }

  public void testIndirectEval() {
    Node n = parse("eval('1');");
    assertPrintNode("eval(\"1\")", n);
    n.getFirstChild().getFirstChild().getFirstChild().putBooleanProp(
        Node.DIRECT_EVAL, false);
    assertPrintNode("(0,eval)(\"1\")", n);
  }
}
