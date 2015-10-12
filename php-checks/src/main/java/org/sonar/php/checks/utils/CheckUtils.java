/*
 * SonarQube PHP Plugin
 * Copyright (C) 2010 SonarSource and Akram Ben Aissi
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.php.checks.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Token;
import org.sonar.php.api.PHPKeyword;
import org.sonar.php.parser.PHPGrammar;
import org.sonar.php.tree.impl.PHPTree;
import org.sonar.plugins.php.api.tree.Tree;
import org.sonar.plugins.php.api.tree.lexical.SyntaxToken;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

public class CheckUtils {

  public static final ImmutableMap<String, String> PREDEFINED_VARIABLES = ImmutableMap.<String, String>builder()
    .put("$HTTP_SERVER_VARS", "$_SERVER")
    .put("$HTTP_GET_VARS", "$_GET")
    .put("$HTTP_POST_VARS", "$_POST")
    .put("$HTTP_POST_FILES", "$_FILES")
    .put("$HTTP_SESSION_VARS", "$_SESSION")
    .put("$HTTP_ENV_VARS", "$_ENV")
    .put("$HTTP_COOKIE_VARS", "$_COOKIE").build();

  private CheckUtils() {
  }

  public static boolean isSuperGlobal(String varName) {
    return "$GLOBALS".equals(varName) || PREDEFINED_VARIABLES.values().contains(varName);
  }

  /**
   * Returns whether a class member (method or variable) is static or not.
   *
   * @param modifiers List of MEMBER_MODIFIER
   * @return true if the class member is static, false otherwise
   */
  public static boolean isStaticClassMember(List<AstNode> modifiers) {
    for (AstNode modifier : modifiers) {
      if (modifier.getFirstChild().is(PHPKeyword.STATIC)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isExpressionABooleanLiteral(AstNode expression) {
    Preconditions.checkArgument(expression.is(PHPGrammar.EXPRESSION));
    AstNode postfixExpr = expression.getFirstChild(PHPGrammar.POSTFIX_EXPR);

    if (postfixExpr == null) {
      return false;
    }

    AstNode commonScalar = postfixExpr.getFirstChild(PHPGrammar.COMMON_SCALAR);
    return commonScalar != null && commonScalar.getFirstChild().is(PHPGrammar.BOOLEAN_LITERAL);
  }

  /**
   * Return String representing the full expression given as parameter.
   */
  public static String getExpressionAsString(AstNode node) {
    StringBuilder builder = new StringBuilder();
    for (Token token : node.getTokens()) {
      builder.append(token.getOriginalValue());
    }
    return builder.toString();
  }

  public static boolean areSyntacticallyEquivalent(AstNode node1, AstNode node2) {
    if (!node1.getType().equals(node2.getType())) {
      return false;
    }
    List<Token> tokens1 = node1.getTokens();
    List<Token> tokens2 = node2.getTokens();
    if (tokens1.size() != tokens2.size()) {
      return false;
    }
    for (int i = 0; i < tokens1.size(); i++) {
      Token token1 = tokens1.get(i);
      Token token2 = tokens2.get(i);
      if (!token1.getValue().equals(token2.getValue())) {
        return false;
      }
    }
    return true;
  }
  
  public static boolean areSyntacticallyEquivalent(@Nullable Tree tree1, @Nullable Tree tree2) {
    if (tree1 == tree2) {
      return true;
    }

    if (tree1 == null || tree2 == null) {
      return false;
    }

    PHPTree phpTree1 = (PHPTree) tree1;
    PHPTree phpTree2 = (PHPTree) tree2;

    if (phpTree1.getKind() != phpTree2.getKind()) {
      return false;
    } else if (phpTree1.isLeaf()) {
      return phpTree1.getFirstToken().text().equals(phpTree2.getFirstToken().text());
    }

    Iterator<Tree> iterator1 = phpTree1.childrenIterator();
    Iterator<Tree> iterator2 = phpTree2.childrenIterator();
    return areSyntacticallyEquivalent(iterator1, iterator2);
  }

  public static boolean areSyntacticallyEquivalent(Iterator<Tree> iterator1, Iterator<Tree> iterator2) {
    while (iterator1.hasNext() && iterator2.hasNext()) {
      if (!areSyntacticallyEquivalent(iterator1.next(), iterator2.next())) {
        return false;
      }
    }

    return !iterator1.hasNext() && !iterator2.hasNext();
  }

  public static String asString(Tree tree) {
    if (tree.is(Tree.Kind.TOKEN)) {
      return ((SyntaxToken) tree).text();

    } else {
      StringBuilder sb = new StringBuilder();
      Iterator<Tree> treeIterator = ((PHPTree) tree).childrenIterator();
      SyntaxToken prevToken = null;

      while (treeIterator.hasNext()) {
        Tree child = treeIterator.next();

        if (child != null) {
          appendChild(sb, prevToken, child);
          prevToken = ((PHPTree) child).getLastToken();
        }
      }
      return sb.toString();
    }
  }

  private static void appendChild(StringBuilder sb, @Nullable SyntaxToken prevToken, Tree child) {
    if (prevToken != null) {
      SyntaxToken firstToken = ((PHPTree) child).getFirstToken();
      if (isSpaceRequired(prevToken, firstToken)) {
        sb.append(" ");
      }
    }
    sb.append(asString(child));
  }

  private static boolean isSpaceRequired(SyntaxToken prevToken, SyntaxToken token) {
    return (token.line() > prevToken.line()) || (prevToken.column() + prevToken.text().length() < token.column());
  }

}
