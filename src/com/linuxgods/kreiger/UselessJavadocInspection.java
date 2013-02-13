package com.linuxgods.kreiger;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.JavaDocTokenType.DOC_COMMENT_DATA;
import static com.intellij.psi.JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
import static java.util.Arrays.asList;
import static com.linuxgods.kreiger.Similarity.levenshtein;

public class UselessJavadocInspection extends BaseJavaLocalInspectionTool {

    public static final double SIMILARITY_THRESHOLD = 0.5;
    public static final DeleteQuickFix DELETE_QUICK_FIX = new DeleteQuickFix();

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {

        return new JavaElementVisitor() {
            @Override
            public void visitDocComment(PsiDocComment comment) {
                checkComment(comment, holder);
            }
        };
    }

    private void checkComment(final PsiDocComment comment, ProblemsHolder holder) {
        if (null == comment) {
            return;
        }
        String commentTextWithTags = getCommentText(comment, true);
        if (commentTextWithTags.isEmpty()) {
            registerProblem(comment, "Empty javadoc.", holder);
            return;
        }

        PsiDocCommentOwner owner = comment.getOwner();
        if (null == owner) {
            return;
        }
        String ownerName = owner.getName();
        if (null == ownerName) {
            return;
        }

        String commentTextWithoutTags = getCommentText(comment, false);
        String ownerType = getOwnerType(owner);
        if (registerProblemIfTooSimilar("Comment matches name.", commentTextWithoutTags, ownerName, comment, holder)
                || registerProblemIfTooSimilar("Comment matches return type.", commentTextWithoutTags, ownerType, comment, holder)) {
            return;
        }

        checkParamTags(comment, ownerName, holder);
        checkReturnTag(comment, ownerName, ownerType, holder);
    }

    private void registerProblem(PsiElement element, String message, ProblemsHolder holder) {
        holder.registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, DELETE_QUICK_FIX);
    }

    private String getOwnerType(PsiDocCommentOwner owner) {
        PsiType psiType = null;
        if (owner instanceof PsiMethod) {
            psiType = ((PsiMethod) owner).getReturnType();
        } else if (owner instanceof PsiField) {
            psiType = ((PsiField) owner).getType();
        }
        if (null != psiType) {
            return psiType.getPresentableText();
        }
        return owner.getName();
    }

    private void checkParamTags(PsiDocComment comment, String methodName, ProblemsHolder holder) {
        PsiDocTag[] paramTags = comment.findTagsByName("param");
        for (PsiDocTag paramTag : paramTags) {
            PsiDocTagValue valueElement = paramTag.getValueElement();
            if (null == valueElement) {
                registerProblem(paramTag, "Missing @param name.", holder);
                continue;
            }
            String parameterDescription = getElementsText(paramTag.getDataElements(), 1);
            if (normalize(parameterDescription).isEmpty()) {
                registerProblem(paramTag, "Missing @param description.", holder);
                continue;
            }
            if (!registerProblemIfTooSimilar("@param description matches @param name.", parameterDescription, valueElement.getText(), paramTag, holder)) {
                registerProblemIfTooSimilar("@param description matches method name.", parameterDescription, methodName, paramTag, holder);
            }
        }
    }

    private void checkReturnTag(PsiDocComment comment, String methodName, String ownerType, ProblemsHolder holder) {
        PsiDocTag returnTag = comment.findTagByName("return");
        if (null == returnTag) {
            return;
        }
        PsiDocTagValue valueElement = returnTag.getValueElement();
        if (null == valueElement) {
            registerProblem(returnTag, "Missing @return description.", holder);
            return;
        }
        String returnValueDescription = getElementsText(returnTag.getDataElements(), 0);
        if (!registerProblemIfTooSimilar("@return description matches return type.", returnValueDescription, ownerType, returnTag, holder)) {
            registerProblemIfTooSimilar("@return description matches method name.", returnValueDescription, methodName, returnTag, holder);
        }
    }

    private String getElementsText(PsiElement[] dataElements, int offset) {
        StringBuilder dataElementsStringBuilder = new StringBuilder();
        for (int i = offset; i < dataElements.length; i++) {
            PsiElement dataElement = dataElements[i];
            dataElementsStringBuilder.append(dataElement.getText());
        }
        return dataElementsStringBuilder.toString().trim();
    }

    private boolean registerProblemIfTooSimilar(String message, String s1, String s2, PsiElement element, ProblemsHolder holder) {
        Similarity similarity = getSimilarity(s1, s2);
        if (similarity.toLongest() < SIMILARITY_THRESHOLD) {
            return false;
        }
        registerProblem(element, message + " " + similarity, holder);
        return true;
    }

    private Similarity getSimilarity(String s1, String s2) {
        String fixed1 = normalize(s1);
        String fixed2 = normalize(s2);
        return levenshtein(fixed1, fixed2);
    }

    private static String getCommentText(PsiDocComment comment, boolean includeTags) {
        final StringBuilder commentText = new StringBuilder();
        for (PsiElement docCommentChild : comment.getChildren()) {
            if (docCommentChild instanceof PsiDocTag) {
                if (includeTags) {
                    for (PsiElement docTagChild : docCommentChild.getChildren()) {
                        if (!(docTagChild instanceof PsiDocToken) || !DOC_COMMENT_LEADING_ASTERISKS.equals(((PsiDocToken) docTagChild).getTokenType())) {
                            commentText.append(docTagChild.getText());
                        }
                    }
                }
            } else if (docCommentChild instanceof PsiDocToken) {
                PsiDocToken docToken = (PsiDocToken) docCommentChild;
                if (DOC_COMMENT_DATA.equals(docToken.getTokenType())) {
                    commentText.append(docCommentChild.getText());
                }
            } else {
                commentText.append(docCommentChild.getText());
            }
        }
        return commentText.toString().trim();
    }

    private static class DeleteQuickFix implements LocalQuickFix {
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
            if (parent instanceof PsiDocComment && getCommentText((PsiDocComment) parent, true).isEmpty()) {
                parent.delete();
            }
        }
    }

    private final static List<String> STOP_WORDS = asList(
            "skapa", "spara", "skall", "till", "värde",
            "read", "create", "find", "fetch", "init",
            "instance", "return", "value", "list",
            "array", "property", "properties", "java");

    static String normalize(String s1) {
        return unCamelCase(s1)
                .replaceAll("\\b\\p{L}\\p{Ll}{1,2}\\b", "")
                .toLowerCase()
                .replaceAll("\\b(" + StringUtils.join(STOP_WORDS, "|") + ")\\p{Ll}?\\b", "")
                .replaceAll("\\P{LD}+", " ")
                .trim();
    }

    public static String unCamelCase(String s) {
        return s.replaceAll("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{Lu}\\p{Ll})", " ");
    }
}

