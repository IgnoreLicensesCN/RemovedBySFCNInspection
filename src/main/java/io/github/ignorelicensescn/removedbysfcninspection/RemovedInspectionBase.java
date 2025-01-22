package io.github.ignorelicensescn.removedbysfcninspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptCheckbox;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.JavaDeprecationUtils;
import io.github.ignorelicensescn.removedbysfcninspection.utils.Consts;
import io.github.ignorelicensescn.removedbysfcninspection.utils.Utils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

import static io.github.ignorelicensescn.removedbysfcninspection.utils.Utils.findRemovedBySFCNByAnnotation;

public class RemovedInspectionBase extends LocalInspectionTool {
    public boolean IGNORE_IN_SAME_OUTERMOST_CLASS = true;

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    public static void checkRemoved(@NotNull PsiModifierListOwner element,
                                       @NotNull PsiElement elementToHighlight,
                                       @Nullable TextRange rangeInElement,
                                       boolean ignoreInsideRemoved,
                                       boolean ignoreImportStatements,
                                       boolean ignoreMethodsOfRemoved,
                                       boolean ignoreInSameOutermostClass,
                                       @NotNull ProblemsHolder holder,
                                       boolean forRemoval) {
        if (Utils.isRemoved(element, elementToHighlight)) {
            if (forRemoval != isForRemovalAttributeSet(element)) {
                return;
            }
        }
        else {
            if (!ignoreMethodsOfRemoved) {
                PsiClass containingClass = element instanceof PsiMember ? ((PsiMember)element).getContainingClass() : null;
                if (containingClass != null) {
                    checkRemoved(containingClass, elementToHighlight, rangeInElement, ignoreInsideRemoved, ignoreImportStatements,
                            false, ignoreInSameOutermostClass, holder, forRemoval);
                }
            }
            return;
        }

        if (ignoreInSameOutermostClass && areElementsInSameOutermostClass(element, elementToHighlight)) return;

        if (ignoreInsideRemoved && isElementInsideRemoved(elementToHighlight)) return;

        if (ignoreImportStatements && isElementInsideImportStatement(elementToHighlight)) return;

        String sinceString = getSinceString(element);

        String description;
        if (sinceString == null || sinceString.isBlank()) {
            description = 
                    getPresentableName(element) + " is removed by sfcn";
        } else {
            description =
                    getPresentableName(element) + " is removed by sfcn since " + sinceString;
        }

        LocalQuickFix replacementQuickFix = null;//getReplacementQuickFix(element, elementToHighlight);

        holder.registerProblem(elementToHighlight, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, rangeInElement,
                LocalQuickFix.notNullElements(replacementQuickFix));
    }

    public static boolean isElementInsideImportStatement(@NotNull PsiElement elementToHighlight) {
        return PsiTreeUtil.getParentOfType(elementToHighlight, PsiImportStatement.class) != null;
    }

    public static boolean isElementInsideRemoved(@NotNull PsiElement element) {
        PsiElement parent = element;
        while ((parent = PsiTreeUtil.getParentOfType(parent, PsiModifierListOwner.class, true)) != null) {
            if (isRemovedPsi(parent)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRemovedByDocTag(@NotNull PsiJavaDocumentedElement owner) {
        PsiDocComment docComment = owner.getDocComment();
        return docComment != null && docComment.findTagByName("removed") != null;
    }

    public static boolean isRemovedPsi(@NotNull PsiElement psiElement) {
        if (psiElement instanceof PsiDocCommentOwner) {
            return ((PsiDocCommentOwner)psiElement).hasAnnotation(Consts.RemovedBySFCNClassName);
        }
        if (psiElement instanceof PsiModifierListOwner && findRemovedBySFCNByAnnotation((PsiModifierListOwner) psiElement)) {
            return true;
        }
        if (psiElement instanceof PsiJavaDocumentedElement) {
            return isRemovedByDocTag((PsiJavaDocumentedElement)psiElement);
        }
        return false;
    }

    @Nullable
    public static LocalQuickFix getReplacementQuickFix(@NotNull PsiModifierListOwner RemovedElement,
                                                       @NotNull PsiElement elementToHighlight) {
//        PsiMethodCallExpression methodCall = getMethodCall(elementToHighlight);
//        if (RemovedElement instanceof PsiMethod method && methodCall != null) {
//            PsiMethod replacement = findReplacementInJavaDoc(method, methodCall);
//            if (replacement != null) {
//                ModCommandAction action =
//                        new ReplaceMethodCallFix((PsiMethodCallExpression)elementToHighlight.getParent().getParent(), replacement);
//                return LocalQuickFix.from(action);
//            }
//        }
//        if (RemovedElement instanceof PsiField field) {
//            PsiReferenceExpression referenceExpression = getFieldReferenceExpression(elementToHighlight);
//            if (referenceExpression != null) {
//                PsiMember replacement = findReplacementInJavaDoc(field, referenceExpression);
//                if (replacement != null) {
////                    ModCommandAction action = new ReplaceFieldReferenceFix(referenceExpression, replacement);
////                    return LocalQuickFix.from(action);
//                }
//            }
//        }
        return null;
    }

    public static String getPresentableName(@NotNull PsiElement psiElement) {
        //Annotation attribute methods don't have parameters.
        if (psiElement instanceof PsiMethod && PsiUtil.isAnnotationMethod(psiElement)) {
            return ((PsiMethod)psiElement).getName();
        }
        return HighlightMessageUtil.getSymbolName(psiElement);
    }

    public static String getSinceString(@NotNull PsiModifierListOwner element) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(element, Consts.RemovedBySFCNClassName);
        if (annotation != null) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue("since");
            if (value instanceof PsiExpression expression &&
                    ExpressionUtils.computeConstantExpression(expression) instanceof String since) {
                return since;
            }
        }
        return null;
    }

    public static boolean isForRemovalAttributeSet(@NotNull PsiModifierListOwner element) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(element, Consts.RemovedBySFCNClassName);
        if (annotation != null) {
            return isForRemovalAttributeSet(annotation);
        }
        return false;
    }

    public static boolean isForRemovalAttributeSet(@NotNull PsiAnnotation RemovedAnnotation) {
        return Boolean.TRUE == AnnotationUtil.getBooleanAttributeValue(RemovedAnnotation, "forRemoval");
    }

    public static boolean areElementsInSameOutermostClass(PsiElement refElement, PsiElement elementToHighlight) {
        PsiClass outermostClass = CachedValuesManager.getCachedValue(
                refElement,
                () -> new CachedValueProvider.Result<>(PsiUtil.getTopLevelClass(refElement), PsiModificationTracker.MODIFICATION_COUNT)
        );
        return outermostClass != null && outermostClass == PsiUtil.getTopLevelClass(elementToHighlight);
    }

    public static OptCheckbox getSameOutermostClassCheckBox() {
        return OptPane.checkbox("IGNORE_IN_SAME_OUTERMOST_CLASS", JavaAnalysisBundle.message("ignore.in.the.same.outermost.class"));
    }

    public static PsiMember findReplacementInJavaDoc(@NotNull PsiField field, @NotNull PsiReferenceExpression referenceExpression) {
        PsiClass qualifierClass = RefactoringChangeUtil.getQualifierClass(referenceExpression);
        return getReplacementCandidatesFromJavadoc(field, PsiField.class, field, qualifierClass)
                .filter(tagField -> areReplaceable(tagField, referenceExpression))
                .select(PsiMember.class)
                .append(getReplacementCandidatesFromJavadoc(field, PsiMethod.class, field, qualifierClass)
                        .filter(tagMethod -> areReplaceable(tagMethod, referenceExpression)))
                .collect(MoreCollectors.onlyOne())
                .orElse(null);
    }

    public static PsiMethod findReplacementInJavaDoc(@NotNull PsiMethod method, @NotNull PsiMethodCallExpression call) {
        if (method instanceof PsiConstructorCall) return null;
        if (method instanceof ClsMethodImpl) {
            PsiMethod sourceMethod = ((ClsMethodImpl)method).getSourceMirrorMethod();
            return sourceMethod == null ? null : findReplacementInJavaDoc(sourceMethod, call);
        }

        return getReplacementCandidatesFromJavadoc(method, PsiMethod.class, call,
                RefactoringChangeUtil.getQualifierClass(call.getMethodExpression()))
                .filter(tagMethod -> areReplaceable(method, tagMethod, call))
                .collect(MoreCollectors.onlyOne())
                .orElse(null);
    }

    @NotNull
    public static <T extends PsiDocCommentOwner> StreamEx<? extends T> getReplacementCandidatesFromJavadoc(PsiDocCommentOwner member, Class<T> clazz, PsiElement context, PsiClass qualifierClass) {
        PsiDocComment doc = member.getDocComment();
        if (doc == null) return StreamEx.empty();

        Collection<PsiDocTag> docTags = PsiTreeUtil.findChildrenOfType(doc, PsiDocTag.class);
        if (docTags.isEmpty()) return StreamEx.empty();
        return StreamEx.of(docTags)
                .filter(t -> {
                    String name = t.getName();
                    return "link".equals(name) || "see".equals(name);
                })
                .map(tag -> tag.getValueElement())
                .nonNull()
                .map(value -> value.getReference())
                .nonNull()
                .map(reference -> reference.resolve())
                .select(clazz)
                .distinct()
                .filter(tagMethod -> !tagMethod.hasAnnotation(Consts.RemovedBySFCNClassName)) // not Removed
                .filter(tagMethod -> PsiResolveHelper.getInstance(context.getProject()).isAccessible(tagMethod, context, qualifierClass)) // accessible
                .filter(tagMethod -> !member.getManager().areElementsEquivalent(tagMethod, member)); // not the same
    }

    public static boolean areReplaceable(PsiField suggested, PsiReferenceExpression expression) {
        if (ExpressionUtils.isVoidContext(expression)) return true;
        PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
        if (expectedType == null) return true;
        PsiType suggestedType = suggested.getType();
        return TypeConversionUtil.isAssignable(expectedType, suggestedType);
    }

    public static boolean areReplaceable(PsiMethod suggested, PsiReferenceExpression expression) {
        if (!suggested.getParameterList().isEmpty()) return false;
        if (ExpressionUtils.isVoidContext(expression)) return true;
        PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
        if (expectedType == null) return true;
        PsiType suggestedType = suggested.getReturnType();
        return suggestedType != null && TypeConversionUtil.isAssignable(expectedType, suggestedType);
    }

    public static boolean areReplaceable(@NotNull PsiMethod initial,
                                          @NotNull PsiMethod suggestedReplacement,
                                          @NotNull PsiMethodCallExpression call) {
        boolean isInitialStatic = initial.hasModifierProperty(PsiModifier.STATIC);

        String qualifierText;
        if (isInitialStatic) {
            qualifierText = Objects.requireNonNull(suggestedReplacement.getContainingClass()).getQualifiedName() + ".";
        }
        else {
            PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
            qualifierText = qualifierExpression == null ? "" : qualifierExpression.getText() + ".";

            PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
            if (qualifier == null) return false;
            PsiClass qualifierClass = PsiUtil.resolveClassInType(qualifier.getType());
            if (qualifierClass == null) return false;
            PsiClass suggestedClass = suggestedReplacement.getContainingClass();
            if (suggestedClass == null || !InheritanceUtil.isInheritorOrSelf(qualifierClass, suggestedClass, true)) return false;
        }

        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(initial.getProject());
        PsiExpressionList arguments = call.getArgumentList();
        PsiMethodCallExpression suggestedCall = (PsiMethodCallExpression)elementFactory
                .createExpressionFromText(qualifierText + suggestedReplacement.getName() + arguments.getText(), call);

        PsiType type = ExpectedTypeUtils.findExpectedType(call, true);
        if (type != null && !type.equals(PsiTypes.voidType())) {
            PsiType suggestedCallType = suggestedCall.getType();
            if (!ExpressionUtils.isVoidContext(call) && suggestedCallType != null && !TypeConversionUtil.isAssignable(type, suggestedCallType)) {
                return false;
            }
        }

        MethodCandidateInfo result = ObjectUtils.tryCast(suggestedCall.resolveMethodGenerics(), MethodCandidateInfo.class);
        return result != null && result.isApplicable();
    }

    @Nullable
    public static PsiReferenceExpression getFieldReferenceExpression(@NotNull PsiElement element) {
        if (element instanceof PsiReferenceExpression) {
            return (PsiReferenceExpression) element;
        }
        return ObjectUtils.tryCast(element.getParent(), PsiReferenceExpression.class);
    }

    @Nullable
    public static PsiMethodCallExpression getMethodCall(@NotNull PsiElement element) {
        if (element instanceof PsiReferenceExpression) {
            return ObjectUtils.tryCast(element.getParent(), PsiMethodCallExpression.class);
        }
        if (element instanceof PsiIdentifier) {
            return getMethodCall(element.getParent());
        }
        return null;
    }
}
