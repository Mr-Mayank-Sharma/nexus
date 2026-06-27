# Testing Guide

## Frontend
- **Framework:** Vitest + React Testing Library
- **Run:** `npx vitest`
- **Watch mode:** `npx vitest --watch`
- **Coverage:** `npx vitest --coverage`

### Writing tests
- Place tests in `src/test/` mirroring the source structure
- Use `renderWithProviders` wrapper for components that need router/auth/theme
- Mock external API calls with `vi.mock()`

## Backend
- **Framework:** JUnit 5 + Mockito
- **Run:** `mvn test`
- **Integration tests:** Add `@SpringBootTest` for full context

### Writing tests
- Unit tests: `src/test/java/com/nexus/oms/service/*Test.java`
- Controller tests: `src/test/java/com/nexus/oms/controller/*Test.java`
- Repository tests: Use `@DataJpaTest` with embedded database
