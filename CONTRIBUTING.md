# Contributing to AWS Kit

We welcome contributions to AWS Kit! This document provides guidelines for contributing to the project.

## Code of Conduct

By participating in this project, you agree to abide by our code of conduct. Please be respectful and constructive in all interactions.

## Getting Started

### Prerequisites

- Java 21 or higher
- Gradle 8.x
- Git
- Docker (for LocalStack testing)

### Development Setup

1. **Fork and Clone**
   ```bash
   git clone https://github.com/your-username/aws-kit.git
   cd aws-kit
   ```

2. **Build the Project**
   ```bash
   ./gradlew clean build
   ```

3. **Run Tests**
   ```bash
   ./gradlew test
   # For integration tests with LocalStack
   ./gradlew test -Drun.integration.tests=true
   ```

## Contribution Workflow

### 1. Issue First

- Check existing [issues](https://github.com/ryu-qqq/aws-kit/issues) before creating new ones
- For new features, create an issue to discuss the proposal first
- For bugs, provide detailed reproduction steps

### 2. Branch Strategy

```bash
# Feature development
git checkout -b feature/your-feature-name

# Bug fixes
git checkout -b fix/issue-description

# Documentation updates
git checkout -b docs/topic-description
```

### 3. Development Guidelines

#### Code Style

- **Java 21 Features**: Use modern Java features (records, pattern matching, virtual threads)
- **Naming Conventions**: Follow Java conventions (camelCase for methods, PascalCase for classes)
- **No Lombok**: Use Java 21 records and standard constructors instead
- **Documentation**: All public APIs must have comprehensive JavaDoc

#### Architecture Patterns

- **Hexagonal Architecture**: Follow the established pattern
- **Type Safety**: Wrap AWS SDK types in custom value objects
- **Async-First**: All I/O operations must return `CompletableFuture<T>`
- **Immutability**: Use immutable objects where possible

#### Example Code Style

```java
// ✅ Good: Type-safe, immutable, well-documented
/**
 * S3 객체 키를 나타내는 값 객체
 *
 * @param value S3 객체 키 값
 */
public record S3Key(String value) {
    public S3Key {
        Objects.requireNonNull(value, "S3 key cannot be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be empty");
        }
    }
}

// ❌ Bad: Mutable, no validation
public class S3Key {
    private String value;

    public void setValue(String value) {
        this.value = value;
    }
}
```

### 4. Testing Requirements

#### Test Coverage
- **Minimum Coverage**: 80% line coverage, 80% method coverage
- **Unit Tests**: Test all business logic
- **Integration Tests**: Use LocalStack for AWS service testing
- **Architecture Tests**: Use ArchUnit for structure validation

#### Test Structure

```java
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Test
    @DisplayName("Should upload object successfully")
    void shouldUploadObjectSuccessfully() {
        // Given
        String bucket = "test-bucket";
        S3Key key = new S3Key("test-object");
        byte[] data = "test data".getBytes();

        // When
        CompletableFuture<String> result = s3Service.putObject(bucket, key, data);

        // Then
        assertThat(result).succeedsWithin(Duration.ofSeconds(5));
        String etag = result.join();
        assertThat(etag).isNotNull();
    }
}
```

### 5. Documentation

#### Required Documentation
- **README**: Update module README if adding features
- **JavaDoc**: All public APIs must be documented
- **Architecture**: Update ARCHITECTURE.md for structural changes
- **Changelog**: Add entry to CHANGELOG.md

#### Documentation Style

```java
/**
 * S3 버킷에서 객체를 조회합니다.
 *
 * 한국어 설명:
 * 지정된 버킷과 키를 사용하여 S3 객체를 비동기적으로 조회합니다.
 * 객체가 존재하지 않는 경우 NoSuchKeyException이 발생합니다.
 *
 * @param bucket S3 버킷 이름 (null 불가)
 * @param key S3 객체 키 (null 불가)
 * @return CompletableFuture<S3Object> 조회된 S3 객체
 * @throws IllegalArgumentException bucket 또는 key가 null인 경우
 *
 * 사용 예제:
 * <pre>{@code
 * s3Service.getObject("my-bucket", new S3Key("path/to/file.txt"))
 *     .thenAccept(object -> {
 *         // 객체 처리 로직
 *     })
 *     .exceptionally(throwable -> {
 *         if (throwable.getCause() instanceof NoSuchKeyException) {
 *             log.warn("Object not found");
 *         }
 *         return null;
 *     });
 * }</pre>
 */
CompletableFuture<S3Object> getObject(String bucket, S3Key key);
```

## Pull Request Process

### 1. Before Submitting

- [ ] All tests pass: `./gradlew test`
- [ ] Code coverage meets requirements: `./gradlew jacocoTestReport`
- [ ] Architecture tests pass: `./gradlew test --tests "*ArchUnit*"`
- [ ] Security scan passes: `./gradlew dependencyCheckAnalyze`
- [ ] Documentation is updated

### 2. PR Description Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed

## Checklist
- [ ] Code follows project conventions
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] Tests pass
```

### 3. Review Process

1. **Automated Checks**: All CI checks must pass
2. **Code Review**: At least one maintainer review required
3. **Architecture Review**: For significant changes
4. **Documentation Review**: For public API changes

## Specific Contribution Areas

### Adding New AWS Service Support

1. **Create Module Structure**
   ```
   aws-{service}-client/
   ├── src/main/java/com/ryuqq/aws/{service}/
   │   ├── types/           # Value objects
   │   ├── service/         # Service interface
   │   ├── impl/           # Implementation
   │   └── config/         # Auto-configuration
   └── src/test/java/
   ```

2. **Define Value Objects**
   ```java
   public record ServiceKey(String value) {
       // Validation logic
   }
   ```

3. **Create Service Interface**
   ```java
   public interface ServiceNameService {
       CompletableFuture<Result> operation(ServiceKey key);
   }
   ```

4. **Implement Auto-Configuration**
   ```java
   @Configuration
   @ConditionalOnClass(ServiceAsyncClient.class)
   public class ServiceAutoConfiguration {
       // Bean definitions
   }
   ```

### Improving Existing Modules

- **Performance Optimization**: Focus on async patterns and connection pooling
- **Error Handling**: Improve exception handling and retry logic
- **Type Safety**: Add more validation to value objects
- **Testing**: Increase coverage and add edge cases

### Documentation Improvements

- **API Documentation**: Enhance JavaDoc with examples
- **Guides**: Add usage guides and best practices
- **Architecture**: Update architecture documentation for changes

## Release Process

### Version Strategy

- **Semantic Versioning**: MAJOR.MINOR.PATCH
- **MAJOR**: Breaking changes
- **MINOR**: New features, backwards compatible
- **PATCH**: Bug fixes, backwards compatible

### Release Checklist

1. **Update Version**: Update `gradle.properties`
2. **Update Changelog**: Add release notes to `CHANGELOG.md`
3. **Tag Release**: Create annotated git tag
4. **GitHub Release**: Create release with notes
5. **Documentation**: Update version references

## Getting Help

- **GitHub Issues**: For bugs and feature requests
- **GitHub Discussions**: For questions and general discussion
- **Code Review**: For implementation guidance

## Recognition

Contributors are recognized in:
- GitHub contributors list
- Release notes
- Special recognition for significant contributions

Thank you for contributing to AWS Kit! 🚀