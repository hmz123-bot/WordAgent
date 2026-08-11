const fs = require('fs');
const p = 'd:/IdeaProjects/Word Agent/word-agent-web/frontend/concept-2026.html';
const h = '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="UTF-8">\n<meta name="viewport" content="width=device-width, initial-scale=1.0">\n<title>Word Agent · 2026 Concept Design</title>\n';
fs.writeFileSync(p, h);
console.log('ok');