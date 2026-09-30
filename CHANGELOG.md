# Changelog

## 2.0.0

Released 30 September 2026.

### Burp integration

GraphQL Grip now uses Montoya API 2026.7. Attack settings have moved to **Settings > GraphQL Grip**, where Burp users expect extension configuration to live.

The GraphQL Grip request editor is always available in Repeater. Proxy, Logger, Scanner, and other message editors still show it only when a request looks like GraphQL.

Schema queries, test responses, and schema type details now use Burp's native raw editor.

### Scanner reliability

Endpoint discovery now parses JSON response structure. It no longer treats an HTML page as GraphQL just because the page contains terms such as `GraphQL` or `__typename`.

The scanner now distinguishes a GraphQL endpoint with blocked introspection from a URL that is not a GraphQL endpoint. Invalid base URLs are rejected before scanning, failed probes are counted, and background failures are written to Burp's error output.

Custom request headers now use a concurrent map so scanner work cannot race with changes made in the UI.

### Request editor

Requests are validated before they are rebuilt. The editor checks the endpoint, query text, JSON body, variables, and batch entries and reports problems in its status line.

GraphQL request detection now supports JSON bodies, batch requests, GET parameters, URL encoded forms, multipart operations, raw GraphQL documents, and persisted queries.

GET, URL encoded, and multipart payload generation now preserves the GraphQL query in the correct transport format.

### Maintenance

The extension version is now reported as `2.0.0`. The Montoya API is a compile time dependency and is no longer bundled in the extension JAR. Unused runtime dependencies and unused imports were removed.

The `2.0.0` release JAR is 465 KB, compared with 5.41 MB for `1.0.1`. The smaller package is expected because Burp supplies the Montoya API and GraphQL Grip no longer depends on GraphQL Java or RSyntaxTextArea. On the GitHub release page, `48` is the download count for `1.0.1`, not part of its file size.

Automated tests cover GraphQL response classification and Repeater request detection, including HTML false positives and generic REST responses.

## 1.0.1

Improved large schema handling with lazy tree loading, pagination, and background parsing.

Preserved authentication and custom headers when sending introspection and schema requests. Fixed scalar and enum query generation, subscription arguments, Repeater and Intruder request templates, HTTP rate limiting, timeout handling, and graph rendering for large schemas.

## 1.0.0

Initial release.
