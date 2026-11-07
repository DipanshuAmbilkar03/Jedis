# Contributing

Thanks for taking the time to contribute.

## Before you start

- For bug fixes and small improvements, open a pull request directly.
- For larger changes, open an issue first so we can agree on the approach.
- **Security issues must not be reported in public issues.** See [SECURITY.md](SECURITY.md).

## Requirements

- JDK 25
- Maven 3.9+
- Node.js 20+ (only if you are rebuilding the dashboard)

## Build and test

```bash
# Backend
cd mini-redis
mvn test
mvn clean package
java -jar target/mini-redis-1.0.0.jar

# Dashboard (optional rebuild)
cd ../frontend
npm install
npm run build
node update_dashboard_html.js
```

The server listens on port 6380 for Redis clients and port 8080 for the dashboard by default.

## Pull request checklist

- [ ] The change is focused on one problem.
- [ ] `mvn test` passes.
- [ ] New behaviour has a test.
- [ ] No secrets, credentials, or personal data are committed.
- [ ] Documentation is updated when behaviour or configuration changes.
- [ ] The PR description explains the problem and the fix, not just the code.

## Code style

- Follow the existing Java style and package layout.
- Keep the runtime dependency count at zero unless there is a strong reason.
- Prefer explicit, bounded resource use: cap request sizes, connection counts, and command output.
- Do not log secrets or full command payloads.

## Commit messages

Use a short imperative subject line, for example:

```text
fix(dashboard): require a token for state-changing API calls
```

Add a body when the reason for the change is not obvious.

## Governance

Project decisions and the security role are described in [GOVERNANCE.md](GOVERNANCE.md).
