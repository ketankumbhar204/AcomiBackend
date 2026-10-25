package com.acomi.acomi_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFirebasePathResolutionTest {

    @Test
    void codeSourceWalkFindsTheModuleRoot(@TempDir Path module) throws Exception {
        Path classes = module.resolve("target").resolve("classes");
        Files.createDirectories(classes);
        Files.createDirectories(module.resolve("src").resolve("main"));
        Files.writeString(module.resolve("pom.xml"), "<project/>");

        assertThat(BackendModuleRoot.fromCodeSource(classes)).isEqualTo(module.toAbsolutePath().normalize());
    }

    @Test
    void envFileComesFromTheBackendModuleWhenTheWorkingDirectoryIsDifferent(
            @TempDir Path workingDirectory, @TempDir Path module) throws Exception {
        Files.writeString(workingDirectory.resolve(".env"), "ACOMI_PUSH_ENABLED=false\n");
        Files.createDirectories(module.resolve("src").resolve("main"));
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        Files.writeString(module.resolve(".env"), "ACOMI_PUSH_ENABLED=true\n");

        Path resolved = DotEnvEnvironmentPostProcessor.resolveEnvFile(workingDirectory, module);

        assertThat(resolved).isEqualTo(module.resolve(".env").toAbsolutePath().normalize());
    }

    @Test
    void envFileFallsBackToTheWorkingDirectoryWhenTheModuleHasNone(
            @TempDir Path workingDirectory, @TempDir Path module) throws Exception {
        Files.writeString(workingDirectory.resolve(".env"), "ACOMI_PUSH_ENABLED=true\n");

        Path resolved = DotEnvEnvironmentPostProcessor.resolveEnvFile(workingDirectory, module);

        assertThat(resolved).isEqualTo(workingDirectory.resolve(".env").toAbsolutePath().normalize());
    }
}
