# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-09

## Context
Six months from now, nobody will remember *why* the partition key is `accountId` or why we don't use H2. Interviewers and new contributors ask "why", not "what".

## Decision
Every significant, hard-to-reverse decision gets an ADR in `docs/adr/` with these sections: Context, Decision, Consequences, Alternatives considered.

## Consequences
- Decisions are reviewable in pull requests like code.
- A little extra writing per decision.
