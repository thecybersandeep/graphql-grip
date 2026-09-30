# GraphQL Grip

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Burp Suite](https://img.shields.io/badge/Burp%20Suite-Montoya%20API-FF6633?style=flat)](https://portswigger.net/burp)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-2.0.0-green.svg)](../../releases)

Burp Suite extension for GraphQL security testing. Fetch schemas, fingerprint backends, and generate attack payloads from within Burp.

**Main tab:** `Schema Analysis & Testing`

<img width="1624" height="966" alt="image" src="https://github.com/user-attachments/assets/59b997fb-a4c5-43c8-a44a-970a6c39822b" />

**Repeater tab:** `Attack Payload Generator`

<img width="1365" height="966" alt="image" src="https://github.com/user-attachments/assets/594a6e7e-2af4-41ab-a47b-0778a6fee14a" />

## What It Does

**Schema Analysis:** runs introspection to pull the full schema. Falls back to blind reconstruction if introspection is off. Also fingerprints the backend (Apollo, Hasura, Yoga, graphql-java, etc.).

**Attack Generation:** lives in the Repeater tab. Pick an attack type, adjust its settings, generate the payload, and send it. Covers DoS (alias overloading, field duplication, circular queries), mutation abuse, directive probing, and introspection bypass techniques.

**Endpoint Discovery:** finds GraphQL endpoints and detects exposed GraphiQL, Playground, and Altair interfaces.

## Installation

Grab `graphql-grip-2.0.0.jar` from [Releases](../../releases), or build from source. Go to Extensions > Add in Burp, select the JAR.

Requires Burp Suite 2026.7+ and Java 17+.

## Quick Start

**Main tab:** drop your target URL, hit Scan & Introspect, browse the schema. Use Fingerprint Engine to identify what's running underneath.

**Repeater:** send a request to Repeater, switch to the GraphQL Grip tab, choose an attack type, adjust its settings, generate the payload, and send it. The tab is always available in Repeater, including for requests that do not yet contain a GraphQL operation.

## Large Schema Support

Tested against production APIs with 18,000+ types. The schema tree loads lazily with pagination (50 items per page), introspection runs off the UI thread, and schema imports parse in the background.

## Attack Types

| Category | Attacks |
|----------|---------|
| DoS | Alias Overloading, Width Attack, Field Duplication, Circular Introspection, Fragment Overloading, Array Batching |
| Mutations | Aliased Mutations, Batch Mutations, Mixed Batch, Mutation Fragments |
| Directives | @include/@skip Overloading, @defer/@stream Detection, Directive Discovery |
| Info Disclosure | Full Introspection, Minimal Introspection, Field Suggestions, __typename Probe |
| Bypasses | Newline, Tab, Spacing, Comments, Aliases, Fragments, GET Method |

## Configuration

Attack payload settings are available under **Settings > GraphQL Grip** and persist through Burp's preferences:

| Setting | Default | What it controls |
|---------|---------|------------------|
| Alias Count | 100 | Aliases for overloading attacks |
| Batch Count | 10 | Queries in batch attacks |
| Field Count | 500 | Duplicated fields |
| Directive Count | 50 | Directives for overloading |
| Depth Count | 10 | Nesting depth for introspection attacks |
| Fragment Count | 50 | Fragments for overloading |

## Engine Detection

Fingerprints: Apollo Server, Hasura, GraphQL Yoga, Graphene, graphql-java, Juniper, Sangria, Hot Chocolate, GraphQL PHP, WPGraphQL, AWS AppSync, Ariadne, Strawberry, gqlgen, Dgraph. All heuristic-based.

## Schema Reconstruction

When introspection is blocked, Grip probes blindly to rebuild the schema from error messages and field suggestions.

## Build from Source

Java 17+ and Git required.

```bash
git clone https://github.com/thecybersandeep/graphql-grip.git
cd graphql-grip
chmod +x ./gradlew
./gradlew jar
```

Windows: `gradlew.bat jar`

Output: `build/libs/graphql-grip-2.0.0.jar`

## Project Structure

```
src/main/java/com/grip/graphql/
├── GripExtender.java          # Burp extension entry point
├── GripCore.java              # Core coordinator
├── GripConfig.java            # Configuration management
├── api/                       # Interfaces
├── editor/                    # Repeater tab integration
├── event/                     # Event bus system
├── http/                      # HTTP client with rate limiting
├── model/                     # Data models (schema, types, fields)
├── schema/                    # Introspection & reconstruction
├── security/                  # Engine fingerprinting
└── ui/                        # Main tab & theming
```

## Changelog

### v2.0.0

Updated to Montoya API 2026.7. Attack configuration now lives under **Settings > GraphQL Grip**, and the GraphQL Grip editor is always available in Repeater.

Schema queries, test responses, and type details now use Burp's native raw editor. Request validation reports invalid endpoints, empty queries, and malformed variables before a request is built.

Endpoint discovery now parses response data instead of matching arbitrary text. HTML error pages, generic JSON responses, and REST error envelopes are no longer reported as GraphQL endpoints. Probe failures are counted and written to Burp's error output.

Fixed GET, URL encoded, multipart, batch, raw GraphQL, and persisted query handling. Custom headers are safe to use across the UI and scanner threads. Removed unused runtime dependencies and stopped bundling the Montoya API in the extension JAR.

Full release history is available in [CHANGELOG.md](CHANGELOG.md).

### v1.0.1

Large schemas (thousands of types) no longer freeze the UI the tree loads lazily with async pagination, and file imports parse in the background.

Auth headers (tokens, cookies, custom headers) from the original request now carry through automatically to introspection and schema tab requests. Transport headers like Host and Content-Length are skipped.

Fixed scalar and enum return types generating empty `{ }` selection sets. Arguments now included for subscriptions. Send to Repeater and Intruder works correctly with the full original request template.

HTTP client rate limiting, timeout handling, and auth error checking cleaned up  no more duplicate header application or scattered timestamp tracking.

Graph view edge rendering now uses a single pass. Schemas over 500 types skip the graph view to stay responsive.

Removed dead code, fixed compiler warnings, cleaned up unused imports and empty catch blocks.

### v1.0.0

Initial release.

## Contributing

Fork it, branch it, build it, PR it. Run `./gradlew build` before submitting.

## License

MIT. See [LICENSE](LICENSE).

## Disclaimer

Authorized security testing only. Get permission before testing anything you don't own.

## Author

[Sandeep Wawdane](https://www.linkedin.com/in/sandeepwawdane/)
