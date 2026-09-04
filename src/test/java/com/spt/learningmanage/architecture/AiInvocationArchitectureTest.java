package com.spt.learningmanage.architecture;

import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.client.ai.AiHttpTransport;
import com.spt.learningmanage.client.ai.HutoolAiHttpTransport;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.impl.AiModelClientImpl;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class AiInvocationArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.spt.learningmanage");

    @Test
    void onlyPipelineAndModelAdapterMayDependOnModelClient() {
        noClasses()
                .that().doNotHaveFullyQualifiedName(AiInvocationPipeline.class.getName())
                .and().doNotHaveFullyQualifiedName(AiModelClientImpl.class.getName())
                .and().doNotHaveFullyQualifiedName(AiModelClient.class.getName())
                .should().dependOnClassesThat().areAssignableTo(AiModelClient.class)
                .check(productionClasses);
    }

    @Test
    void onlyTransportPackageAndModelAdapterMayDependOnTransport() {
        noClasses()
                .that().doNotHaveFullyQualifiedName(AiHttpTransport.class.getName())
                .and().doNotHaveFullyQualifiedName(HutoolAiHttpTransport.class.getName())
                .and().doNotHaveFullyQualifiedName(AiModelClientImpl.class.getName())
                .should().dependOnClassesThat().areAssignableTo(AiHttpTransport.class)
                .check(productionClasses);
    }

    @Test
    void pipelineIsTheOnlyBusinessModelInvocationEntry() {
        noClasses()
                .that().doNotHaveFullyQualifiedName(AiInvocationPipeline.class.getName())
                .and().doNotHaveFullyQualifiedName(AiModelClientImpl.class.getName())
                .should().callMethod(AiModelClient.class, "chat", AiChatCommand.class)
                .because(AiInvocationPipeline.class.getSimpleName() + " 必须是业务模型调用的唯一入口")
                .check(productionClasses);
        noClasses()
                .that().doNotHaveFullyQualifiedName(AiModelClientImpl.class.getName())
                .should().callMethod(AiModelClient.class, "invoke", String.class, String.class, String.class)
                .because("旧 invoke 只能保留在模型客户端兼容适配器内部")
                .check(productionClasses);
    }
}
