package io.github.ignorelicensescn.removedbysfcninspection;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import io.github.ignorelicensescn.removedbysfcninspection.utils.Consts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.List;

import static io.github.ignorelicensescn.removedbysfcninspection.RemovedInspectionBase.*;
import static io.github.ignorelicensescn.removedbysfcninspection.utils.Utils.*;

public class RemovedApiUsageProcessor implements ApiUsageProcessor {
    private final ProblemsHolder myHolder;
    private final boolean myIgnoreInsideRemoved;
    private final boolean myIgnoreAbstractRemovedOverrides;
    private final boolean myIgnoreImportStatements;
    private final boolean myIgnoreMethodsOfRemoved;
    private final boolean myIgnoreInSameOutermostClass;
    private final boolean myForRemoval;

    public RemovedApiUsageProcessor(@NotNull ProblemsHolder holder,
                                       boolean ignoreInsideRemoved,
                                       boolean ignoreAbstractRemovedOverrides,
                                       boolean ignoreImportStatements,
                                       boolean ignoreMethodsOfRemoved,
                                       boolean ignoreInSameOutermostClass,
                                       boolean forRemoval) {
        myHolder = holder;
        myIgnoreInsideRemoved = ignoreInsideRemoved;
        myIgnoreAbstractRemovedOverrides = ignoreAbstractRemovedOverrides;
        myIgnoreImportStatements = ignoreImportStatements;
        myIgnoreMethodsOfRemoved = ignoreMethodsOfRemoved;
        myIgnoreInSameOutermostClass = ignoreInSameOutermostClass;
        myForRemoval = forRemoval;
    }

    @Override
    public void processReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target, @Nullable UExpression qualifier) {
        checkTargetRemoved(sourceNode, target);
    }

    @Override
    public void processImportReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target) {
        checkTargetRemoved(sourceNode, target);
    }

    private void checkTargetRemoved(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target) {
        PsiElement elementToHighlight = sourceNode.getSourcePsi();
        if (elementToHighlight != null) {
            checkTargetRemoved(elementToHighlight, target);
        }
    }

    private void checkTargetRemoved(@NotNull PsiElement elementToHighlight, @NotNull PsiModifierListOwner target) {
        checkRemoved(target, elementToHighlight, null, myIgnoreInsideRemoved, myIgnoreImportStatements,
                myIgnoreMethodsOfRemoved, myIgnoreInSameOutermostClass, myHolder, myForRemoval);
    }

    @Override
    public void processConstructorInvocation(@NotNull UElement sourceNode,
                                             @NotNull PsiClass instantiatedClass,
                                             @Nullable PsiMethod constructor,
                                             @Nullable UClass subclassDeclaration) {
        if (constructor != null) {
            if (isRemoved(constructor, sourceNode.getSourcePsi()) && myForRemoval == findRemovedBySFCNByAnnotation(constructor)) {
                checkTargetRemoved(sourceNode, constructor);
                return;
            }
        }

        if (isDefaultConstructorRemoved(instantiatedClass)) {
            PsiElement elementToHighlight = sourceNode.getSourcePsi();
            if (elementToHighlight == null) {
                return;
            }
            String description = instantiatedClass.getQualifiedName() + " is removed by sfcn ";

            myHolder.registerProblem(elementToHighlight, description);
        }
    }

    @Override
    public void processMethodOverriding(@NotNull UMethod method, @NotNull PsiMethod overriddenMethod) {
        PsiClass aClass = overriddenMethod.getContainingClass();

        if (aClass == null) return;

        PsiElement methodNameElement = UElementKt.getSourcePsiElement(method.getUastAnchor());
        if (methodNameElement == null) return;

        //Do not show Removed warning for class implementing Removed methods

        if (myIgnoreAbstractRemovedOverrides
                && !aClass.hasAnnotation(Consts.RemovedBySFCNClassName)
                && overriddenMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (
                overriddenMethod.hasAnnotation(Consts.RemovedBySFCNClassName)
                &&
                        myForRemoval == findRemovedBySFCNByAnnotation(overriddenMethod)) {
            String description = getPresentableName(aClass) + " is removed by sfcn";
//                    JavaErrorBundle.message(myForRemoval ? "overrides.marked.for.removal.method" : "overrides.Removed.method",
//                    getPresentableName(aClass));
            myHolder.registerProblem(methodNameElement, description);
        }
    }

    @Override
    public void processJavaModuleReference(@NotNull PsiJavaModuleReference javaModuleReference, @NotNull PsiJavaModule target) {
        checkTargetRemoved(javaModuleReference.getElement(), target);
    }

    /**
     * The default constructor of a class can be externally annotated (IDEA-200832).
     */
    private boolean isDefaultConstructorRemoved(@NotNull PsiClass aClass) {
        List<PsiAnnotation> externalRemoved = ExternalAnnotationsManager
                .getInstance(aClass.getProject())
                .findDefaultConstructorExternalAnnotations(aClass, Consts.RemovedBySFCNClassName);

        return externalRemoved != null &&
                ContainerUtil.exists(externalRemoved, annotation -> findRemovedBySFCNByAnnotation(annotation) == myForRemoval);
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
        if (isRemoved(element, elementToHighlight)) {
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
                    getPresentableName(element) + " is removed by sfcn since "+ sinceString;
        }

        LocalQuickFix replacementQuickFix = getReplacementQuickFix(element, elementToHighlight);

        holder.registerProblem(elementToHighlight, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, rangeInElement,
                LocalQuickFix.notNullElements(replacementQuickFix));
    }
}
