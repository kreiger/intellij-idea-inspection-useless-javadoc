package com.linuxgods.kreiger;

import com.intellij.codeInspection.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

import static com.intellij.psi.JavaDocTokenType.DOC_COMMENT_DATA;
import static com.intellij.psi.JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
import static java.util.Arrays.asList;
import static com.linuxgods.kreiger.Similarity.levenshtein;

public class UselessJavadocInspection extends BaseJavaLocalInspectionTool {

    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final DeleteQuickFix DELETE_QUICK_FIX = new DeleteQuickFix();

    static boolean isEmptyDocComment(PsiElement parent) {
        return parent instanceof PsiDocComment && getCommentText((PsiDocComment) parent, true).isEmpty();
    }

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
        if (isEmptyDocComment(comment)) {
            registerProblem(comment, "Empty javadoc.", holder, null);
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

        String commentTextWithTags = getCommentText(comment, true);
        String commentTextWithoutTags = getCommentText(comment, false);
        String ownerType = getOwnerType(owner);
        if (registerProblemIfTooSimilar("Comment matches name.", commentTextWithTags, ownerName, comment, null, holder)
                || registerProblemIfTooSimilar("Comment matches return type.", commentTextWithTags, ownerType, comment, null, holder)) {
            return;
        }

        checkParamTags(comment, ownerName, holder);
        checkReturnTag(comment, ownerName, ownerType, holder);
    }

    private void registerProblem(PsiElement element, String message, ProblemsHolder holder, TextRange rangeInElement) {
        holder.registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, rangeInElement, DELETE_QUICK_FIX);
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
                registerProblem(paramTag, "Missing @param name.", holder, null);
                continue;
            }
            String parameterDescription = getElementsText(paramTag.getDataElements(), 1);
            if (normalize(parameterDescription).isEmpty()) {
                registerProblem(paramTag, "Missing @param description.", holder, null);
                continue;
            }
            parameterDescription = parameterDescription.replaceAll("[\\s*]+$", "");
            TextRange textRange = new TextRange(0,paramTag.getDataElements()[1].getStartOffsetInParent()+parameterDescription.length());
            if (!registerProblemIfTooSimilar("@param description matches @param name.", parameterDescription, valueElement.getText(), paramTag, textRange, holder)) {
                registerProblemIfTooSimilar("@param description matches method name.", parameterDescription, methodName, paramTag, textRange, holder);
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
            registerProblem(returnTag, "Missing @return description.", holder, null);
            return;
        }
        String returnValueDescription = valueElement+" "+getElementsText(returnTag.getDataElements(), 1);
        if (!registerProblemIfTooSimilar("@return description matches return type.", returnValueDescription, ownerType, returnTag, null, holder)) {
            registerProblemIfTooSimilar("@return description matches method name.", returnValueDescription, methodName, returnTag, null, holder);
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

    private boolean registerProblemIfTooSimilar(String message, String s1, String s2, PsiElement element, TextRange rangeInElement, ProblemsHolder holder) {
        Similarity similarity = getSimilarity(s1, s2);
        if (similarity.toLongest() < SIMILARITY_THRESHOLD) {
            return false;
        }
        registerProblem(element, message + " " + similarity, holder, rangeInElement);
        return true;
    }

    private Similarity getSimilarity(String s1, String s2) {
        String fixed1 = normalize(s1);
        String fixed2 = normalize(s2);
        return levenshtein(fixed1, fixed2);
    }

    static String getCommentText(PsiDocComment comment, boolean includeTags) {
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

    private final static List<String> STOP_WORDS = asList(
            "skapa", "spara", "skall", "till", "värde",
            "read", "create", "find", "fetch", "init",
            "should",
            "instance", "return", "value", "list",
            "array", "property", "properties", "java");

    static String normalize(String s) {
        s = unCamelCase(s);
        s = removeNonInitialismsUpToLength(s, 3);
        s = s.toLowerCase();
        s = removeStopWords(s);
        s = replaceNonLettersAndNonDigitsWithSpaces(s);
        return s.trim();
    }

    private static String removeNonInitialismsUpToLength3(String s) {
        return removeNonInitialismsUpToLength(s, 3);
    }

    private static String removeStopWords(String s) {
        return s.replaceAll("\\b(" + StringUtils.join(STOP_WORDS, "|") + ")\\p{Ll}?\\b", "");
    }

    private static String removeNonInitialismsUpToLength(String s, int length) {
        return s.replaceAll("\\b\\p{L}\\p{Ll}{0," + (length - 1) + "}\\b", "");
    }

    private static String replaceNonLettersAndNonDigitsWithSpaces(String s) {
        return s.replaceAll("\\P{LD}+", " ");
    }

    public static String unCamelCase(String s) {
        return s.replaceAll("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{Lu}\\p{Ll})", " ");
    }
}

