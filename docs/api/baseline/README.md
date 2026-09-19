# Stage 8 pre-Spring-AI OpenAPI baseline

## Sealed baseline (never overwritten)

`stage8-pre-spring-ai-v1.0.0-openapi.json` is the complete runtime OpenAPI
document exported from the final Phase 0 candidate. Its adjacent SHA-256 file
is verified before every comparison.

The Release Gate first keeps the existing frontend method/path existence check,
then runs checksum-locked oasdiff 1.28.0 against this document. Any breaking
change to paths, parameters, request/response schemas, types, media types or
response status contracts fails the candidate. Updating this baseline requires
an explicit compatibility review and a new named baseline; released baselines
must never be overwritten.

SpringDoc derives `servers` from the request host and port. The gate archives
the unmodified runtime document, but canonicalizes that volatile field to the
sealed baseline value before diffing; paths and all operation contracts remain
fully compared.

## Derived baseline for the springdoc 2.8.17 toolchain

`stage8-pre-spring-ai-v1.0.0-openapi-springdoc-2.8.17.json` is a **derived**
baseline added by Phase 1 PR 1 (Spring Boot 3.3.6 → 3.5.16). The sealed
baseline above is not modified; this file records the post-upgrade canonical
document so that later phases compare against the toolchain actually in use.

Derivation:

- Source: the runtime `/api/v3/api-docs` document of the Phase 1 PR 1 candidate
  (springdoc-openapi 2.8.17, swagger-core-jakarta 2.2.47,
  `springdoc.api-docs.version=OPENAPI_3_0`).
- `servers` canonicalized to the sealed baseline value
  (`http://127.0.0.1:8123/api`). Every other node is byte-identical to the
  runtime document; verified by comparing both documents with `servers`
  removed.
- Serialization style matches the sealed baseline: compact
  (`separators=(",", ":")`), `ensure_ascii=False`, single trailing LF.

Verdict (`oasdiff 1.28.0`): `breaking` = 0 entries, i.e. no breaking change.
Paths 94, operations 95, no operation added or removed, `servers` identical
after canonicalization.

The only difference against the sealed baseline is 9 leaf nodes, all of them
`deprecated: true` added to the `pages` property of the nine `Page*` schemas:

| schema | property | baseline | runtime |
| --- | --- | --- | --- |
| `PageAiCallLogVO` | `pages` | (absent) | `true` |
| `PageAnalysisReportVO` | `pages` | (absent) | `true` |
| `PageCleanupRunVO` | `pages` | (absent) | `true` |
| `PageKnowledgeEventVO` | `pages` | (absent) | `true` |
| `PageOpsFailureVO` | `pages` | (absent) | `true` |
| `PageProjectVo` | `pages` | (absent) | `true` |
| `PageTaskAssignmentHistoryVO` | `pages` | (absent) | `true` |
| `PageTaskVo` | `pages` | (absent) | `true` |
| `PageWeeklyReviewSharedVO` | `pages` | (absent) | `true` |

Cause: MyBatis-Plus 3.5.7 → 3.5.17 marked `IPage#getPages()` as `@Deprecated`
(verified with `javap` against both `mybatis-plus-core` jars), and
swagger-core 2.2.47 reflects that flag onto the generated schema. The flag is
accurate metadata, not a contract change, so it is recorded here instead of
being suppressed. These `Page*` schemas are not source classes; swagger-core
derives their names from the `IPage<T>` return types in the controllers.

### Suppressed change: `@NotBlank`-derived `minLength`

swagger-core 2.2.47 (pulled in transitively by springdoc 2.8.17) added
`applyNotBlankConstraint`, so it now derives `minLength: 1` from `@NotBlank`.
The sealed baseline was generated with swagger-core 2.2.19, which did not.
Three fields are affected — `CleanupRunCreateRequest.clientRequestId`,
`TeamJoinRequest.inviteCode`, `TeamMemberRoleUpdateRequest.role` — and oasdiff
reports them as `request-property-min-length-increased`, which would fail
`scripts/ci/verify-runtime-api-contract.sh`.

Because PR 1 must not change the published contract, those three values are
removed at generation time by
`OpenApiConfig#notBlankDerivedMinLengthCompatibility`. Spring Boot 3.5 upgrade
does not change runtime validation: `@NotBlank` still rejects blank values
exactly as before. See that method's Javadoc for the full analysis, including
why `PropertyCustomizer` is not a usable extension point here.

## Checksums

| file | SHA-256 |
| --- | --- |
| `stage8-pre-spring-ai-v1.0.0-openapi.json` | `6251FC7E067BFFCDF648B2A3BC939C1BB808467DEBECC4DFB0E13339DAE6F1B2` |
| `stage8-pre-spring-ai-v1.0.0-openapi-springdoc-2.8.17.json` | `60153CCE7CCE2548F9E9532164C540BDD234E00F419B65E6A2428C85A6A5EE51` |

Both `.sha256` sidecars pass `sha256sum --check`.
