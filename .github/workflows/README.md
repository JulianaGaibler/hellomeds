# CI/CD Workflows

This directory is ready for GitHub Actions workflows.

## Planned workflows

### `android.yml`
- **Trigger:** push/PR to main
- **Steps:** checkout, setup JDK 17, Gradle cache, `./hm verify`

### `ios.yml`
- **Trigger:** push/PR to main
- **Runner:** macOS (required for Xcode)
- **Steps:** checkout, setup JDK 17, `./hm build ios`

### `test.yml`
- **Trigger:** push/PR to any branch
- **Steps:** checkout, setup JDK 17, `./hm test`
