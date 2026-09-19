<?php
/**
 * Kopf, Fuß und Aussehen der öffentlichen Seiten.
 *
 * Bewusst getrennt von admin/style.php: das Panel ist ein Werkzeug für dich, das
 * hier ist ein Schaufenster für Fremde. Dieselben Farben wie die App — dieselben
 * Zahlenwerte wie in Theme.kt —, damit jemand, der vom Fernseher mit dem Handy
 * herkommt, nicht das Gefühl hat, auf einer fremden Seite gelandet zu sein.
 */

function site_head(string $title, string $description = ''): void {
    $lang = current_lang();
    $desc = $description !== '' ? $description : t('hero.body');
    ?>
<!doctype html>
<html lang="<?= e($lang) ?>">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="<?= e(mb_substr(strip_tags($desc), 0, 200)) ?>">
<meta name="color-scheme" content="dark">
<title><?= e($title) ?> — Karacast</title>
<style>
:root {
    /* Aus Theme.kt der App übernommen. */
    --accent:#FF7A00; --accent-soft:#FFBA3C;
    --bg:#0A0C14; --surface:#161A26; --surface-high:#20263A; --border:#2C3348;
    --text:#F2F4F8; --dim:#9AA3B8; --live:#3DDC84; --danger:#FF5A5A;
    --radius:16px;
}
* { box-sizing:border-box; }
body {
    margin:0; background:var(--bg); color:var(--text);
    font:16px/1.65 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;
    -webkit-font-smoothing:antialiased;
}
a { color:var(--accent-soft); }
.wrap { max-width:1000px; margin:0 auto; padding:0 20px; }

header.top {
    position:sticky; top:0; z-index:10;
    background:rgba(10,12,20,.88); backdrop-filter:blur(10px);
    border-bottom:1px solid var(--border);
}
header.top .wrap { display:flex; align-items:center; gap:18px; height:62px; }
.brand { display:flex; align-items:center; gap:10px; font-weight:800; font-size:19px;
         letter-spacing:-.02em; color:var(--text); text-decoration:none; }
.brand .dot { width:12px; height:12px; border-radius:4px; background:var(--accent); }
nav.main { display:flex; gap:18px; margin-left:auto; flex-wrap:wrap; }
nav.main a { color:var(--dim); text-decoration:none; font-size:15px; }
nav.main a:hover { color:var(--text); }
.langs { display:flex; gap:6px; }
.langs a {
    font-size:12px; text-transform:uppercase; letter-spacing:.06em; text-decoration:none;
    color:var(--dim); border:1px solid var(--border); border-radius:8px; padding:3px 7px;
}
.langs a.on { color:var(--bg); background:var(--accent); border-color:var(--accent); font-weight:700; }

h1 { font-size:clamp(30px,5.2vw,52px); line-height:1.1; letter-spacing:-.03em; margin:0 0 18px; }
h2 { font-size:clamp(22px,3vw,30px); letter-spacing:-.02em; margin:0 0 14px; }
h3 { font-size:18px; margin:0 0 6px; }
p  { margin:0 0 14px; }
.lead { font-size:18px; color:var(--dim); max-width:62ch; }
.dim { color:var(--dim); }
section { padding:64px 0; border-bottom:1px solid var(--border); }
section:last-of-type { border-bottom:0; }

.btn {
    display:inline-block; padding:13px 22px; border-radius:12px; font-weight:700;
    text-decoration:none; background:var(--accent); color:#101010; border:1px solid var(--accent);
}
.btn:hover { background:var(--accent-soft); border-color:var(--accent-soft); }
.btn.ghost { background:transparent; color:var(--text); border-color:var(--border); }
.btn.ghost:hover { border-color:var(--dim); background:var(--surface); }
.row { display:flex; gap:12px; flex-wrap:wrap; align-items:center; }

.grid { display:grid; gap:18px; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); }
.card {
    background:var(--surface); border:1px solid var(--border); border-radius:var(--radius);
    padding:22px;
}
.card h3 { color:var(--text); }
.card p  { color:var(--dim); margin:0; }
.step-no {
    display:inline-flex; align-items:center; justify-content:center;
    width:30px; height:30px; border-radius:9px; background:var(--surface-high);
    color:var(--accent); font-weight:800; margin-bottom:10px;
}

.price {
    background:linear-gradient(180deg,var(--surface-high),var(--surface));
    border:1px solid var(--border); border-radius:var(--radius); padding:30px; max-width:520px;
}
.price .amount { font-size:46px; font-weight:800; letter-spacing:-.03em; line-height:1; }
.price ul { margin:18px 0; padding-left:20px; color:var(--dim); }
.price li { margin-bottom:8px; }

label { display:block; margin-bottom:14px; font-size:14px; color:var(--dim); }
input[type=text] {
    display:block; width:100%; margin-top:6px; padding:13px 14px; font-size:18px;
    font-family:ui-monospace,SFMono-Regular,Menlo,monospace; letter-spacing:.06em;
    color:var(--text); background:var(--bg); border:1px solid var(--border); border-radius:12px;
}
input[type=text]:focus { outline:none; border-color:var(--accent); }

label.check {
    display:flex; gap:12px; align-items:flex-start; margin-bottom:18px;
    color:var(--text); font-size:15px; line-height:1.5; cursor:pointer;
}
label.check input { width:22px; height:22px; margin:1px 0 0; accent-color:var(--accent); flex:0 0 auto; }

.tag { display:inline-block; padding:4px 11px; border-radius:999px; font-size:13px; font-weight:700; }
.tag.active  { background:rgba(61,220,132,.14); color:var(--live); }
.tag.trial   { background:rgba(255,186,60,.14); color:var(--accent-soft); }
.tag.blocked { background:rgba(255,90,90,.14);  color:var(--danger); }
.mono { font-family:ui-monospace,SFMono-Regular,Menlo,monospace; letter-spacing:.08em; }
.err { color:var(--danger); }

@media (max-width:760px) {
    /* Auf dem Telefon fliegt die Abschnittsnavigation raus, statt in der
       Kopfzeile umzubrechen: die Seite ist kurz genug zum Scrollen, und die
       Sprachwahl ist das Einzige, was hier oben wirklich gebraucht wird. */
    nav.main { display:none; }
    .langs { margin-left:auto; }
    section { padding:46px 0; }
    .price { padding:24px; }
}

footer.bottom { padding:40px 0 60px; color:var(--dim); font-size:14px; }
footer.bottom .links { display:flex; gap:16px; flex-wrap:wrap; margin-bottom:14px; }
footer.bottom a { color:var(--dim); }
.legal-note { max-width:70ch; }
article.legal h2 { margin-top:32px; }
article.legal p, article.legal li { color:var(--dim); }
.draft {
    background:rgba(255,186,60,.1); border:1px solid rgba(255,186,60,.35);
    color:var(--accent-soft); border-radius:12px; padding:12px 16px; margin-bottom:24px;
}
</style>
</head>
<body>
<header class="top"><div class="wrap">
    <a class="brand" href="index.php"><span class="dot"></span>Karacast</a>
    <nav class="main">
        <a href="index.php#features"><?= e(t('nav.features')) ?></a>
        <a href="index.php#how"><?= e(t('nav.how')) ?></a>
        <a href="index.php#price"><?= e(t('nav.price')) ?></a>
        <a href="status.php"><?= e(t('nav.status')) ?></a>
    </nav>
    <div class="langs">
        <?php foreach (LANGS as $code => $name): ?>
            <a class="<?= $code === $lang ? 'on' : '' ?>" href="<?= e(lang_url($code)) ?>"
               hreflang="<?= e($code) ?>" title="<?= e($name) ?>"><?= e($code) ?></a>
        <?php endforeach; ?>
    </div>
</div></header>
<main>
<?php
}

function site_foot(): void {
    $config = config();
    ?>
</main>
<footer class="bottom"><div class="wrap">
    <div class="links">
        <a href="impressum.php"><?= e(t('footer.legal')) ?></a>
        <a href="agb.php"><?= e(t('footer.terms')) ?></a>
        <a href="datenschutz.php"><?= e(t('footer.privacy')) ?></a>
        <a href="mailto:<?= e($config['support_mail']) ?>"><?= e($config['support_mail']) ?></a>
    </div>
    <p class="legal-note"><?= e(t('footer.note')) ?></p>
    <p>© <?= date('Y') ?> Karacast</p>
</div></footer>
</body></html>
<?php
}
