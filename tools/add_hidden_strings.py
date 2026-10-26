#!/usr/bin/env python3
"""Adds the hidden-apps strings to every language, keeping the key sets aligned."""
import os
import re
import glob

RES = 'launcher/app/src/main/res'
KEYS = ['tile_hide', 'app_hidden', 'hidden_apps', 'hidden_none', 'hidden_count', 'app_restored']

T = {
 'zh':      ['隐藏', '%1$s 已从桌面隐藏', '隐藏的应用', '没有被隐藏的应用', '已隐藏 %1$d 个应用', '%1$s 已放回桌面'],
 'zh-rTW':  ['隱藏', '%1$s 已從桌面隱藏', '隱藏的應用', '沒有被隱藏的應用', '已隱藏 %1$d 個應用', '%1$s 已放回桌面'],
 'de':      ['Ausblenden', '%1$s vom Schreibtisch ausgeblendet', 'Ausgeblendete Apps', 'Keine Apps ausgeblendet', '%1$d Apps ausgeblendet', '%1$s ist zurück auf dem Schreibtisch'],
 'fr':      ['Masquer', '%1$s masqué du bureau', 'Apps masquées', 'Aucune app masquée', '%1$d apps masquées', "%1$s est de retour sur le bureau"],
 'es':      ['Ocultar', '%1$s oculto del escritorio', 'Apps ocultas', 'Ninguna app oculta', '%1$d apps ocultas', '%1$s vuelve al escritorio'],
 'it':      ['Nascondi', '%1$s nascosto dalla scrivania', 'App nascoste', 'Nessuna app nascosta', '%1$d app nascoste', '%1$s è tornato sulla scrivania'],
 'pt':      ['Ocultar', '%1$s oculto do ambiente de trabalho', 'Apps ocultas', 'Nenhuma app oculta', '%1$d apps ocultas', '%1$s voltou ao ambiente de trabalho'],
 'ru':      ['Скрыть', '%1$s скрыт с рабочего стола', 'Скрытые приложения', 'Нет скрытых приложений', 'Скрыто приложений: %1$d', '%1$s снова на рабочем столе'],
 'pl':      ['Ukryj', '%1$s ukryty na pulpicie', 'Ukryte aplikacje', 'Brak ukrytych aplikacji', 'Ukryto aplikacji: %1$d', '%1$s z powrotem na pulpicie'],
 'tr':      ['Gizle', '%1$s masaüstünden gizlendi', 'Gizli uygulamalar', 'Gizli uygulama yok', '%1$d uygulama gizli', '%1$s yeniden masaüstünde'],
 'uk':      ['Сховати', '%1$s приховано з робочого столу', 'Приховані застосунки', 'Немає прихованих застосунків', 'Приховано застосунків: %1$d', '%1$s знову на робочому столу'],
 'ar':      ['إخفاء', '%1$s مخفى من سطح المكتب', 'التطبيقات المخفية', 'لا توجد تطبيقات مخفية', '%1$d تطبيقات مخفية', '%1$s عاد إلى سطح المكتب'],
 'iw':      ['הסתר', '%1$s הוסתר משולחן העבודה', 'אפליקציות מוסתרות', 'אין אפליקציות מוסתרות', '%1$d אפליקציות מוסתרות', '%1$s חזר לשולחן העבודה'],
 'in':      ['छिपाएँ', '%1$s डेस्क से छिपाया गया', 'छिपे हuए ऐप', 'कोई ऐप छिपा नहीं है', '%1$d ऐप छिपे हuए', '%1$s वापस डेस्क पर'],
 'th':      ['ซ่อน', '%1$s ถูกซ่อนจากหน้าจอโฮม', 'แอปที่ซ่อน', 'ไม่มีแอปที่ซ่อน', 'ซ่อนแอป %1$d ตัว', '%1$s กลับมาที่หน้าจอโฮม'],
 'vi':      ['Ẩn', '%1$s đã ẩn khỏi màn hình chính', 'Ứng dụng đã ẩn', 'Không có ứng dụng nào bị ẩn', 'Đã ẩn %1$d ứng dụng', '%1$s đã trở lại màn hình chính'],
 'ca':      ['Amaga', "%1$s amagat de l'escriptori", 'Apps amagades', 'Cap app amagada', "%1$d apps amagades", "%1$s torna a l'escriptori"],
 'cs':      ['Skrýt', '%1$s skryt z plochy', 'Skryté aplikace', 'Žádné skryté aplikace', '%1$d skrytých aplikací', '%1$s je zpět na ploše'],
 'fi':      ['Piilota', '%1$s piilotettu työpöydältä', 'Piilotetut sovellukset', 'Ei piilotettuja sovelluksia', '%1$d sovellusta piilotettu', '%1$s on taas työpöydällä'],
 'nb':      ['Skjul', '%1$s skjult fra skrivebordet', 'Skjulte apper', 'Ingen skjulte apper', '%1$d apper skjult', '%1$s er tilbake på skrivebordet'],
 'nl':      ['Verbergen', '%1$s verborgen van bureaublad', 'Verborgen apps', 'Geen verborgen apps', '%1$d apps verborgen', '%1$s is terug op het bureaublad'],
 'sr':      ['Sakrij', '%1$s sakriven sa radne površine', 'Сакривене апликације', 'Нема сакривених апликација', 'Сакривено апликација: %1$d', '%1$s је враћен на радну површину'],
 'uz':      ["Yashirish", "%1$s ish stolidan yashirildi", 'Yashirilgan ilovalar', "Yashirilgan ilova yo'q", '%1$d ilova yashirilgan', '%1$s ish stoliga qaytdi'],
 'bn':      ['লুকান', '%1$s ডেস্ক থেকে লুকানো হয়েছে', 'লুকানো অ্যাপ', 'কোনো অ্যাপ লুকানো নেই', '%1$d অ্যাপ লুকানো', '%1$s আবার ডেস্কে'],
 'en':      ['Hide', '%1$s hidden from the desk', 'Hidden apps', 'No apps are hidden', '%1$d apps hidden', '%1$s is back on the desk'],
}


def esc(s):
    """A string with an apostrophe must be wrapped whole, not escaped piecewise."""
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
