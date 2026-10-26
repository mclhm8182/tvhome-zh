#!/usr/bin/env python3
"""Add the new launcher strings to every language file.

The keys are the ones the app library, the always-on names and the source button added. A key that
is already there is left alone, so this can be run again after a change.
"""
import os
import re
import sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'launcher', 'app', 'src', 'main', 'res')

KEYS = [
    'always_show_names', 'always_show_names_off', 'always_show_names_on',
    'input_source_key', 'input_source_key_off', 'input_source_key_on',
    'app_library', 'app_library_hint', 'app_library_title',
    'app_library_added', 'app_library_removed', 'app_library_empty',
    'app_library_loading', 'app_library_already', 'app_library_full',
]

T = {
'zh': ['始终显示应用名称','只有选中的图标显示名称','每个图标都显示名称','信号源按键','电视使用自带的信号源选择','遥控器的信号源键打开此菜单','把应用加到桌面','没有电视图标的应用','桌面上的应用','已把 %1$s 加到桌面','已把 %1$s 从桌面移除','这台电视上的应用都已经在桌面上了','正在查找应用…','%1$s 已经在桌面上了','桌面已经放不下了'],
'zh-rTW': ['始終顯示應用程式名稱','只有選取的圖示顯示名稱','每個圖示都顯示名稱','訊號源按鍵','電視使用內建的訊號源選擇','遙控器的訊號源鍵開啟此選單','把應用程式加到桌面','沒有電視圖示的應用程式','桌面上的應用程式','已把 %1$s 加到桌面','已把 %1$s 從桌面移除','這台電視上的應用程式都已經在桌面上了','正在尋找應用程式…','%1$s 已經在桌面上了','桌面已經放不下了'],
'de': ['App-Namen immer anzeigen','Nur das ausgewählte Symbol zeigt seinen Namen','Jedes Symbol zeigt seinen Namen','Quellen-Taste','Das Fernsehgerät behält seine eigene Quellenauswahl','Die Quellen-Taste der Fernbedienung öffnet dieses Menü','Apps zum Schreibtisch hinzufügen','Apps ohne TV-Symbol','Apps auf dem Schreibtisch','%1$s zum Schreibtisch hinzugefügt','%1$s vom Schreibtisch entfernt','Alle Apps auf diesem Fernsehgerät sind bereits auf dem Schreibtisch','Apps werden gesucht…','%1$s ist bereits auf dem Schreibtisch','Auf dem Schreibtisch ist kein Platz mehr'],
'fr': ['Toujours afficher le nom des applications','Seule l\'icône sélectionnée affiche son nom','Chaque icône affiche son nom','Bouton source','La TV garde son propre sélecteur de source','Le bouton source de la télécommande ouvre ce menu','Ajouter des applications au bureau','Applications sans icône TV','Applications sur le bureau','%1$s ajouté au bureau','%1$s retiré du bureau','Toutes les applications de cette TV sont déjà sur le bureau','Recherche des applications…','%1$s est déjà sur le bureau','Le bureau n\'a plus de place'],
'es': ['Mostrar siempre el nombre de las apps','Solo el icono seleccionado muestra su nombre','Cada icono muestra su nombre','Botón de fuente','El televisor mantiene su propio selector de fuente','El botón de fuente del mando abre este menú','Añadir apps al escritorio','Apps sin icono de TV','Apps en el escritorio','%1$s añadida al escritorio','%1$s quitada del escritorio','Todas las apps de este televisor ya están en el escritorio','Buscando apps…','%1$s ya está en el escritorio','El escritorio no tiene sitio para más'],
'it': ['Mostra sempre il nome delle app','Solo l\'icona selezionata mostra il suo nome','Ogni icona mostra il suo nome','Pulsante sorgente','Il televisore mantiene il proprio selettore di sorgente','Il pulsante sorgente del telecomando apre questo menu','Aggiungi app alla scrivania','App senza icona TV','App sulla scrivania','%1$s aggiunta alla scrivania','%1$s rimossa dalla scrivania','Tutte le app di questo televisore sono già sulla scrivania','Ricerca delle app…','%1$s è già sulla scrivania','La scrivania non ha più spazio'],
'pt': ['Mostrar sempre o nome dos apps','Só o ícone selecionado mostra o seu nome','Cada ícone mostra o seu nome','Botão de fonte','A TV mantém o seu próprio seletor de fonte','O botão de fonte do comando abre este menu','Adicionar apps ao ambiente de trabalho','Apps sem ícone de TV','Apps no ambiente de trabalho','%1$s adicionada ao ambiente de trabalho','%1$s removida do ambiente de trabalho','Todas as apps desta TV já estão no ambiente de trabalho','A procurar apps…','%1$s já está no ambiente de trabalho','O ambiente de trabalho não tem mais espaço'],
'ru': ['Всегда показывать названия приложений','Название показывает только выбранный значок','Название показывает каждый значок','Кнопка источника','Телевизор сохраняет собственный выбор источника','Кнопка источника на пульте открывает это меню','Добавить приложения на рабочий стол','Приложения без значка ТВ','Приложения на рабочем столе','%1$s добавлено на рабочий стол','%1$s удалено с рабочего стола','Все приложения этого телевизора уже на рабочем столе','Поиск приложений…','%1$s уже на рабочем столе','На рабочем столе больше нет места'],
'pl': ['Zawsze pokazuj nazwy aplikacji','Tylko wybrana ikona pokazuje swoją nazwę','Każda ikona pokazuje swoją nazwę','Przycisk źródła','Telewizor zachowuje własny wybór źródła','Przycisk źródła na pilocie otwiera to menu','Dodaj aplikacje do pulpitu','Aplikacje bez ikony TV','Aplikacje na pulpicie','Dodano %1$s do pulpitu','Usunięto %1$s z pulpitu','Wszystkie aplikacje tego telewizora są już na pulpicie','Szukanie aplikacji…','%1$s jest już na pulpicie','Na pulpicie nie ma już miejsca'],
'tr': ['Uygulama adlarını her zaman göster','Yalnızca seçili simge adını gösterir','Her simge adını gösterir','Kaynak düğmesi','Televizyon kendi kaynak seçicisini korur','Kumandanın kaynak düğmesi bu menüyü açar','Uygulamaları masaüstüne ekle','TV simgesi olmayan uygulamalar','Masaüstündeki uygulamalar','%1$s masaüstüne eklendi','%1$s masaüstünden kaldırıldı','Bu televizyondaki tüm uygulamalar zaten masaüstünde','Uygulamalar aranıyor…','%1$s zaten masaüstünde','Masaüstünde artık yer yok'],
'uk': ['Завжди показувати назви застосунків','Назву показує лише вибрана піктограма','Назву показує кожна піктограма','Кнопка джерела','Телевізор залишає власний вибір джерела','Кнопка джерела на пульті відкриває це меню','Додати застосунки на робочий стіл','Застосунки без піктограми ТБ','Застосунки на робочому столі','%1$s додано на робочий стіл','%1$s вилучено з робочого столу','Усі застосунки цього телевізора вже на робочому столі','Пошук застосунків…','%1$s вже на робочому столі','На робочому столі більше немає місця'],
'ar': ['إظهار أسماء التطبيقات دائمًا','فقط الأيقونة المحددة تعرض اسمها','كل أيقونة تعرض اسمها','زر المصدر','يبقى التلفاز على منتقي المصدر الخاص به','زر المصدر في الريموت يفتح هذه القائمة','إضافة تطبيقات إلى سطح المكتب','تطبيقات بلا أيقونة تلفاز','التطبيقات على سطح المكتب','تمت إضافة %1$s إلى سطح المكتب','تمت إزالة %1$s من سطح المكتب','كل تطبيقات هذا التلفاز موجودة بالفعل على سطح المكتب','جارٍ البحث عن التطبيقات…','%1$s موجود بالفعل على سطح المكتب','لا توجد مساحة أخرى على سطح المكتب'],
'iw': ['הצגת שמות האפליקציות תמיד','רק הסמל הנבחר מציג את שמו','כל סמל מציג את שמו','כפתור מקור','הטלוויזיה שומרת על בורר המקור שלה','כפתור המקור בשלט רחוק פותח את התפריט הזה','הוספת אפליקציות לשולחן העבודה','אפליקציות ללא סמל טלוויזיה','אפליקציות על שולחן העבודה','%1$s נוסף לשולחן העבודה','%1$s הוסר משולחן העבודה','כל האפליקציות בטלוויזיה הזו כבר על שולחן העבודה','מחפש אפליקציות…','%1$s כבר על שולחן העבודה','אין יותר מקום בשולחן העבודה'],
'in': ['ऐप के नाम हमेशा दिखाएँ','केवल चुना हुआ आइकन अपना नाम दिखाता है','हर आइकन अपना नाम दिखाता है','स्रोत बटन','टीवी अपना स्रोत चयनकर्ता बनाए रखता है','रिमोट का स्रोत बटन यह मेन्यू खोलता है','ऐप को डेस्क पर जोड़ें','बिना टीवी आइकन वाले ऐप','डेस्क पर मौजूद ऐप','%1$s डेस्क पर जोड़ा गया','%1$s डेस्क से हटाया गया','इस टीवी के सभी ऐप पहले से डेस्क पर हैं','ऐप खोजे जा रहे हैं…','%1$s पहले से डेस्क पर है','डेस्क पर अब जगह नहीं बची'],
'th': ['แสดงชื่อแอปเสมอ','เฉพาะไอคอนที่เลือกจะแสดงชื่อ','ทุกไอคอนจะแสดงชื่อ','ปุ่มเลือกสัญญาณ','ทีวียังคงตัวเลือกสัญญาณของตัวเองไว้','ปุ่มเลือกสัญญาณบนรีโมตเปิดเมนูนี้','เพิ่มแอปไปยังหน้าจอโฮม','แอปที่ไม่มีไอคอนทีวี','แอปบนหน้าจอโฮม','เพิ่ม %1$s ไปยังหน้าจอโฮมแล้ว','นำ %1$s ออกจากหน้าจอโฮมแล้ว','ทุกแอปบนทีวีเครื่องนี้อยู่บนหน้าจอโฮมอยู่แล้ว','กำลังค้นหาแอป…','%1$s อยู่บนหน้าจอโฮมอยู่แล้ว','หน้าจอโฮมไม่มีที่ว่างแล้ว'],
'vi': ['Luôn hiện tên ứng dụng','Chỉ biểu tượng đang chọn hiện tên','Mọi biểu tượng đều hiện tên','Nút nguồn','TV giữ bộ chọn nguồn của riêng nó','Nút nguồn trên điều khiển mở menu này','Thêm ứng dụng vào màn hình chính','Ứng dụng không có biểu tượng TV','Ứng dụng trên màn hình chính','Đã thêm %1$s vào màn hình chính','Đã gỡ %1$s khỏi màn hình chính','Mọi ứng dụng trên TV này đã có trên màn hình chính','Đang tìm ứng dụng…','%1$s đã có trên màn hình chính','Màn hình chính không còn chỗ nữa'],
'ca': ['Mostra sempre el nom de les aplicacions','Només la icona seleccionada mostra el seu nom','Cada icona mostra el seu nom','Botó de font','El televisor manté el seu propi selector de font','El botó de font del comandament obre aquest menú','Afegeix aplicacions a l\'escriptori','Aplicacions sense icona de TV','Aplicacions a l\'escriptori','%1$s afegida a l\'escriptori','%1$s eliminada de l\'escriptori','Totes les aplicacions d\'aquest televisor ja són a l\'escriptori','Buscant aplicacions…','%1$s ja és a l\'escriptori','L\'escriptori no hi cap més'],
'cs': ['Vždy zobrazovat názvy aplikací','Název zobrazuje jen vybraná ikona','Název zobrazuje každá ikona','Tlačítko zdroje','Televizor si ponechá vlastní výběr zdroje','Tlačítko zdroje na ovladači otevře tuto nabídku','Přidat aplikace na plochu','Aplikace bez ikony TV','Aplikace na ploše','%1$s přidána na plochu','%1$s odebrána z plochy','Všechny aplikace tohoto televizoru už jsou na ploše','Hledají se aplikace…','%1$s už je na ploše','Na ploše už není místo'],
'fi': ['Näytä aina sovellusten nimet','Vain valittu kuvake näyttää nimensä','Jokainen kuvake näyttää nimensä','Lähde-painike','Televisio pitää oman lähteenvalitsimensa','Kaukaimen lähde-painike avaa tämän valikon','Lisää sovelluksia työpöydälle','Sovellukset joilla ei ole TV-kuvaketta','Sovellukset työpöydällä','%1$s lisätty työpöydälle','%1$s poistettu työpöydältä','Kaikki tämän television sovellukset ovat jo työpöydällä','Etsitään sovelluksia…','%1$s on jo työpöydällä','Työpöydällä ei ole enää tilaa'],
'nb': ['Vis alltid appnavn','Bare det valgte ikonet viser navnet sitt','Hvert ikon viser navnet sitt','Kildeknapp','TV-en beholder sin egen kildevelger','Kildeknappen på fjernkontrollen åpner denne menyen','Legg apper på skrivebordet','Apper uten TV-ikon','Apper på skrivebordet','%1$s lagt på skrivebordet','%1$s fjernet fra skrivebordet','Alle appene på denne TV-en er allerede på skrivebordet','Letter etter apper…','%1$s er allerede på skrivebordet','Det er ikke plass til flere på skrivebordet'],
'nl': ['Appnamen altijd tonen','Alleen het geselecteerde pictogram toont zijn naam','Elk pictogram toont zijn naam','Bronknop','De tv houdt zijn eigen bronkiezer','De bronknop op de afstandsbediening opent dit menu','Apps aan het bureaublad toevoegen','Apps zonder tv-pictogram','Apps op het bureaublad','%1$s toegevoegd aan het bureaublad','%1$s verwijderd van het bureaublad','Alle apps op deze tv staan al op het bureaublad','Apps zoeken…','%1$s staat al op het bureaublad','Het bureaublad heeft geen ruimte meer'],
'sr': ['Uvek prikaži nazive aplikacija','Samo izabrana ikona prikazuje svoj naziv','Svaka ikona prikazuje svoj naziv','Dugme za izvor','TV zadržava svoj sopstveni birač izvora','Dugme za izvor na daljinskom otvara ovaj meni','Dodaj aplikacije na radnu površinu','Aplikacije bez TV ikone','Aplikacije na radnoj površini','%1$s dodato na radnu površinu','%1$s uklonjeno sa radne površine','Sve aplikacije na ovom TV-u su već na radnoj površini','Traženje aplikacija…','%1$s je već na radnoj površini','Nema više mesta na radnoj površini'],
'uz': ['Ilova nomlarini doim ko\'rsatish','Faqat tanlangan ikon o\'z nomini ko\'rsatadi','Har bir ikon o\'z nomini ko\'rsatadi','Manba tugmasi','TV o\'z manba tanlovini saqlaydi','Pultdagi manba tugmasi bu menyuni ochadi','Ilovalarni ish stoliga qo\'shish','TV ikonkasi yo\'q ilovalar','Ish stolidagi ilovalar','%1$s ish stoliga qo\'shildi','%1$s ish stolidan olib tashlandi','Bu TVdagi barcha ilovalar allaqachon ish stolida','Ilovalar qidirilmoqda…','%1$s allaqachon ish stolida','Ish stolida joy qolmadi'],
'bn': ['সবসময় অ্যাপের নাম দেখান','শুধু নির্বাচিত আইকনেই তার নাম দেখায়','প্রতিটি আইকন তার নাম দেখায়','উৎস বোতাম','টিভি নিজের উৎস নির্বাচক রাখে','রিমোটের উৎস বোতাম এই মেনু খোলে','অ্যাপ ডেস্কে যোগ করুন','টিভি আইকন ছাড়া অ্যাপ','ডেস্কের অ্যাপ','%1$s ডেস্কে যোগ হয়েছে','%1$s ডেস্ক থেকে সরানো হয়েছে','এই টিভির সব অ্যাপই ইতিমধ্যে ডেস্কে আছে','অ্যাপ খোঁজা হচ্ছে…','%1$s ইতিমধ্যে ডেস্কে আছে','ডেস্কে আর জায়গা নেই'],
}

# The name of the accessibility service, which a few languages never got.
HOME_KEY_NAME = {
    'ar': 'زر Home', 'ca': 'Botó Home', 'de': 'Home-Taste', 'es': 'Botón de inicio',
    'fr': 'Bouton Accueil', 'pl': 'Przycisk Home', 'ru': 'Кнопка Home', 'sr': 'Dugme Home',
    'th': 'ปุ่มโฮม', 'tr': 'Ana sayfa düğmesi',
}


def escape(text):
    """A string with an apostrophe or a quote has to be wrapped in double quotes."""
    if "'" in text or '"' in text:
        return '"' + text.replace('"', '&quot;') + '"'
    return text


def main():
    missing = []
    for lang, values in sorted(T.items()):
        if len(values) != len(KEYS):
            print('wrong count for %s: %d' % (lang, len(values)))
            return 1
        path = os.path.join(RES, 'values-' + lang, 'strings.xml')
        if not os.path.exists(path):
            missing.append(lang)
            continue
        with open(path, encoding='utf-8') as f:
            text = f.read()
        have = set(re.findall(r'<string name="([^"]+)"', text))
        add = []
        for key, value in zip(KEYS, values):
            if key in have:
                continue
            add.append('    <string name="%s">%s</string>' % (key, escape(value)))
        if not add:
            continue
        text = text.replace('</resources>', '\n'.join(add) + '\n</resources>')
        with open(path, 'w', encoding='utf-8') as f:
            f.write(text)
        print('%-8s +%d' % (lang, len(add)))

    for lang, value in sorted(HOME_KEY_NAME.items()):
        path = os.path.join(RES, 'values-' + lang, 'strings.xml')
        if not os.path.exists(path):
            continue
        with open(path, encoding='utf-8') as f:
            text = f.read()
        if 'name="home_key_name"' in text:
            continue
        text = text.replace('</resources>',
                            '    <string name="home_key_name">%s</string>\n</resources>' % escape(value))
        with open(path, 'w', encoding='utf-8') as f:
            f.write(text)
        print('%-8s +1  (home_key_name)' % lang)

    if missing:
        print('no file for: ' + ' '.join(missing))
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
