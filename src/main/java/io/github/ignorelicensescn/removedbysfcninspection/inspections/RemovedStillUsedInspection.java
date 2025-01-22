package io.github.ignorelicensescn.removedbysfcninspection.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor;
import com.intellij.codeInspection.deprecation.DeprecationInspectionBase;
import com.intellij.psi.PsiElementVisitor;
import io.github.ignorelicensescn.removedbysfcninspection.RemovedApiUsageProcessor;
import io.github.ignorelicensescn.removedbysfcninspection.RemovedInspectionBase;
import org.jetbrains.annotations.NotNull;

public class RemovedStillUsedInspection extends RemovedInspectionBase {
    public boolean IGNORE_INSIDE_DEPRECATED = true;
    public boolean IGNORE_ABSTRACT_DEPRECATED_OVERRIDES = true;
    public boolean IGNORE_IMPORT_STATEMENTS = true;
    public boolean IGNORE_METHODS_OF_DEPRECATED = true;

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return ApiUsageUastVisitor.createPsiElementVisitor(
                new RemovedApiUsageProcessor(holder, IGNORE_INSIDE_DEPRECATED, IGNORE_ABSTRACT_DEPRECATED_OVERRIDES,
                        IGNORE_IMPORT_STATEMENTS, IGNORE_METHODS_OF_DEPRECATED,
                        IGNORE_IN_SAME_OUTERMOST_CLASS, false)
        );
    }

}
