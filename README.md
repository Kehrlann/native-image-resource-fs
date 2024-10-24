# native-image-resource-fs

Tests with Native Image, classpath resources and the `File` api.

## How to run

Everything is under `FilesystemTests`. Use Graal JDK, I've tested with Graal 23.

Run the tests on the JVM, they should all pass:

```
./gradlew test
```

Run the tests in native image, 3 tests will fail, that is on purpose:

```
./gradlew nativeTest
```

Find the details of what works and what doesn't in the test file.

## Further notes

This was created to support a [Testcontainers native-image
issue](https://github.com/testcontainers/testcontainers-java/issues/7954).

I tried a TC patch to TC work by using the `Path` API instead of `File`, but there's another issue
which is that TC uses `org.apache.commons:commons-compress`, and that also does file operations,
some of which are not supported. Specifically, `TarArchiveEntry#readOsSpecificProperties`, used in
the constructor, uses `Files.getOwner` which is just not supported in native image.
