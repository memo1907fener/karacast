<?php
require __DIR__ . '/../lib/db.php';
require __DIR__ . '/../lib/auth.php';
session_start_safe();
$_SESSION = [];
session_destroy();
header('Location: login.php');
