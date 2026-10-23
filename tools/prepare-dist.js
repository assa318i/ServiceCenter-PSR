// Стадия build/dist — то, что берёт установщик: jar, библиотеки, запускатель, HTML-документация.
// Использование: node tools/prepare-dist.js
'use strict';
const fs = require('fs');
const path = require('path');
const cp = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const DIST = path.join(ROOT, 'build', 'dist');
const DOCS = path.join(DIST, 'docs');

function mkdir(d) { fs.mkdirSync(d, { recursive: true }); }
function copy(src, dst) {
    mkdir(path.dirname(dst));
    fs.copyFileSync(src, dst);
    console.log('  ' + path.relative(ROOT, dst) + '  ' + Math.round(fs.statSync(dst).size / 1024) + ' KB');
}

mkdir(DOCS);
copy(path.join(ROOT, 'build', 'psr-service-center.jar'), path.join(DIST, 'psr-service-center.jar'));
const lib = path.join(DIST, 'lib');
mkdir(lib);
for (const f of fs.readdirSync(path.join(ROOT, 'lib')))
    if (f.endsWith('.jar')) copy(path.join(ROOT, 'lib', f), path.join(lib, f));
copy(path.join(ROOT, 'installer', 'run.bat'), path.join(DIST, 'run.bat'));
const icon = path.join(ROOT, 'installer', 'icon.ico');
if (fs.existsSync(icon)) copy(icon, path.join(DIST, 'icon.ico'));
copy(path.join(ROOT, 'installer', 'icon.png'), path.join(DIST, 'icon.png'));

for (const pair of [['docs/ДОКУМЕНТАЦИЯ.md', 'doc.html'], ['docs/ЧЕК-ЛИСТ-ЗНАКОМСТВА.md', 'chek-list.html'],
    ['docs/ИМПОРТ-И-ЭКСПОРТ.md', 'import.html'], ['docs/ИСТОРИЯ-ВЕРСИЙ.md', 'versions.html']]) {
    cp.execFileSync(process.execPath,
        [path.join(ROOT, 'tools', 'md2html.js'), path.join(ROOT, pair[0]), path.join(DOCS, pair[1])],
        { stdio: 'inherit' });
    fs.unlinkSync(path.join(DOCS, pair[1].replace('.html', '') + '-anchors.txt'));
}
copy(path.join(ROOT, 'docs', 'ЧЕК-ЛИСТ-ЗНАКОМСТВА.md'), path.join(DOCS, 'chek-list.md'));
copy(path.join(ROOT, 'docs', 'ИМПОРТ-И-ЭКСПОРТ.md'), path.join(DOCS, 'import.md'));
copy(path.join(ROOT, 'docs', 'ИСТОРИЯ-ВЕРСИЙ.md'), path.join(DOCS, 'versions.md'));
copy(path.join(ROOT, 'README.md'), path.join(DOCS, 'README.md'));
copy(path.join(ROOT, 'LICENSE'), path.join(DOCS, 'LICENSE'));

console.log('Дистрибутив готов: ' + DIST);
