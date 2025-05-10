# Kotlin Multiplatform Migration Summary

This document outlines the steps taken to migrate the Survey Engine from a Kotlin JVM-only application to a Kotlin Multiplatform (KMP) project.

## 1. Project Structure Changes

- Converted Gradle build scripts to Kotlin DSL (.kts)
- Set up multiplatform source sets:
  - commonMain: Core logic shared across all platforms
  - jvmMain: JVM-specific implementations
  - jsMain: JavaScript-specific implementations
  - nativeMain: Native platform-specific implementations
- Added test source sets for each platform

## 2. Dependency Updates

- Replaced Jackson with kotlinx.serialization
- Removed org.json dependency in favor of kotlinx.serialization.json
- Added kotlinx.datetime for cross-platform date handling

## 3. Model Class Conversion

- Added  annotations to all model classes
- Added @SerialName for polymorphic serialization
- Used sealed classes with proper serialization configuration
- Implemented platform-specific code using expect/pattern

## 4. JSON Handling

- Created JsonSerializer.kt with centralized JSON configuration
- Replaced Jackson ObjectNode/ArrayNode with kotlinx.serialization's JsonObject/JsonArray
- Updated all JSON handling utilities to work with the new serialization library

## 5. JavaScript Integration

- Created a cross-platform ScriptEngine interface using expect/actual
- Implemented platform-specific JavaScript execution:
  - JVM: Uses Nashorn JavaScript engine
  - JS: Uses browser's native eval function
  - Native: Placeholder for native JS engine integration
- Migrated JavaScript resources to commonMain resources

## 6. Testing Infrastructure

- Created common tests that run on all platforms
- Added platform-specific tests for platform-specific features

## Next Steps

1. Complete migration of any remaining classes
2. Add more comprehensive tests for all platforms
3. Enhance the native platform implementation with a real JavaScript engine
4. Create platform-specific samples to demonstrate usage on different platforms

## JavaScript Compatibility Issues

### Test Method Names

When writing tests for Kotlin Multiplatform, avoid using backticked function names (like `` `test with spaces` ``) as they contain characters that are illegal in JavaScript identifiers. Instead, use camelCase function names:

```kotlin
// BAD - won't work in JS target
@Test
fun `this test has spaces and special chars!`() { ... }

// GOOD - works in all platforms
@Test
fun thisTestUsesValidJsIdentifiers() { ... }
```

### Map Sorting

The `toSortedMap()` extension isn't available in the JS standard library. Use the custom extensions in `MapExt.kt` instead:

```kotlin
// For generic maps
val sortedMap = unsortedMap.sortedByKeys()

// For maps with Dependency keys (sorts by componentCode and reservedCode)
val sortedDependencies = dependencyMap.sortedByDependency()
```

### Platform-Specific Code

For platform-specific functionality (like file I/O), use the expect/actual pattern:

```kotlin
// In commonMain:
expect fun readResourceFile(path: String): String

// In jvmMain: 
actual fun readResourceFile(path: String): String {
    return File(path).readText()
}

// In jsMain:
actual fun readResourceFile(path: String): String {
    // JS-specific implementation...
}
```

## Migration Benefits

1. Cross-platform compatibility with a single codebase
2. Better alignment with Kotlin ecosystem through use of kotlinx libraries
3. Future-proofing the codebase for multiple platforms
4. Improved serialization performance with kotlinx.serialization