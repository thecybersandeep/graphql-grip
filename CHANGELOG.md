# Changelog

## 3.0.0

Unreleased.

### Request editor

Payloads are now built from a parsed GraphQL document rather than string matching. A hand written lexer, parser and printer reconcile variable definitions, so every generated payload declares the variables it references and carries their captured values. Payloads that reference no variables are passed through byte for byte, which keeps the whitespace and directive based introspection bypasses intact.

Every attack button was audited by parsing its output. Field duplication sent a bare field name with no arguments or selection set and was rejected by any schema where the root field takes arguments or returns an object; it now sends unaliased copies of the captured field, which is what the technique tests. The depth probe nested `types` inside `__Type`, so every probe past depth two failed on an unknown field rather than on depth. Directive overloading placed directives using the first `{` in the printed field, which corrupted any field with an inline object argument; directives are now positioned through the document model. Mutation fragments conditioned on a type named `Mutation` and the inline fragment bypass on a type named `Query`, neither of which the specification guarantees; both now use inline fragments with no type condition. The engine fingerprint asked for `__schema.description`, which fails on engines predating the 2021 specification draft, and now reports the root type names and directive set instead. The persisted query hash is taken from the query as sent.

The request format buttons changed an internal field only. Because the editor reported itself unmodified, Burp never asked for the rewritten request, so GET, URL encoded and multipart did nothing. The editor now reports a format change as a modification. Switching formats also left the operation in the URL from a previous GET conversion, so the query was sent twice; only the GraphQL parameters are stripped now, and parameters belonging to the endpoint are preserved in both directions.

Alias spray was removed. Batch mutations cover the same ground.

### Schema import and export

SDL import was a regular expression that matched `type Name {` and nothing else. Against a schema of eleven types it imported three: types declaring `implements` were skipped, and interfaces, unions, enums, inputs and scalars were never matched. Field arguments were discarded, every type was recorded as an object, and the query root was assumed to be named `Query`.

Import now uses a type system parser covering schema definitions, objects, interfaces, unions, enums, inputs, scalars, directive definitions, extensions, descriptions, `implements`, arguments with default values and wrapped types. A `schema` block is authoritative, so a type named `Subscription` is not treated as a root when the block does not name it.

Introspection results discarded `interfaces` and `possibleTypes`, so unions and interface implementations were lost on every introspected schema and SDL export wrote `union X = ` with no members. Both are linked now, and export no longer emits empty field blocks, empty unions, an empty schema block or a directive with no expressible location. Export and import round trip.

Type kinds are read from the document instead of guessed from names. A type whose name ends in `Input` is no longer assumed to be an input object.

### Graph view

The graph laid types out alphabetically in rows grouped by kind, with edges drawn across the result. Edges came only from object fields, so input objects, enums, unions and scalars appeared as isolated boxes, and schemas above five hundred types rendered an empty panel labelled as having no schema loaded.

Types are now placed in layers by distance from the root types, ordered within each layer to reduce crossings. Edges are drawn for field types, field argument types, input object fields, implemented interfaces and union members, de duplicated per pair and kind, with self references drawn as loops. A type can be focused to show its neighbourhood in both directions, and a schema beyond the drawing limit focuses the query root instead of rendering nothing. Fit to view and reset layout are available from the toolbar.

### Scanner

The scanner probes POST JSON, GET and form encoded transports, then walks introspection bypasses over each confirmed transport. The transport it finds is carried through to the schema tab, the graph, Repeater, Intruder and the execute action, so a GET only endpoint is no longer sent a POST body.

Cancel did not cancel. It cleared the in progress flag and re enabled the buttons while the work continued, which also allowed a second run to start alongside the first. Scans, blind discovery and endpoint discovery now stop at the next request, and blind discovery cancels the reconstructor it owns. Endpoint discovery and engine fingerprinting did not mark the scanner busy at all, so their buttons stayed live and could be fired repeatedly; all four runs now share one lifecycle.

### Settings

Payload sizes, their ranges and their defaults are defined once and read by both the settings panel and the configuration store, so the panel cannot display a value the extension does not use. The panel no longer stretches its controls, and the Repeater tab says where the sizes come from.

### Burp integration

The Repeater payload box is Burp's native raw editor rather than a Swing text area, so it carries Burp's search, context menu and theming, and the editor now reports its selection to Burp through `selectedData()`. No part of the extension renders text in a plain text area any more.

### Maintenance

The module registry and event bus were never driven by anything and have been removed, along with twenty three configuration keys that nothing read and three unused HTTP client methods. Tooltips are plain text, because Burp renders the markup literally.

Automated tests cover the document parser and printer, payload generation, transport selection and request rewriting, SDL and introspection import, schema export round trips, the graph model and the introspection ladder.

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
