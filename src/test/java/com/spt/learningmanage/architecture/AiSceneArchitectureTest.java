package com.spt.learningmanage.architecture;

import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.scene.AiChatCompatibilityService;
import com.spt.learningmanage.service.ai.scene.DailyRenameAiService;
import com.spt.learningmanage.service.ai.scene.ListReplanAiService;
import com.spt.learningmanage.service.ai.scene.TaskBreakdownAiService;
import com.spt.learningmanage.service.ai.scene.TodayOrderAiService;
import com.spt.learningmanage.service.ai.scene.WeeklyReviewAiService;
import com.spt.learningmanage.service.impl.AiServiceImpl;
import com.spt.learningmanage.service.impl.ai.scene.AiChatCompatibilityServiceImpl;
import com.spt.learningmanage.service.impl.ai.scene.DailyRenameAiServiceImpl;
import com.spt.learningmanage.service.impl.ai.scene.ListReplanAiServiceImpl;
import com.spt.learningmanage.service.impl.ai.scene.TaskBreakdownAiServiceImpl;
import com.spt.learningmanage.service.impl.ai.scene.TodayOrderAiServiceImpl;
import com.spt.learningmanage.service.impl.ai.scene.WeeklyReviewAiServiceImpl;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSceneArchitectureTest {

    private static final Set<Class<?>> SCENE_INTERFACES = Set.of(
            AiChatCompatibilityService.class,
            TaskBreakdownAiService.class,
            WeeklyReviewAiService.class,
            TodayOrderAiService.class,
            DailyRenameAiService.class,
            ListReplanAiService.class
    );
    private static final Set<Class<?>> SCENE_IMPLEMENTATIONS = Set.of(
            AiChatCompatibilityServiceImpl.class,
            TaskBreakdownAiServiceImpl.class,
            WeeklyReviewAiServiceImpl.class,
            TodayOrderAiServiceImpl.class,
            DailyRenameAiServiceImpl.class,
            ListReplanAiServiceImpl.class
    );

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.spt.learningmanage");

    @Test
    void controllersMustNotBypassAiServiceFacade() {
        noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..service.ai.scene..", "..service.impl.ai.scene..")
                .check(productionClasses);
    }

    @Test
    void facadeContainsOnlySceneAndDraftDelegates() {
        Set<Class<?>> fieldTypes = java.util.Arrays.stream(AiServiceImpl.class.getDeclaredFields())
                .map(Field::getType)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(7, fieldTypes.size());
        assertEquals(Set.of(
                AiChatCompatibilityService.class,
                TaskBreakdownAiService.class,
                WeeklyReviewAiService.class,
                TodayOrderAiService.class,
                DailyRenameAiService.class,
                ListReplanAiService.class,
                com.spt.learningmanage.service.ai.support.AiDraftLifecycleService.class
        ), fieldTypes);
        for (Method method : AiServiceImpl.class.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(Transactional.class),
                    "transaction belongs to a scene service: " + method.getName());
        }
    }

    @Test
    void facadeMustNotDependOnBusinessInfrastructure() {
        noClasses()
                .that().haveFullyQualifiedName(AiServiceImpl.class.getName())
                .should().dependOnClassesThat().resideInAnyPackage("..mapper..")
                .orShould().dependOnClassesThat().areAssignableTo(PermissionService.class)
                .orShould().dependOnClassesThat().areAssignableTo(AiInvocationPipeline.class)
                .orShould().dependOnClassesThat().areAssignableTo(AiProperties.class)
                .check(productionClasses);
    }

    @Test
    void sceneServicesMustNotDependOnEachOther() {
        Set<String> implementationNames = SCENE_IMPLEMENTATIONS.stream()
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toSet());
        for (JavaClass owner : productionClasses) {
            if (!implementationNames.contains(owner.getName())) {
                continue;
            }
            for (Dependency dependency : owner.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (target.isTopLevelClass() && implementationNames.contains(target.getName())) {
                    assertTrue(owner.getName().equals(target.getName()),
                            owner.getName() + " must not depend on " + target.getName());
                }
            }
        }
        for (Class<?> implementation : SCENE_IMPLEMENTATIONS) {
            for (Field field : implementation.getDeclaredFields()) {
                assertNoForeignSceneType(implementation, field.getType());
            }
            for (Constructor<?> constructor : implementation.getDeclaredConstructors()) {
                for (Class<?> parameterType : constructor.getParameterTypes()) {
                    assertNoForeignSceneType(implementation, parameterType);
                }
            }
            for (Method method : implementation.getDeclaredMethods()) {
                assertNoForeignSceneType(implementation, method.getReturnType());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    assertNoForeignSceneType(implementation, parameterType);
                }
            }
        }
    }

    private void assertNoForeignSceneType(Class<?> owner, Class<?> dependency) {
        if (!SCENE_INTERFACES.contains(dependency) && !SCENE_IMPLEMENTATIONS.contains(dependency)) {
            return;
        }
        Class<?> ownInterface = Arrays.stream(owner.getInterfaces())
                .filter(SCENE_INTERFACES::contains)
                .findFirst()
                .orElse(null);
        assertEquals(ownInterface, dependency,
                owner.getSimpleName() + " must not depend on " + dependency.getSimpleName());
    }
}
