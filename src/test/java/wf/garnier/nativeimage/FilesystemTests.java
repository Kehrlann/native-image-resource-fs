package wf.garnier.nativeimage;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledInNativeImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FilesystemTests {

    /**
     * This works in native-image, because it uses {@link ClassLoader#getResourceAsStream(String)}
     */
    @Test
    @Order(1)
    void filesystemResource() throws IOException {
        var resource = getClass().getClassLoader().getResourceAsStream("hello.txt");
        var result = readInputStream(resource);

        assertThat(result).isEqualTo("hello world");
    }


    /**
     * This does NOT work in native-image, it's using {@link Path#of(URI)} and you get a {@link java.nio.file.FileSystemNotFoundException}
     * in native-image. This is a known limitation, see this discussion: https://github.com/oracle/graal/issues/7682.
     * <p>
     * To work around it, check example {@link FilesystemTests#filesystemInitializedPath()}.
     */
    @Test
    @Order(2)
    void filesystemPath() throws IOException, URISyntaxException {
        var resource = getClass().getClassLoader().getResource("hello.txt").toURI();
        var path = Path.of(resource);
        System.out.println("~~> resource path: " + path);
        var result = Files.readAllLines(path).stream().collect(Collectors.joining("\n"));

        assertThat(result).isEqualTo("hello world");
    }


    /**
     * Same as {@link FilesystemTests#filesystemPath()}.
     */
    @Test
    @Order(3)
    void systemClassLoader() throws IOException, URISyntaxException {
        var resource = ClassLoader.getSystemClassLoader().getResource("hello.txt");
        System.out.println("systemClassLoader ~~> resource path: " + resource);
        var path = Path.of(resource.toURI());
        var result = Files.readAllLines(path).stream().collect(Collectors.joining("\n"));

        assertThat(result).isEqualTo("hello world");
    }

    /**
     * Here we apply what Testcontainers does when you call {@code MountableFile.transferTo(...)}, which happens every time
     * there is a {@code GenericContainer#withCopyFileToContainer(...)} call. It does not work in native image for multiple
     * reasons, the first problem being that, in Native Image, you get a path in the form of {@code resource:/hello.txt}.
     * When this gets canonicalized, you get weird results e.g. {@code /Users/kehrlann/my-work-dir/resource:/hello.txt}.
     */
    @Test
    @Order(4)
    void fsCopy() throws URISyntaxException, IOException {
        var path = ClassLoader.getSystemClassLoader().getResource("hello.txt").toString().replace("file:", "");
        var sourceFile = new File(path).getCanonicalFile();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Files.copy(sourceFile.toPath(), byteArrayOutputStream);
        byteArrayOutputStream.close();

        var contents = readInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
        assertThat(contents).isEqualTo("hello world");
    }

    /**
     * This works in native image, because the {@code NativeImageResourceFileSystem} is initialized trying to access
     * resources through {@link Path}.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    void filesystemInitializedPath() throws IOException, URISyntaxException {
        createFileSystem();

        var resource = getClass().getClassLoader().getResource("hello.txt").toURI();
        var path = Path.of(resource);
        System.out.println("filesystemInitializedPath ~~> resource path: " + path);
        var result = Files.readAllLines(path).stream().collect(Collectors.joining("\n"));

        assertThat(result).isEqualTo("hello world");
    }

    /**
     * This works in native image, see {@link FilesystemTests#filesystemInitializedPath()}.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    void filesystemInitializedSystemClassLoader() throws IOException, URISyntaxException {
        createFileSystem();

        var resource = ClassLoader.getSystemClassLoader().getResource("hello.txt");
        System.out.println("systemClassLoaderInitialized ~~> resource path: " + resource);
        var path = Path.of(resource.toURI());
        System.out.println("~~> resource path: " + path);
        var result = Files.readAllLines(path).stream().collect(Collectors.joining("\n"));

        assertThat(result).isEqualTo("hello world");
    }


    /**
     * This works in native image, see {@link FilesystemTests#filesystemInitializedPath()}.
     * <p>
     * Ideally, this is what we want to be doing in Testcontainers.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    void filesystemInitializedFsCopy() throws URISyntaxException, IOException {
        createFileSystem();
        var resource = ClassLoader.getSystemClassLoader().getResource("hello.txt");
        var path = Path.of(resource.toURI());

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // This path is a com.oracle.svm.core.jdk.resources.NativeImageResourcePath ; injected by GraalVM at compile time.
        System.out.println("filesystemInitializedFsCopy ~~~> " + path + " ; fs : " + path.getClass().getName());

        Files.copy(path, byteArrayOutputStream);
        byteArrayOutputStream.close();

        var contents = readInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
        assertThat(contents).isEqualTo("hello world");
    }


    /**
     * This shows the limitations of the {@link File} api. It will not run on the JVM.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    @EnabledInNativeImage
    void filesystemInitializedFileApi() throws URISyntaxException, IOException, ClassNotFoundException {
        createFileSystem();
        var path = ClassLoader.getSystemClassLoader().getResource("hello.txt").toString();
        assertThat(path).isEqualTo("resource:/hello.txt");

        // File.toPath gives you a UnixPath instead of Graal's NativeImageResourcePath
        // In that case, Files.copy will NOT work and throw ; whether you prefix the file path with resource:/ or not.
        File sourceFile = new File(path);
        assertThat(sourceFile.toPath().getClass().getName()).isEqualTo("sun.nio.fs.UnixPath");
        assertThatThrownBy(() -> Files.copy(sourceFile.toPath(), new ByteArrayOutputStream()))
                .isInstanceOf(NoSuchFileException.class)
                .hasMessage("resource:/hello.txt");

        File sourceFileWithoutPrefix = new File(path.replace("resource:", ""));
        assertThat(sourceFileWithoutPrefix.toPath().getClass().getName()).isEqualTo("sun.nio.fs.UnixPath");
        assertThatThrownBy(() -> Files.copy(sourceFileWithoutPrefix.toPath(), new ByteArrayOutputStream()))
                .isInstanceOf(NoSuchFileException.class)
                .hasMessage("/hello.txt");
    }

    private static void createFileSystem() {
        try {
            FileSystem x = FileSystems.newFileSystem(URI.create("resource:/"), Collections.singletonMap("create", "true"));
            System.out.println("✅ Created file system " + x.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("⚠️ Did not create FS");
            // not in native image
        }
    }

    private String readInputStream(InputStream inputStream) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            return br.lines()
                    .collect(Collectors.joining("\n"));
        }
    }
}
