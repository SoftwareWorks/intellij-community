// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A class which converts switch statement breaks before switch statement
 * is unwrapped.
 */
public class BreakConverter {
  private final PsiSwitchStatement mySwitchStatement;
  private final String myReplacement;

  public BreakConverter(PsiSwitchStatement switchStatement, String replacement) {
    mySwitchStatement = switchStatement;
    myReplacement = replacement;
  }

  public void process(boolean removeRemovable) {
    List<PsiBreakStatement> breaks = collectBreaks();
    for (PsiBreakStatement breakStatement : breaks) {
      if (isRemovable(mySwitchStatement, breakStatement)) {
        if (removeRemovable) {
          DeleteUnnecessaryStatementFix.deleteUnnecessaryStatement(breakStatement);
        }
      } else {
        assert myReplacement != null;
        new CommentTracker().replaceAndRestoreComments(breakStatement, myReplacement);
      }
    }
  }

  @NotNull
  private List<PsiBreakStatement> collectBreaks() {
    List<PsiBreakStatement> breaks = new ArrayList<>();
    mySwitchStatement.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitBreakStatement(PsiBreakStatement statement) {
        super.visitBreakStatement(statement);
        if (statement.findExitedStatement() == mySwitchStatement) {
          breaks.add(statement);
        }
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        // Going down into any expression seems redundant
      }

      @Override
      public void visitClass(PsiClass aClass) {}
    });
    return breaks;
  }

  @Nullable
  private static String getReplacement(PsiStatement statement) {
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiIfStatement || parent instanceof PsiLabeledStatement) {
      return getReplacement((PsiStatement)parent);
    }
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (nextStatement != null) {
      if (nextStatement instanceof PsiContinueStatement ||
          nextStatement instanceof PsiBreakStatement ||
          nextStatement instanceof PsiReturnStatement ||
          nextStatement instanceof PsiThrowStatement) {
        return nextStatement.getText();
      }
      return null;
    }
    if (parent == null) return null;
    if (parent instanceof PsiLoopStatement) {
      return "continue;";
    }
    if (parent instanceof PsiCodeBlock) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethod && PsiType.VOID.equals(((PsiMethod)grandParent).getReturnType()) ||
          grandParent instanceof PsiLambdaExpression &&
          PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiFunctionalExpression)grandParent))) {
        return "return;";
      }
      if (grandParent instanceof PsiBlockStatement) {
        return getReplacement((PsiStatement)grandParent);
      }
    }
    return null;
  }

  private static boolean isRemovable(PsiSwitchStatement switchStatement, PsiStatement statement) {
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiIfStatement || parent instanceof PsiLabeledStatement) {
      return isRemovable(switchStatement, (PsiStatement)parent);
    }
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (nextStatement != null) {
      return nextStatement instanceof PsiBreakStatement &&
             ((PsiBreakStatement)nextStatement).findExitedStatement() == switchStatement;
    }
    if (parent == null) return false;
    if (parent instanceof PsiCodeBlock) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiBlockStatement) {
        return isRemovable(switchStatement, (PsiStatement)grandParent);
      }
      return grandParent == switchStatement;
    }
    return false;
  }

  @Nullable
  public static BreakConverter from(PsiSwitchStatement switchStatement) {
    String replacement = getReplacement(switchStatement);
    if (replacement == null) {
      class Visitor extends JavaRecursiveElementWalkingVisitor {
        boolean hasNonRemovableBreak;

        @Override
        public void visitBreakStatement(PsiBreakStatement statement) {
          super.visitBreakStatement(statement);
          if (statement.findExitedStatement() == switchStatement && !isRemovable(switchStatement, statement)) {
            hasNonRemovableBreak = true;
            stopWalking();
          }
        }

        @Override
        public void visitExpression(PsiExpression expression) {
          // Going down into any expression seems redundant
        }

        @Override
        public void visitClass(PsiClass aClass) {}
      }
      Visitor visitor = new Visitor();
      switchStatement.accept(visitor);
      if (visitor.hasNonRemovableBreak) return null;
    }
    return new BreakConverter(switchStatement, replacement);
  }
}
