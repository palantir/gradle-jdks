# Test Migration Errors and Fixes

## Migration of PalantirCaPluginIntegrationSpec to PalantirCaPluginIntegrationTest

### Compilation Error 1: InvocationResult.getOutput() method not found
**Error:** `cannot find symbol: method getOutput()`
**Fix:** Changed `result.getOutput()` to use the fluent assertion API: `assertThat(result).output().contains(...)`
**Source:** Testing guide section on Output Assertions

### Error Prone Check: GradleTestPluginsBlock 
**Error:** `Plugins must be added using .plugins().add() method`
**Fix:** Replaced `apply plugin:` statements in the build file string with structured API calls:
```java
project.buildGradle()
    .plugins()
    .add("com.palantir.jdks.palantir-ca")
    .add("java-library");
```
**Source:** Testing guide section on Plugin Management - "Always use the plugins() API instead of manually writing plugin blocks"

### Changes Made During Migration

1. **Test name conversion:** `'#gradleVersionNumber: can add ca certs to a JDK'` → `can_add_ca_certs_to_a_jdk` (snake_case_english_sentence)

2. **Multi-version testing:** Removed explicit `@Unroll` and `where` clause since the new framework automatically runs tests against multiple Gradle versions

3. **File manipulation:** 
   - Converted `buildFile << '''content'''` to structured API calls
   - Converted `writeJavaSourceFile` to `project.mainSourceSet().java().writeClass()`

4. **Method structure:** Created helper methods:
   - `standardBuildFile()` - replaces the build file content variable
   - `outputCaCertsJavaClass` - contains the Java source as a string constant

5. **Assertions:** Used the fluent assertion API with `assertThat(result).output().contains(...)` instead of direct string manipulation

### Second Pass Review

All requirements from the testing guide have been implemented:
- ✅ Used `@GradlePluginTests` annotation
- ✅ Used parameter injection for `GradleInvoker` and `RootProject` 
- ✅ Used fluent APIs for file manipulation
- ✅ Used structured plugin API
- ✅ Used modern AssertJ assertions
- ✅ Preserved all delineator comments from original test
- ✅ Test compiles successfully

## Migration of Multiple Test Classes - First Compilation Attempt

### Error 1: WithJdkAutomanagement annotation package wrong
- **Error**: `package com.palantir.gradle.testing.annotation does not exist`
- **Fix**: Changed import to `com.palantir.gradle.testing.junit.WithJdkAutomanagement`

### Error 2: Return type mismatch for applyBaselineJavaVersions
- **Error**: `incompatible types: Plugins cannot be converted to GradleFile`
- **Fix**: Fixed return type to properly return a GradleFile

### Error 3: Task assertion methods missing
- **Error**: `cannot find symbol: method task(String)`
- **Fix**: Need to use proper fluent assertion API for tasks

### Error 4: Missing AssertJ imports for collections
- **Error**: `no suitable method found for assertThat(Set<String>)`  
- **Fix**: Need to import org.assertj.core.api.Assertions.assertThat for non-gradle specific assertions

### Error 5: File path formatting methods
- **Error**: String formatting methods on file paths don't accept varargs
- **Fix**: Use regular string concatenation instead of String.format style

### Error 6: Task assertion chaining issues  
- **Error**: Methods like `notUpToDate()` not available after `succeeded()`
- **Fix**: Use separate assertion chains for different task aspects

### Error 7: Exception type violations (Error Prone)
- **Error**: `[PreferUncheckedIoException] Prefer UncheckedIOException or SafeUncheckedIoException when wrapping IOException`
- **Fix**: Changed `throw new RuntimeException(e);` to `throw new java.io.UncheckedIOException(e);`

### Error 8: Plugin block Error Prone violation 
- **Error**: `[GradleTestPluginsBlock] Plugins must be added using .plugins().add() method`
- **Fix**: Moved `apply plugin: 'java-library'` from build script content to structured API call: `rootProject.buildGradle().plugins().add("java-library");`

### Error 9: AssertJ imports should be static
- **Issue**: Using `org.assertj.core.api.Assertions.assertThat()` instead of static import
- **Fix**: Changed to `import static org.assertj.core.api.Assertions.assertThat;` and updated all calls to use `assertThat()` directly

### Final Status: SUCCESS ✅
- **Compilation**: All tests now compile successfully with proper static imports
- **Code Quality**: All Error Prone violations resolved
- **Best Practices**: Following modern testing patterns with static imports

## Tests Successfully Migrated

1. ✅ **JdksPluginIntegrationTest** - Migrated from JdksPluginIntegrationSpec
2. ✅ **JdksExtensionProjectTest** - Migrated from JdksExtensionProjectSpec  
3. ✅ **GradleJdkToolchainsIntegrationTest** - Migrated from GradleJdkToolchainsIntegrationTest
4. ✅ **GradleJdkPatcherIntegrationTest** - Migrated from GradleJdkPatcherIntegrationTest
5. ✅ **GradleJdkIntegrationTestBase** - Created as replacement for abstract GradleJdkIntegrationSpec

All tests follow the new Java-based testing framework with:
- `@GradlePluginTests` annotation
- `@WithJdkAutomanagement` annotation for JDK-related tests
- Parameter injection for test components
- Fluent APIs for file manipulation
- Modern AssertJ assertions
- Proper exception handling