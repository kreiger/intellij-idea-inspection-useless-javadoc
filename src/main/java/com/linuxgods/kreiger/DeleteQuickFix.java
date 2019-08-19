package com.linuxgods.kreiger;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.linuxgods.kreiger.UselessJavadocInspection.isEmptyDocComment;

class DeleteQuickFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
        return "Delete useless Javadoc";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Delete useless Javadoc";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
        PsiElement psiElement = problemDescriptor.getPsiElement();
        PsiElement parent = psiElement.getParent();
        psiElement.delete();
        if (isEmptyDocComment(parent)) {
            parent.delete();
        }
    }

}
