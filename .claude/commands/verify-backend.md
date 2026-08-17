Verify the backend against the quality gates already defined in `CLAUDE.md`:

- Java 25 compilation succeeds.
- Unit and integration tests pass.
- Formatting and static analysis pass.
- No secrets are present in the diff or logs.
- Docker health checks pass (`deploy/local/docker-compose.yml`).
- Logs are structured.
- Metrics and traces are available (OpenTelemetry/Micrometer/Prometheus).
- Documentation and ADRs are updated if the change touched architecture or scope.

Report each gate as pass/fail with the command used and its output, not just a verdict. Do not attempt to fix failures silently — report them and propose the fix before applying it.
