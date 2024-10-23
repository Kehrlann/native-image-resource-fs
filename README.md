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