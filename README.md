# GraphQL Grip

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Burp Suite](https://img.shields.io/badge/Burp%20Suite-Montoya%20API-FF6633?style=flat)](https://portswigger.net/burp)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.0-green.svg)](../../releases)

A Burp Suite extension for GraphQL security testing. Fetch schemas, fingerprint backends, and generate attack payloads directly from Repeater.

## What It Does

**Schema Analysis** extracts GraphQL schemas through introspection or blind reconstruction when introspection is disabled. The fingerprinter identifies backend implementations like Apollo, Hasura, Yoga, graphql-java, and others.

**Attack Generation** happens right in the Repeater tab. Select an attack type, tweak the parameters, and fire. Supports DoS patterns (alias overloading, field duplication, circular queries), mutation abuse, directive probing, and multiple introspection bypass techniques.

**Endpoint Discovery** finds GraphQL endpoints automatically and detects GraphiQL/Playground interfaces.

## Installation

Download `graphql-grip-1.0.0.jar` from [Releases](../../releases), or build it yourself. In Burp Suite, go to Extensions > Add and select the JAR. The GraphQL Grip tab appears immediately.

Requires Burp Suite 2023.12+ and Java 17+.

## Quick Start

**From the main tab:** Enter your target URL, hit Scan & Introspect, and browse the schema. Use Fingerprint Engine to identify what's running on the backend.

**From Repeater:** Send any GraphQL request to Repeater, switch to the GraphQL Grip tab, pick an attack type, adjust parameters, generate, send.

## Attack Types

| Category | Attacks |
|----------|---------|
| DoS | Alias Overloading, Width Attack, Field Duplication, Circular Introspection, Fragment Overloading, Array Batching |
| Mutations | Aliased Mutations, Batch Mutations, Mixed Batch, Mutation Fragments |
| Directives | @include/@skip Overloading, @defer/@stream Detection, Directive Discovery |
| Info Disclosure | Full Introspection, Minimal Introspection, Field Suggestions, __typename Probe |
| Bypasses | Newline, Tab, Spacing, Comments, Aliases, Fragments, GET Method |

## Configuration

Settings persist via Burp's preferences:

| Setting | Default | Description |
|---------|---------|-------------|
| Alias Count | 100 | Number of aliases for overloading attacks |
| Batch Count | 10 | Number of queries in batch attacks |
| Field Count | 500 | Number of duplicated fields |
| Directive Count | 50 | Number of directives for overloading |
| Depth Count | 10 | Nesting depth for introspection attacks |
| Fragment Count | 50 | Number of fragments for overloading |

## Engine Detection

Fingerprints Apollo Server, Hasura, GraphQL Yoga, Graphene, graphql-java, Juniper, Sangria, Hot Chocolate, GraphQL PHP, WPGraphQL, AWS AppSync, Ariadne, Strawberry, gqlgen, and Dgraph. Detection is heuristic-based.

## Schema Reconstruction

When introspection is blocked, Grip probes blindly. It tests 64 field names per request using aliases, parses "Did you mean X?" suggestions from errors, traverses nested types breadth-first, and infers return types from error messages. Works against Apollo, Hasura, Hot Chocolate, Sangria, Graphene, Juniper, AppSync, PostGraphile, Relay, Netflix DGS, and others.

## Building from Source

Requires Java 17+ and Git.

```bash
git clone https://github.com/AzraelSec/graphql-grip.git
cd graphql-grip
chmod +x ./gradlew
./gradlew jar or bash ./gradlew jar  
```

On Windows, use `gradlew.bat jar` instead.

Output: `build/libs/graphql-grip-1.0.0.jar`

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

## Contributing

Fork, branch, build, PR. Run `./gradlew build` before submitting to make sure everything compiles.

## License

MIT. See [LICENSE](LICENSE).

## Disclaimer

⚠️ For authorized security testing only. Get permission before testing systems you don't own.

## Author

[Sandeep Wawdane](https://www.linkedin.com/in/sandeepwawdane/)
