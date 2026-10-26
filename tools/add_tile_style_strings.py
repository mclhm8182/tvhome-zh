#!/usr/bin/env python3
"""Adds the tile-style strings to every language, keeping the key sets aligned."""
import os
import re

RES = 'launcher/app/src/main/res'
KEYS = ['tile_style', 'tile_style_set', 'tile_style_icon']

T = {
 'en':      ['Tile style', 'As a set', 'App icons'],
 'zh':      ['磁贴样式', '统一风格', '应用原图'],
 'zh-rTW':  ['磁貼樣式', '統一風格', '應用程式原圖'],
 'de':      ['Kachelstil', 'Als Satz', 'App-Symbole'],
 'fr':      ['Style des tuiles', 'En ensemble', "Icônes d'app"],
 'es':      ['Estilo de mosaicos', 'Como conjunto', 'Iconos de apps'],
 'it':      ['Stile riquadri', 'Come set', 'Icone app'],
 'pt':      ['Estilo dos mosaicos', 'Como conjunto', 'Ícones das apps'],
 'ru':      ['Стиль плиток', 'Как набор', 'Значки приложений'],
 'pl':      ['Styl kafli', 'Jako zestaw', 'Ikony aplikacji'],
 'tr':      ['Kutucuk stili', 'Set olarak', 'Uygulama simgeleri'],
 'uk':      ['Стиль плиток', 'Як набір', 'Значки застосунків'],
 'ar':      ['نمط المربعات', 'كمجموعة', 'أيقونات التطبيقات'],
 'iw':      ['סגנון אריחים', 'כערכה', 'סמלי אפליקציה'],
 'in':      ['टाइल शैली', 'सेट के रूप में', 'ऐप आइकन'],
 'th':      ['สไตล์ไทล์', 'เป็นชุด', 'ไอคอนแอป'],
 'vi':      ['Kiểu ô', 'Như một bộ', 'Biểu tượng ứng dụng'],
 'ca':      ['Estil de les tessel·les', 'Com a conjunt', "Icones d'app"],
 'cs':      ['Styl dlaždic', 'Jako sada', 'Ikony aplikací'],
 'fi':      ['Ruudun tyyli', 'Sarjana', 'Sovelluskuvakkeet'],
 'nb':      ['Rutestil', 'Som sett', 'App-ikoner'],
 'nl':      ['Tegelstijl', 'Als set', 'App-pictogrammen'],
 'sr':      ['Стил плочица', 'Као сет', 'Иконице апликација'],
 'uz':      ['Plitka uslubi', 'To\'plam sifatida', 'Ilova belgilari'],
 'bn':      ['টাইল স্টাইল', 'সেট হিসেবে', 'অ্যাপ আইকন'],
}


def esc(s):
    return '"' + s + '"' if "'" in s else s


for lang, vals in sorted(T.items()):
    assert len(vals) == len(KEYS), (lang, len(vals))
    d = 'values' if lang == 'en' else 'values-' + lang
    p = os.path.join(RES, d, 'strings.xml')
    if not os.path.exists(p):
        print('缺', d)
        continue
    t = open(p, encoding='utf-8').read()
    have = set(re.findall(r'<string name="([^"]+)"', t))
    add = ['    <string name="%s">%s</string>' % (k, esc(v))
           for k, v in zip(KEYS, vals) if k not in have]
    if not add:
        continue
    t = t.replace('</resources>', '\n'.join(add) + '\n</resources>')
    open(p, 'w', encoding='utf-8').write(t)
    print('%-9s +%d' % (d, len(add)))
