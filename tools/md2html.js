// docs/ДОКУМЕНТАЦИЯ.md → один HTML-файл (без сетевых зависимостей, Node 8+).
// Якоря разделов — латиница, чтобы установщик мог открыть нужный раздел по ссылке.
// Использование: node tools/md2html.js docs/ДОКУМЕНТАЦИЯ.md build/dist/docs/doc.html
'use strict';
const fs = require('fs');

const src = process.argv[2] || 'docs/ДОКУМЕНТАЦИЯ.md';
const out = process.argv[3] || 'build/dist/docs/doc.html';
const map = { а: 'a', б: 'b', в: 'v', г: 'g', д: 'd', е: 'e', ё: 'e', ж: 'zh', з: 'z', и: 'i',
  й: 'i', к: 'k', л: 'l', м: 'm', н: 'n', о: 'o', п: 'p', р: 'r', с: 's', т: 't', у: 'u',
  ф: 'f', х: 'h', ц: 'ts', ч: 'ch', ш: 'sh', щ: 'sch', ъ: '', ы: 'y', ь: '', э: 'e', ю: 'yu',
  я: 'ya' };

function slug(t) {
  return t.toLowerCase().replace(/[а-яё]/g, c => (c in map ? map[c] : c))
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'r';
}
function esc(t) {
  return t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
function inline(t) {
  return esc(t).replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, '<a href="$2">$1</a>')
    .replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>').replace(/\*([^*]+)\*/g, '<i>$1</i>');
}

const lines = fs.readFileSync(src, 'utf8').replace(/\r/g, '').split('\n');
const body = [], used = {}, toc = [];
let para = [], list = null;          /* null | { tag, indent } */

function flushPara() {
  if (para.length) { body.push('<p>' + inline(para.join(' ')) + '</p>'); para = []; }
}
function closeList() {
  while (list) { body.push('</' + list.tag + '>'); list = list.up; }
}
function heading(level, text) {
  flushPara(); closeList();
  let id = slug(text);
  id = used[id] ? id + '-' + ++used[id] : (used[id] = 1, id);
  if (level <= 3) toc.push({ level, text, id });
  body.push('<h' + level + ' id="' + id + '">' + inline(text) + '</h' + level + '>');
}

for (let i = 0; i < lines.length; i++) {
  const ln = lines[i], t = ln.trim();
  const h = /^(#{1,6})\s+(.*)$/.exec(t);
  if (h) { heading(h[1].length, h[2]); continue; }
  if (/^(-{3,}|\*{3,})$/.test(t)) { flushPara(); closeList(); body.push('<hr>'); continue; }
  if (/^\|/.test(t)) {                                   /* таблица */
    flushPara(); closeList();
    const rows = [];
    while (i < lines.length && /^\|/.test(lines[i].trim())) { rows.push(lines[i].trim()); i++; }
    i--;
    const cell = r => r.replace(/^\||\|$/g, '').split('|').map(c => c.trim());
    body.push('<table>');
    rows.forEach((r, n) => {
      if (/^[\s|:-]+$/.test(r)) return;                  /* разделитель --- */
      const tg = n === 0 ? 'th' : 'td';
      body.push('<tr>' + cell(r).map(c => '<' + tg + '>' + inline(c) + '</' + tg + '>').join('') + '</tr>');
    });
    body.push('</table>');
    continue;
  }
  const li = /^(\s*)([-*]|\d+\.)\s+(.*)$/.exec(ln);
  if (li) {
    flushPara();
    const indent = li[1].length, tag = /\d/.test(li[2]) ? 'ol' : 'ul';
    if (!list) { body.push('<' + tag + '>'); list = { tag, indent }; }
    else if (indent > list.indent) { body.push('<' + tag + ' class="sub">'); list = { tag, indent, up: list }; }
    else while (list.up && indent <= list.up.indent) { body.push('</' + list.tag + '>'); list = list.up; }
    body.push('<li>' + inline(li[3]) + '</li>');
    continue;
  }
  if (!t) {                                              /* пустая строка — конец блока */
    flushPara();
    if (list && !/^\s*([-*]|\d+\.)\s/.test(lines[i + 1] || '')) { closeList(); }
    continue;
  }
  if (list && !/^\s/.test(ln)) closeList();
  para.push(t);
}
flushPara(); closeList();

const nav = toc.map(e => '<a class="t' + e.level + '" href="#' + e.id + '">' + inline(e.text) + '</a>')
  .join('\n');
const title = (toc[0] && toc[0].text) || 'Документация';
const css = `body{margin:0 auto;max-width:900px;padding:24px 20px 80px;background:#f4f4f2;
color:#111;font:16px/1.55 "Segoe UI",Arial,sans-serif}
main{background:#fff;padding:34px 40px;border:1px solid #d8d8d4;border-radius:6px}
h1{font-size:28px;margin:.2em 0 .8em}h2{font-size:22px;margin:1.6em 0 .5em;color:#0a4d2c;
border-bottom:2px solid #dfe9e2;padding-bottom:.2em}
h3{font-size:18px;margin:1.4em 0 .4em}h4{font-size:16px;margin:1.2em 0 .3em}
p,li{margin:.45em 0}ul,ol{padding-left:1.5em}ul .sub{margin-top:.2em}
code{background:#eef1ee;padding:1px 5px;border-radius:3px;font:14px/1.5 Consolas,monospace}
b{color:#000}hr{border:0;border-top:1px solid #ddd;margin:26px 0}
a{color:#0b5a32}a:hover{text-decoration:underline}
table{border-collapse:collapse;margin:14px 0;width:100%}
th,td{border:1px solid #cfd6cf;padding:6px 9px;text-align:left;vertical-align:top}
th{background:#e8f0e8}tr:nth-child(even) td{background:#fafbf9}
nav{position:sticky;top:0;background:#2b2b2b;color:#fff;padding:10px 16px;margin:0 -20px 22px;
font-size:14px;max-height:38vh;overflow:auto}
nav a{display:inline-block;color:#cfe8d6;text-decoration:none;margin:2px 12px 2px 0}
nav a:hover{color:#fff;text-decoration:underline}nav a.t1{font-weight:700}
nav a.t3{margin-left:12px;font-size:13px}
@media print{nav{display:none}body{background:#fff;max-width:none;padding:0}
main{border:0;padding:0}}`;
const html = '<!doctype html>\n<html lang="ru">\n<head>\n<meta charset="utf-8">\
\n<meta name="viewport" content="width=device-width,initial-scale=1">\
\n<title>' + esc(title) + '</title>\n<style>\n' + css + '</style></head>\n<body>\
\n<nav>' + nav + '</nav>\n<main>\n' + body.join('\n') + '\n</main></body></html>\n';

const dir = require('path').dirname(out);
fs.mkdirSync(dir, { recursive: true });
fs.writeFileSync(out, html);
fs.writeFileSync(out.replace(/\.html$/, '') + '-anchors.txt',
  toc.map(e => e.id + '  ' + e.text).join('\n'));
console.log('HTML: ' + out + '  (' + Math.round(html.length / 1024) + ' KB), якорей: ' + toc.length);
