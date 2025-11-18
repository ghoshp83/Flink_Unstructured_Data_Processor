# Contributing to Flink Unstructured Data Processor

Thank you for your interest in contributing! This document provides guidelines for contributing to this project.

## Code of Conduct

- Be respectful and inclusive
- Focus on constructive feedback
- Help others learn and grow

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in Issues
2. Create a new issue with:
   - Clear title and description
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment details (Java version, Flink version, etc.)

### Suggesting Enhancements

1. Open an issue describing the enhancement
2. Explain why this enhancement would be useful
3. Provide examples if possible

### Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass (`mvn test`)
6. Commit your changes (`git commit -m 'Add amazing feature'`)
7. Push to the branch (`git push origin feature/amazing-feature`)
8. Open a Pull Request

## Development Setup

```bash
# Clone the repository
git clone https://github.com/yourusername/Flink_Unstructured_Data_Processor.git

# Build the project
mvn clean install

# Run tests
mvn test

# Run with code coverage
mvn clean verify
```

## Coding Standards

- Follow Java naming conventions
- Write meaningful commit messages
- Add JavaDoc for public methods
- Keep methods focused and concise
- Write unit tests for new features
- Maintain code coverage above 70%

## Testing

- Write unit tests for all new functionality
- Ensure existing tests pass
- Add integration tests for complex features
- Test with different log formats

## Documentation

- Update README.md for new features
- Add JavaDoc comments for public APIs
- Update configuration examples
- Include usage examples

## Questions?

Feel free to open an issue for any questions or clarifications.
