# Integrations: client changes as patches (until the forks exist)

The desktop client changes are kept here as patch files against the pinned upstream commit. When the GitHub forks exist (D26), each patch is applied on its fork branch and becomes a pull request inside the fork.

| Patch | Against | Ticket | Notes |
|---|---|---|---|
| 0001-registration-api-liveness-spi.patch | registration-client `release-1.2.0.x` @ c1cc4e7fc1625ccc6ef26282910934bfcb9d164c | #40 | Adds the liveness SPI (`LivenessFacade`, `LivenessService`, `LivenessResult`, `LivenessOutcome`) and a fail-closed stub. Adds JUnit test scope to `registration-api-stub-impl/pom.xml`. |

Apply with: `git apply integrations/patches/<file>` inside a checkout at the pinned commit.

Not yet pinned: the `develop` line (failed, #05), `android-registration-client` (pin pending the Flutter decision D44 and the fork), `mosip-mock-services` and `android-camera-mds` (used locally as build copies, not forked yet).
