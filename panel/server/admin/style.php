<style>
:root {
    --bg:#0f1115; --card:#181b22; --high:#22262f; --line:#2c313c;
    --text:#f2f4f8; --dim:#98a0b0; --accent:#4da3ff; --ok:#3ecf8e; --bad:#ff6b6b;
}
* { box-sizing:border-box; }
body { margin:0; background:var(--bg); color:var(--text);
       font:15px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif; }
.wrap { max-width:1100px; margin:0 auto; padding:32px 20px 64px; }
.wrap.narrow { max-width:680px; }
h1 { font-size:26px; margin:0 0 6px; }
h2 { font-size:18px; margin:32px 0 10px; }
a { color:var(--accent); }
code { background:var(--high); padding:2px 6px; border-radius:5px; font-size:13px; }
textarea { width:100%; background:var(--high); color:var(--text); border:1px solid var(--line);
           border-radius:8px; padding:12px; font:13px/1.5 ui-monospace,Menlo,Consolas,monospace; }
label { display:block; margin:14px 0; color:var(--dim); font-size:14px; }
input[type=text], input[type=password] {
    width:100%; margin-top:6px; padding:11px 12px; background:var(--high);
    color:var(--text); border:1px solid var(--line); border-radius:8px; font-size:15px; }
.btn { display:inline-block; background:var(--accent); color:#0b0d11; border:0;
       border-radius:8px; padding:10px 18px; font-size:15px; font-weight:600;
       cursor:pointer; text-decoration:none; }
.btn.ghost { background:var(--high); color:var(--text); }
.btn.bad { background:rgba(255,107,107,.15); color:var(--bad); }
.bar { display:flex; gap:10px; align-items:center; flex-wrap:wrap; margin:18px 0; }
.bar .spacer { flex:1; }
table { width:100%; border-collapse:collapse; margin-top:14px; }
th, td { text-align:left; padding:11px 10px; border-bottom:1px solid var(--line); font-size:14px; }
th { color:var(--dim); font-weight:600; font-size:12px; text-transform:uppercase; letter-spacing:.04em; }
tr:hover td { background:var(--card); }
.tag { display:inline-block; padding:2px 9px; border-radius:99px; font-size:12px; font-weight:600; }
.tag.trial { background:rgba(77,163,255,.15); color:var(--accent); }
.tag.active { background:rgba(62,207,142,.15); color:var(--ok); }
.tag.blocked { background:rgba(255,107,107,.15); color:var(--bad); }
.card { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:20px; margin:16px 0; }
.mono { font:14px ui-monospace,Menlo,Consolas,monospace; }
.dim { color:var(--dim); }
.err { color:var(--bad); }
.ok { color:var(--ok); }
.empty { color:var(--dim); padding:40px 0; text-align:center; }
form.inline { display:inline; }
</style>
