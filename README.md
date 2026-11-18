```text
 __  __               __  __  _____
|  \/  |  ___  _ __  |  \/  || ____|
| |\/| | / __|| '_ \ | |\/| ||  _|
| |  | || (__ | |_) || |  | || |___
|_|  |_| \___|| .__/ |_|  |_||_____|
              |_|
```

# McpME

An MCP server that lets an agent operate a MoWare business application the way a person does:
through the application's commands and pages, with that person's permissions.

McpME is a toolkit implementation of the MoWare DataUX runtime, next to the browser and desktop
toolkits. The application model stays unchanged. Where the other toolkits draw a window, McpME
hands the same content to the agent as data.

## Why

- **The agent sees what a person sees.** Pages, fields, tables and messages come from the same
  runtime that serves the user interface. Nothing is exposed that the application does not show.
- **The agent can do what that person may do.** Every permission check, validation and business
  rule of the application applies, because the agent goes through the same commands.
- **Every action is attributable.** The agent never works on its own account. It always acts on
  behalf of a signed-in person, and the application records it under that person's name.

## What an agent gets

Ten tools, the same for every application:

| Tool | Purpose |
| --- | --- |
| `get_overview` | Commands that can be started now, and the commands already open |
| `describe_command` | Parameters and documentation of a command |
| `start_command` | Start a command from an entry, or directly with parameter values |
| `get_state` | Page, messages, forms, tables, commands and conclusion actions of the active command |
| `get_rows` | Rows of a table by range or text search |
| `set_fields` | Set form field values |
| `select_rows` | Set the row selection of a table |
| `conclude` | Run a conclusion action of the page, such as saving or moving on |
| `cancel_command` | Cancel the active command |
| `switch_command` | Make another open command the active one |

Every changing tool returns the full state of the active command, so the agent does not have to
ask again. Documents the application produces arrive as URLs, charts as their data.

A session with an order application looks like this:

```text
start_command(label: "Search for orders")
get_rows(table: "Order", search: "order 77")              -> position 76
start_command(entry: "Order-1", table: "Order", rows: [76])
start_command(label: "Edit Order Position", table: "OrderPosition", rows: [2])
conclude(action: 964284453, fields: {"posValue": "42,50"})
start_command(label: "Print Order")                        -> order78.pdf
conclude(action: 190784468)                                -> saved
```

## Requirements

- Java 21 or newer
- Tomcat 11 (Jakarta Servlet 6.1)
- A MoWare installation: the runtime jars from the MoWare plugin and the generated classes of
  an application
- The [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) and Nimbus JOSE + JWT,
  fetched by Gradle

McpME does not run without the MoWare platform. This repository contains the server only.

## Build and deploy

The paths to the MoWare plugin, the platform sources, the application and Tomcat are set in
`gradle.properties`. The values in the repository are those of the development machine.

```sh
./gradlew deploy
```

This packs the runtime jars into the plugin, builds one WAR containing McpME, the runtime and the
application, and copies it to Tomcat. The MCP endpoint is then at

```text
http://<host>:<port>/<application>/mcp
```

## Configuration

An application gets its own configuration (OFXConfig) with McpME's factories:

- `org.modellwerkstatt.mcpme.core.McpAppFactory` as application factory
- `org.modellwerkstatt.mcpme.print.McpPrintFactory` as print factory

Settings of the application factory:

| Setting | Default | Meaning |
| --- | --- | --- |
| `FixedUser` | none | Sign in every session as this user. For development only; turns sign-in off. |
| `MaxRowsPerTable` | `20` | Rows of a table in one result |
| `MaxOpenCommands` | `5` | Commands that may be open side by side |
| `ShowFolded` | `true` | Include fields and columns the user interface folds away |
| `SessionIdleMinutes` | `60` | Idle time after which a session is closed |
| `PublicUrl` | from the request | Address of the endpoint as clients see it |

## Sign-in

Without `FixedUser`, McpME is an OAuth resource server. It checks the access token of every
request, answers `401` with a pointer to its metadata (RFC 9728) and binds the MCP session to the
person from the token.

Sign-in through Entra ID is configured with one `EntraIdMcpAuth` element: tenant, client id and
secret of the app registration, the scope, and where the user name comes from, either a claim of
the token or a Microsoft Graph query.

Two things to know when using Entra ID:

- Entra ID has no dynamic client registration. The MCP client needs a registered client id, for
  example `claude mcp add --transport http --client-id <id> --callback-port <port> ...`.
- Entra ID rejects the `resource` parameter MCP clients send unless it matches the application id
  URI. With `Relay` switched on, which is the default, McpME passes sign-in and token requests on
  to Entra ID without it.

MCP clients also look for the metadata at the root of the host. That is outside the web
application and needs a rewrite rule in Tomcat or in the reverse proxy.

## Documentation

The documentation is in German, in [`docu/`](docu):

- [`mcp-spezifikation.md`](docu/mcp-spezifikation.md): the specification, with the decisions
  taken and the findings from testing
- [`toolkit-schnittstelle.md`](docu/toolkit-schnittstelle.md) and the other `toolkit-*.md`
  files: the toolkit interface of the runtime that McpME implements

## Status

McpME is a working prototype. It has been tested against one sample application with Claude Code
as the agent, including sign-in through an Entra ID test tenant. It has not run against a
production application.

Known gaps:

- File upload is not supported.
- The user name through Microsoft Graph is implemented but untested.
- Image fields and links are untested.
- The URLs of generated documents are not protected by McpME.
