package io.github.ignorelicensescn.removedbysfcninspection.utils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.roots.JdkUtils;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.siyeh.ig.psiutils.JavaDeprecationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.deprecation.DeprecationInspectionBase.getPresentableName;
import static io.github.ignorelicensescn.removedbysfcninspection.RemovedInspectionBase.isRemovedByDocTag;

public class Utils {

    public static boolean isAnnotatedRemovedBySFCN(@NotNull Object o) {
        return o instanceof PsiModifierListOwner owner && findRemovedBySFCNByAnnotation(owner);
    }

    /**
     * @return found or not
     */
    public static boolean findRemovedBySFCNByAnnotation(@NotNull PsiModifierListOwner owner) {
        return AnnotationUtil.findAnnotation(owner, Consts.RemovedBySFCNClassName) != null;
    }
    public static boolean findRemovedBySFCNByAnnotation(@NotNull PsiAnnotation deprecatedAnnotation) {
        return Boolean.TRUE == AnnotationUtil.getBooleanAttributeValue(deprecatedAnnotation, "forRemoval");
    }


    private static @NotNull ThreeState isRemovedByAnnotation(@NotNull PsiModifierListOwner owner, @Nullable PsiElement context) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, Consts.RemovedBySFCNClassName);
        if (annotation == null) return ThreeState.UNSURE;
        if (context == null) return ThreeState.YES;
        String since = null;
        PsiAnnotationMemberValue value = annotation.findAttributeValue("since");
        if (value instanceof PsiLiteralExpression) {
            since = ObjectUtils.tryCast(((PsiLiteralExpression)value).getValue(), String.class);
        }
        if (since == null || JdkUtils.getJdkForElement(owner) == null) return ThreeState.YES;
        LanguageLevel deprecationLevel = LanguageLevel.parse(since);
        return ThreeState.fromBoolean(deprecationLevel == null || PsiUtil.getLanguageLevel(context).isAtLeast(deprecationLevel));
    }


    /**
     * Checks if the given PSI element is deprecated with annotation or JavaDoc tag, taking the context into account.
     * <br>
     * It is suitable for elements other than {@link PsiDocCommentOwner}.
     * The deprecation of JDK members may depend on context. E.g., uses if a JDK method is deprecated since Java 19,
     * but current module has Java 17 target, than the method is not considered as deprecated.
     *
     * @param psiElement element to check whether it's deprecated
     * @param context context in which the check should be performed
     */
    public static boolean isRemoved(@NotNull PsiElement psiElement, @Nullable PsiElement context) {
        if (psiElement instanceof PsiModifierListOwner) {
            ThreeState byAnnotation = isRemovedByAnnotation((PsiModifierListOwner)psiElement, context);
            if (byAnnotation != ThreeState.UNSURE) {
                return byAnnotation.toBoolean();
            }
        }
//        if (psiElement instanceof PsiDocCommentOwner) {
//            return ((PsiDocCommentOwner)psiElement).isDeprecated();
//        }
        if (psiElement instanceof PsiJavaDocumentedElement) {
            return isRemovedByDocTag((PsiJavaDocumentedElement)psiElement);
        }
        return false;
    }


}
