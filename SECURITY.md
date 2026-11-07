# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| Latest release | Yes |
| `main` branch | Yes (development) |
| Older releases | No |

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Use GitHub's private vulnerability reporting:

1. Open https://github.com/DipanshuAmbilkar03/Jedis/security/advisories/new
2. Include the affected version or commit, a description of the issue, reproduction steps, and the impact you believe it has.

GitHub private vulnerability reporting is the only intake channel. If it is temporarily unavailable, open a minimal public issue asking for a private contact channel without including vulnerability details.

You should receive an acknowledgement within **3 business days**. I aim to triage within **7 days** and to agree on a disclosure timeline with you. Please give me a reasonable window to fix the issue before publishing details.

## Scope

In scope:

- RESP2 parsing and command routing
- Authentication and ACL handling
- TLS configuration and certificate handling
- AOF/RDB persistence and file handling
- Replication and cluster communication
- The HTTP dashboard and its API (`/api/*`)
- Default configuration and deployment guidance

Out of scope:

- Vulnerabilities in third-party dependencies (the project has no runtime dependencies, but build and test dependencies are still in scope for supply-chain reports)
- Issues that require an already-compromised host or physical access
- Denial of service caused solely by a deliberately misconfigured deployment with authentication disabled and the server exposed to the internet
- Social engineering

## Safe harbour

I will not pursue legal action against researchers who:

- act in good faith,
- test only against their own instance,
- avoid accessing, modifying, or exfiltrating data that is not theirs,
- report the issue privately and give a reasonable time to fix it before disclosure.

## Recognition

With your permission, I will credit you in the release notes and the advisory. There is currently no paid bounty program.
