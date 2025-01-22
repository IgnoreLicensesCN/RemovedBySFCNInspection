package io.github.ignorelicensescn.removedbysfcninspection.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import io.github.ignorelicensescn.removedbysfcninspection.utils.Consts;
import io.github.ignorelicensescn.removedbysfcninspection.utils.Utils;
import org.jetbrains.annotations.NotNull;

import static io.github.ignorelicensescn.removedbysfcninspection.RemovedInspectionBase.isElementInsideRemoved;


public class RemovedBySFCNInspectionBase extends LocalInspectionTool {


    @Override
    public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder,
                                                   final boolean isOnTheFly,
                                                   final @NotNull LocalInspectionToolSession session) {
        return new JavaElementVisitor() {

            private void visitPsiElementInternal(PsiElement element){
                visitPsiElementFroAll(element,holder);
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                visitPsiElementInternal(method);
                super.visitMethod(method);
            }

            @Override
            public void visitIdentifier(@NotNull PsiIdentifier identifier) {
                visitPsiElementInternal(identifier);
                super.visitIdentifier(identifier);
            }
        };
    }

    public static void visitPsiElementFroAll(PsiElement element, ProblemsHolder holder){
        if (element instanceof PsiJvmModifiersOwner owner){
            if (owner.hasAnnotation(Consts.RemovedBySFCNClassName)) {
                holder.registerProblem(owner,
                        "SFCN Removed member is still used"
//                        JavaAnalysisBundle.message("deprecated.member.0.is.still.used", name)
                );
            }
        }else if (element instanceof PsiReferenceExpression reference){
            final PsiElement element1 = reference.resolve();
            if (element1 instanceof PsiField) {
                visitPsiElementFroAll(element1,holder);
            }
        }
        else if (element instanceof PsiIdentifier identifier){
            PsiElement parent = identifier.getParent();
            if (parent instanceof PsiMember member && parent instanceof PsiNameIdentifierOwner identifierOwner
                    && identifierOwner.getNameIdentifier() == identifier) {
                checkMember(member, identifier, holder);
            }
            else if (parent != null && parent.getParent() instanceof PsiJavaModule) {
                checkJavaModule((PsiJavaModule)parent.getParent(), holder);
            }
        }
        else if (element instanceof PsiMethodCallExpression expression){
            PsiMethod method = expression.resolveMethod();
            visitPsiElementFroAll(method,holder);
        }
        else if (element instanceof PsiJavaCodeReferenceElement reference){
            if (reference.getParent() instanceof PsiNewExpression) {
                return;
            }
            if (reference.getParent() instanceof PsiAnonymousClass) {
                return;
            }
            if (PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class) != null) {
                return;
            }
            final PsiElement elementResolved = reference.resolve();
            if (element instanceof PsiClass clazz) {
                visitPsiElementFroAll(clazz,holder);
            }
        }
    }

    private static void checkMember(@NotNull PsiMember member, @NotNull PsiIdentifier identifier, @NotNull ProblemsHolder holder) {
        if (!(member instanceof PsiDocCommentOwner)
                || !Utils.isAnnotatedRemovedBySFCN(member)
//                || !isDeprecated((PsiDocCommentOwner)member)
        ) {
            return;
        }

        PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(member.getProject());
        String name = member.getName();
        if (name != null) {
            ThreeState state = hasUsages(member, name, searchHelper, member.getUseScope());
            if (state == ThreeState.YES) {
                holder.registerProblem(identifier,
                        "SFCN Removed member ''"+name+"'' is still used"
//                        JavaAnalysisBundle.message("deprecated.member.0.is.still.used", name)
                );
            }
            else if (state == ThreeState.UNSURE) {
                holder.registerPossibleProblem(identifier);
            }
        }
    }

    private static void checkJavaModule(@NotNull PsiJavaModule javaModule, @NotNull ProblemsHolder holder) {
        if (!Utils.isAnnotatedRemovedBySFCN(javaModule)) {
            return;
        }
        PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(javaModule.getProject());
        ThreeState state = hasUsages(javaModule, javaModule.getName(), searchHelper, javaModule.getUseScope());
        if (state == ThreeState.YES) {
            holder.registerProblem(javaModule.getNameIdentifier(),
                    "SFCN Removed member ''"+javaModule.getName()+"'' is still used"
//                    JavaAnalysisBundle.message("deprecated.member.0.is.still.used", javaModule.getName())
            );
        }
        else if (state == ThreeState.UNSURE) {
            holder.registerPossibleProblem(javaModule.getNameIdentifier());
        }
    }

//    private static boolean isDeprecated(PsiDocCommentOwner element) {
//        return element.isDeprecated();
//    }


    private static ThreeState hasUsages(@NotNull PsiElement element,
                                        @NotNull String name,
                                        @NotNull PsiSearchHelper psiSearchHelper,
                                        @NotNull SearchScope searchScope) {
        PsiSearchHelper.SearchCostResult cheapEnough
                = searchScope instanceof GlobalSearchScope ?
                psiSearchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)searchScope, null, null) : null;
        if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) {
            return ThreeState.NO;
        }

        if (cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
            return ThreeState.UNSURE;
        }

        return ThreeState.fromBoolean(ReferencesSearch.search(element, searchScope, false)
                .anyMatch(reference -> {
                    PsiElement referenceElement = reference.getElement();
                    return PsiTreeUtil.getParentOfType(referenceElement, PsiImportStatementBase.class) == null &&
                            !isElementInsideRemoved(referenceElement)
                            &&
                            !PsiUtil.isInsideJavadocComment(referenceElement);
                }));
    }

}
