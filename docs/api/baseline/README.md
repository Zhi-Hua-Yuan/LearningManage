# Stage 8 pre-Spring-AI OpenAPI baseline

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
