package com.chatflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the module-boundary rules documented in {@code docs/ARCHITECTURE.md}:
 * dependencies flow one direction (feature → shared infra/config), and the technical
 * layers {@code infra} and {@code ai} never depend on a feature.
 */
class ModuleBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chatflow");

    /** Feature / orchestration packages — none of these may be depended on by infra or ai. */
    private static final String[] FEATURES = {
            "..auth..", "..user..", "..conversation..", "..friend..",
            "..notification..", "..media..", "..presence..", "..typing..", "..realtime.."
    };

    @Test
    void noCyclesBetweenModules() {
        slices().matching("com.chatflow.(*)..")
                .should().beFreeOfCycles()
                .check(CLASSES);
    }

    @Test
    void infraDependsOnNoFeature() {
        noClasses().that().resideInAPackage("..infra..")
                .should().dependOnClassesThat().resideInAnyPackage(withAi(FEATURES))
                .because("infra is pure plumbing; the dependency must point feature -> infra")
                .check(CLASSES);
    }

    @Test
    void aiDependsOnNoFeature() {
        // AI now lives in the chatflow-ai module; core hosts no model-provider code. Kept as a
        // guard against re-introducing an ai package in core that reaches into a feature.
        noClasses().that().resideInAPackage("com.chatflow.ai..")
                .should().dependOnClassesThat().resideInAnyPackage(FEATURES)
                .because("ai is a pure model-provider and must not depend on a feature")
                .allowEmptyShould(true)   // core has no ai package post-Phase-1.5; guard for re-introductions
                .check(CLASSES);
    }

    private static String[] withAi(String[] base) {
        String[] all = new String[base.length + 1];
        System.arraycopy(base, 0, all, 0, base.length);
        all[base.length] = "com.chatflow.ai..";
        return all;
    }
}
