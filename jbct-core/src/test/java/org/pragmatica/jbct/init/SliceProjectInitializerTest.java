package org.pragmatica.jbct.init;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SliceProjectInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void should_create_slice_project_structure() {
        var projectDir = tempDir.resolve("my-slice");
        var initializer = SliceProjectInitializer.sliceProjectInitializer(
            projectDir, "org.example", "my-slice");

        var result = initializer.initialize();

        assertThat(result.isSuccess()).isTrue();
        result.onSuccess(files -> {
            assertThat(files).isNotEmpty();
            assertThat(projectDir.resolve("pom.xml")).exists();
            assertThat(projectDir.resolve("jbct.toml")).exists();
            assertThat(projectDir.resolve(".gitignore")).exists();
            assertThat(projectDir.resolve("src/main/java/org/example/myslice/MySlice.java")).exists();
            assertThat(projectDir.resolve("src/main/java/org/example/myslice/MySliceImpl.java")).exists();
        });
    }

    @Test
    void should_generate_valid_pom_xml() throws Exception {
        var projectDir = tempDir.resolve("test-slice");
        var initializer = SliceProjectInitializer.sliceProjectInitializer(
            projectDir, "com.test", "test-slice");

        initializer.initialize();

        var pomContent = Files.readString(projectDir.resolve("pom.xml"));
        assertThat(pomContent).contains("<groupId>com.test</groupId>");
        assertThat(pomContent).contains("<artifactId>test-slice</artifactId>");
        assertThat(pomContent).contains("slice.class");
        assertThat(pomContent).contains("collect-slice-deps");
    }

    @Test
    void should_generate_valid_slice_interface() throws Exception {
        var projectDir = tempDir.resolve("inventory-service");
        var initializer = SliceProjectInitializer.sliceProjectInitializer(
            projectDir, "org.example", "inventory-service");

        initializer.initialize();

        var sliceFile = projectDir.resolve(
            "src/main/java/org/example/inventoryservice/InventoryService.java");
        var content = Files.readString(sliceFile);

        assertThat(content).contains("@Slice");
        assertThat(content).contains("public interface InventoryService");
        assertThat(content).contains("static InventoryService inventoryService()");
    }

    @Test
    void should_derive_slice_name_from_artifact_id() {
        var projectDir = tempDir.resolve("my-test-service");
        var initializer = SliceProjectInitializer.sliceProjectInitializer(
            projectDir, "org.example", "my-test-service");

        assertThat(initializer.sliceName()).isEqualTo("MyTestService");
    }
}
