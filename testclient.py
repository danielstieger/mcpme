import json,urllib.request,re
class Mcp:
    def __init__(s,url):
        s.url=url; s.sid=None; s.n=0
        s._post({"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"py-test","version":"0.2"}}})
        s._post({"jsonrpc":"2.0","method":"notifications/initialized"})
    def _post(s,body):
        h={'Content-Type':'application/json','Accept':'application/json, text/event-stream'}
        if s.sid: h['Mcp-Session-Id']=s.sid
        req=urllib.request.Request(s.url,data=json.dumps(body).encode(),headers=h,method='POST')
        with urllib.request.urlopen(req,timeout=120) as r:
            if not s.sid: s.sid=r.headers.get('Mcp-Session-Id')
            raw=r.read().decode()
        m=re.search(r'^data:\s*(\{.*\})\s*$',raw,re.M)
        return json.loads(m.group(1)) if m else (json.loads(raw) if raw.strip() else None)
    def call(s,tool,**args):
        s.n+=1
        r=s._post({"jsonrpc":"2.0","id":s.n,"method":"tools/call","params":{"name":tool,"arguments":args}})['result']
        return r.get('isError'), r.get('structuredContent') or r['content'][0]['text']
    def end(s):
        req=urllib.request.Request(s.url,headers={'Mcp-Session-Id':s.sid},method='DELETE'); urllib.request.urlopen(req,timeout=60).read()
def brief(res):
    err,sc=res
    if isinstance(sc,str): return f"ERR={err} :: {sc[:330]}"
    out=f"ERR={err} open={[(o['handle'],o['title'].strip(),'*' if o['active'] else '','dirty' if o['unsavedChanges'] else '') for o in sc.get('openCommands',[])]}"
    for m in sc.get('messages',[]): out+=f"\n     msg {m['level']}: {m['text'][:200]}"
    for m in sc.get('pageMessages',[]): out+=f"\n     page {m['level']}: {m['text'][:200]}"
    if 'page' in sc: out+=f"\n     page={sc['page']}"
    return out
def action(sc,label): return next(a['action'] for a in sc['conclusionActions'] if a['label']==label)
def field(sc,name):
    for f in sc.get('forms',[]):
        for x in f['fields']:
            if x['field']==name: return x
