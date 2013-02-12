package se.linuxgods.kreiger;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

import static com.intellij.psi.JavaDocTokenType.DOC_COMMENT_DATA;
import static se.linuxgods.kreiger.Similarity.levenshtein;

public class UselessJavadocInspection extends LocalInspectionTool {

    public static final LocalQuickFix[] DELETE_QUICK_FIX = new LocalQuickFix[]{new DeleteQuickFix()};
    public static final double SIMILARITY_THRESHOLD = 0.8;

    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "Javadoc issues";
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "Useless Javadoc";
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
                checkComment(comment, holder, isOnTheFly);
            }
        };
    }

    private void checkComment(final PsiDocComment comment, ProblemsHolder holder, boolean isOnTheFly) {
        if (null == comment) {
            return;
        }
        String commentTextWithoutTags = getCommentText(comment, false);
        String commentTextWithTags = getCommentText(comment, true);
        if (commentTextWithTags.isEmpty()) {
            registerProblem("Empty javadoc.", comment, holder, isOnTheFly);
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

        String ownerType = getOwnerType(owner);
        if (registerProblemIfTooSimilar("Comment matches name.", commentTextWithoutTags, ownerName, comment, holder, isOnTheFly)
         || registerProblemIfTooSimilar("Comment matches return type.", commentTextWithoutTags, ownerType, comment, holder, isOnTheFly)) {
            return;
        }

        checkParamTags(comment, holder.getManager(), isOnTheFly, ownerName, holder);
        checkReturnTag(comment, isOnTheFly, ownerName, holder);
    }

    private String getOwnerType(PsiDocCommentOwner owner) {
        PsiType psiType = null;
        if (owner instanceof PsiMethod) {
            psiType = ((PsiMethod) owner).getReturnType();
        } else if (owner instanceof PsiField) {
            psiType = ((PsiField)owner).getType();
        }
        if (null != psiType) {
            return psiType.getPresentableText();
        }
        return owner.getName();
    }

    private void checkParamTags(PsiDocComment comment, InspectionManager manager, boolean isOnTheFly, String ownerName, ProblemsHolder holder) {
        PsiDocTag[] paramTags = comment.findTagsByName("param");
        for (PsiDocTag paramTag : paramTags) {
            PsiDocTagValue paramValueElement = paramTag.getValueElement();
            if (null == paramValueElement) {
                continue;
            }
            PsiElement[] dataElements = paramTag.getDataElements();
            String parameterDescription = getElementsText(dataElements, 1);
            if (normalize(parameterDescription).isEmpty()) {
                registerProblem("Missing parameter description.", paramTag, holder, isOnTheFly);
                continue;
            }
            if (!registerProblemIfTooSimilar("Parameter description matches parameter name.", paramValueElement.getText(), parameterDescription, paramTag, holder, isOnTheFly)) {
                registerProblemIfTooSimilar("Parameter description matches method name.", ownerName, parameterDescription, paramTag, holder, isOnTheFly);
            }
        }
    }

    private void registerProblem(String message, PsiElement element, ProblemsHolder holder, boolean isOnTheFly) {
        holder.registerProblem(holder.getManager().createProblemDescriptor(element, message, isOnTheFly, DELETE_QUICK_FIX, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
    }

    private void checkReturnTag(PsiDocComment comment, boolean isOnTheFly, String ownerName, ProblemsHolder holder) {
        PsiDocTag returnTag = comment.findTagByName("return");
        if (null == returnTag) {
            return;
        }
        String returnValueDescription = getElementsText(returnTag.getDataElements(), 1);
        if (normalize(returnValueDescription).isEmpty()) {
            registerProblem("Missing return value description.", returnTag, holder, isOnTheFly);
            return;
        }
        registerProblemIfTooSimilar("Return value description matches method name: " + returnValueDescription, ownerName, returnValueDescription, returnTag, holder, isOnTheFly);
    }

    private String getElementsText(PsiElement[] dataElements, int offset) {
        StringBuilder dataElementsStringBuilder = new StringBuilder();
        for (int i = offset; i < dataElements.length; i++) {
            PsiElement dataElement = dataElements[i];
            dataElementsStringBuilder.append(dataElement.getText());
        }
        return dataElementsStringBuilder.toString().trim();
    }

    private boolean registerProblemIfTooSimilar(String message, String s1, String s2, PsiElement element, ProblemsHolder holder, boolean isOnTheFly) {
        Similarity similarity = getSimilarity(s1, s2);
        if (similarity.toLongest() >= SIMILARITY_THRESHOLD) {
            registerProblem(message + " " + similarity, element, holder, isOnTheFly);
            return true;
        }
        return false;
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
                        if (isCommentData(docTagChild)) {
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

    private static boolean isCommentData(PsiElement element) {
        return !(element instanceof PsiDocToken) || DOC_COMMENT_DATA.equals(((PsiDocToken) element).getTokenType());
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

    static String normalize(String s1) {
        return unCamelCase(s1)
                .replaceAll("\\b\\p{L}\\p{Ll}{1,2}\\b", "")
                .toLowerCase()
                .replaceAll("\\b(create|init|instance|return|value|list|array|propert(y|ie)|java)\\p{Ll}?\\b", "")
                .replaceAll("\\P{LD}+", "")
                .trim();
    }

    public static String unCamelCase(String s) {
        return s.replaceAll("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{Lu}\\p{Ll})", " ");
    }
}

