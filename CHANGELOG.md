# Changelog

All notable changes to AWS Kit will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.2] - 2024-01-15

### Added
- **S3 Presigned URL Upload**: New `generatePresignedPutUrl` methods for direct client-side uploads
  - Support for content type, metadata, and storage class options
  - Comprehensive test coverage with 4 test scenarios
  - Detailed Korean documentation with usage examples
- **Security Improvements**: Fixed logging exposure of AWS SDK request details
- **Documentation**: Added comprehensive README files for `aws-secrets-client` and `aws-sns-client`
- **Quality Infrastructure**:
  - OWASP Dependency Check integration for security scanning
  - Standardized Jacoco configuration across all modules
  - Architecture documentation with detailed system design

### Changed
- **Dependency Management**: Standardized AWS SDK version to 2.28.11 across all modules
- **Logging Security**: Changed AWS SDK request logging from DEBUG to WARN to prevent credential exposure
- **Build Configuration**: Standardized dependency scopes (`compileOnly` for Spring Boot dependencies)

### Fixed
- **Version Consistency**: Fixed AWS SDK version mismatch in `aws-secrets-client` (2.28.0 → 2.28.11)
- **Dependency Scopes**: Fixed inappropriate `implementation` scope for Spring Boot in `aws-sqs-consumer`
- **Security**: Removed DEBUG logging for `software.amazon.awssdk.request` to prevent credential leakage

### Documentation
- **Architecture Guide**: Comprehensive system architecture documentation
- **Contributing Guidelines**: Detailed contribution process and coding standards
- **API Documentation**: Enhanced README files with practical examples and security considerations

## [1.0.1] - 2024-01-10

### Added
- Initial release with core AWS service clients
- Spring Boot auto-configuration support
- Type-safe AWS SDK abstractions

### Modules
- `aws-sdk-commons`: Common configuration and utilities
- `aws-s3-client`: S3 file storage operations
- `aws-dynamodb-client`: DynamoDB NoSQL database operations
- `aws-sqs-client`: SQS message queue operations
- `aws-sqs-consumer`: SQS message consumption framework
- `aws-lambda-client`: Lambda function invocation
- `aws-secrets-client`: Secrets Manager and Parameter Store
- `aws-sns-client`: Simple Notification Service

### Features
- **Type Safety**: Custom value objects wrapping AWS SDK types
- **Async-First**: All operations return `CompletableFuture<T>`
- **Spring Boot Integration**: Auto-configuration and property binding
- **LocalStack Testing**: Comprehensive integration test support
- **Java 21**: Modern Java features including records and virtual threads

### Security
- **Credential Chain**: Secure credential provider chain implementation
- **IAM Best Practices**: Examples following least privilege principle
- **Encryption Support**: Built-in support for AWS KMS encryption

### Testing
- **Unit Tests**: 88 test files with 955 test methods
- **Integration Tests**: LocalStack container-based testing
- **Architecture Tests**: ArchUnit enforcement of design principles
- **Security Tests**: Vulnerability prevention and concurrent safety tests

## [Unreleased]

### Planned
- **CloudWatch Integration**: Metrics and logging enhancements
- **Event Bridge Support**: Event-driven architecture components
- **Performance Optimization**: Enhanced connection pooling and retry strategies
- **Documentation Site**: Comprehensive documentation website

---

## Version History

| Version | Release Date | Key Features |
|---------|--------------|--------------|
| 1.0.2   | 2024-01-15   | S3 Presigned URLs, Security fixes, Quality improvements |
| 1.0.1   | 2024-01-10   | Initial release, Core AWS services |

## Migration Guide

### From 1.0.1 to 1.0.2

No breaking changes. All existing APIs remain compatible.

**New Features Available:**
- S3 presigned URL upload methods
- Enhanced security logging configuration
- Improved documentation and examples

**Recommended Actions:**
1. Update logging configuration to use new secure defaults
2. Consider using new S3 presigned URL features for direct uploads
3. Run OWASP dependency check: `./gradlew dependencyCheckAnalyze`

## Support

- **GitHub Issues**: [Report bugs and request features](https://github.com/ryu-qqq/aws-kit/issues)
- **Documentation**: See module README files for detailed usage guides
- **Examples**: Check test files for comprehensive usage examples