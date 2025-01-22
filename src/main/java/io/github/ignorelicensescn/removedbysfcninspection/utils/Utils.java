package io.github.ignorelicensescn.removedbysfcninspection.utils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.ignorelicensescn.removedbysfcninspection.RemovedBySFCN;
import org.jetbrains.annotations.NotNull;

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

    public static boolean isElementInsideDeprecated(@NotNull PsiElement element) {
        PsiElement parent = element;
        while ((parent = PsiTreeUtil.getParentOfType(parent, PsiModifierListOwner.class, true)) != null) {
            if (isAnnotatedRemovedBySFCN(parent)) {
                return true;
            }
        }
        return false;
    }
}
