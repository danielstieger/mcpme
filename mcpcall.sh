#!/bin/bash
# mcpcall.sh <url> init | <url> call <tool> '<json-args>' | <url> end     (hält die Session in ./mcp.sid)
U=$1; shift
H=(-H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream')
case $1 in
 init) curl -s -D mcp.hdr -o mcp.init -X POST $U "${H[@]}" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl-test","version":"0.1"}}}'
       grep -i '^Mcp-Session-Id' mcp.hdr | tr -d '\r' | awk '{print $2}' > mcp.sid
       curl -s -o /dev/null -X POST $U "${H[@]}" -H "Mcp-Session-Id: $(cat mcp.sid)" -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
       cat mcp.init; echo;;
 call) curl -s -X POST $U "${H[@]}" -H "Mcp-Session-Id: $(cat mcp.sid)" -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"$2\",\"arguments\":${3:-{\}}}}" | sed -n 's/^data: *//p';;
 end)  curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $U "${H[@]}" -H "Mcp-Session-Id: $(cat mcp.sid)";;
esac
