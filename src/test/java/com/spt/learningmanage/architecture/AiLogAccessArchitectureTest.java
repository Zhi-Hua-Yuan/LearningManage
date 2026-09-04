package com.spt.learningmanage.architecture;

import com.spt.learningmanage.mapper.AiCallLogMapper;
import com.spt.learningmanage.service.impl.AiCallLogServiceImpl;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class AiLogAccessArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.spt.learningmanage");

    @Test
    void controllersMustNotAccessAiCallLogMapperDirectly() {
        noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().areAssignableTo(AiCallLogMapper.class)
                .check(productionClasses);
    }

    @Test
    void onlyAiCallLogServiceImplementationMayUseAiCallLogMapper() {
        noClasses()
                .that().doNotHaveFullyQualifiedName(AiCallLogServiceImpl.class.getName())
                .and().doNotHaveFullyQualifiedName(AiCallLogMapper.class.getName())
                .should().dependOnClassesThat().areAssignableTo(AiCallLogMapper.class)
                .check(productionClasses);
    }
}
