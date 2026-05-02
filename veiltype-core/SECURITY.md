# Security Policy

## Scope

This repository publishes the open-core technical layer for VeilType:
protocol notes, schemas, vectors, and a small reference implementation.

It does not contain the full Android product shell, signing pipeline, or
commercial distribution logic.

## Reporting a security issue

If you believe you found a security issue in the published protocol docs,
test vectors, or reference implementation, please report it privately first.

Recommended report format:
- affected file or component
- impact summary
- reproduction steps
- proof-of-concept input or vector, if relevant
- suggested mitigation, if known

Do not open a public issue for an unpatched vulnerability.

## Disclosure expectations

- Please allow reasonable time to validate and fix the issue before public disclosure.
- After a fix is ready, a public note or changelog entry can reference the issue at a high level.

## Out of scope

The following are out of scope for this repository:
- Android UI bugs outside the public core
- payment or licensing flows
- app store distribution issues
- deployment secrets or local workstation configuration

