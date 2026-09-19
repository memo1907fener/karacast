<?php
/**
 * „Ist mein Gerät freigeschaltet?" — die Frage, die sonst als Anruf bei dir landet.
 *
 * Bewusst sparsam mit dem, was sie verrät: Status und, in der Testphase, die
 * verbleibenden Tage. Kein Modell, keine Notiz, kein Kaufdatum. Die Gerätekennung
 * steht auf jedem Fernsehschirm, sie ist also kein Geheimnis — aber sie ist auch
 * kein Ausweis, und entsprechend wenig gibt diese Seite heraus.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/license.php';
require __DIR__ . '/lib/lang.php';
require __DIR__ . '/lib/site.php';

$config    = config();
$trialDays = (int) ($config['trial_days'] ?? 14);

$given  = is_string($_REQUEST['d'] ?? null) ? $_REQUEST['d'] : '';
$code   = strtoupper(substr(preg_replace('/[^0-9A-Fa-f]/', '', $given) ?? '', 0, 12));
$asked  = trim($given) !== '';

$error   = null;
$device  = null;
$daysLeft = 0;

if ($asked) {
    if (strlen($code) !== 12) {
        $error = t('status.invalid');
    } elseif (lookup_throttled()) {
        // Eine Kennung zu erraten ist aussichtslos — zwölf Hexzeichen sind mehr
        // Möglichkeiten, als jemand durchprobieren kann. Die Bremse steht hier
        // trotzdem: sie hält die Datenbank von Massenabfragen frei.
        $error = t('status.toobusy');
    } else {
        note_lookup();
        $device = device_by_code($code);
        if ($device) {
            $elapsed  = (int) ((time() - strtotime((string) $device['first_seen'])) / 86400);
            $daysLeft = max(0, $trialDays - $elapsed);
        }
    }
}

/**
 * Höchstens dreißig Abfragen je Viertelstunde und Adresse.
 *
 * Nutzt dieselbe Tabelle wie die Panel-Anmeldung, aber mit vorangestelltem „s:",
 * also einem anderen Schlüssel: eine Statusabfrage kann niemanden aus dem Panel
 * aussperren, und ein Fehlversuch bei der Anmeldung bremst keine Statusabfrage.
 */
function lookup_key(): string {
    return substr('s:' . ($_SERVER['REMOTE_ADDR'] ?? '?'), 0, 45);
}

function lookup_throttled(): bool {
    $stmt = db()->prepare(
        'SELECT COUNT(*) FROM login_attempts
         WHERE ip = ? AND at > DATE_SUB(NOW(), INTERVAL 15 MINUTE)'
    );
    $stmt->execute([lookup_key()]);
    return ((int) $stmt->fetchColumn()) >= 30;
}

function note_lookup(): void {
    db()->prepare('INSERT INTO login_attempts (ip, at) VALUES (?, NOW())')->execute([lookup_key()]);
}

site_head(t('status.title'));
?>

<section>
    <div class="wrap" style="max-width:720px">
        <h1 style="font-size:clamp(26px,4vw,40px)"><?= e(t('status.title')) ?></h1>
        <p class="lead"><?= t('status.intro') ?></p>

        <form method="get" class="card" style="margin:26px 0">
            <input type="hidden" name="lang" value="<?= e(current_lang()) ?>">
            <label><?= e(t('status.label')) ?>
                <input type="text" name="d" value="<?= e($code ? implode(':', str_split($code, 2)) : '') ?>"
                       placeholder="A1:B2:C3:D4:E5:F6" autocapitalize="characters" autocomplete="off" required>
            </label>
            <button class="btn" type="submit"><?= e(t('status.submit')) ?></button>
        </form>

        <?php if ($error): ?>
            <p class="err"><?= e($error) ?></p>

        <?php elseif ($asked && !$device): ?>
            <div class="card"><p><?= e(t('status.unknown')) ?></p></div>

        <?php elseif ($device): ?>
            <div class="card">
                <p class="mono dim" style="font-size:15px"><?= e(implode(':', str_split($code, 2))) ?></p>

                <?php if ($device['status'] === 'active'): ?>
                    <p><span class="tag active"><?= e(t('status.active')) ?></span></p>
                    <p class="dim" style="margin:0"><?= t('status.active.hint') ?></p>

                <?php elseif ($device['status'] === 'blocked'): ?>
                    <p><span class="tag blocked"><?= e(t('status.blocked')) ?></span></p>
                    <p class="dim" style="margin:0">
                        <a href="mailto:<?= e($config['support_mail']) ?>?subject=Karacast%20<?= e($code) ?>"><?= e($config['support_mail']) ?></a>
                    </p>

                <?php else: ?>
                    <p>
                        <span class="tag trial">
                            <?= $daysLeft > 0 ? e(t('status.trial', $daysLeft)) : e(t('status.trial.over')) ?>
                        </span>
                    </p>
                    <p style="margin:14px 0 0">
                        <a class="btn" href="aktivieren.php?d=<?= e($code) ?>"><?= e(t('price.cta')) ?></a>
                    </p>
                <?php endif; ?>
            </div>
        <?php endif; ?>
    </div>
</section>

<?php site_foot(); ?>
