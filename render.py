#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Maqraa Al-Noor v1 design renders — code-drawn phone screens (1080x1920).
Font note: UI typeface in the design system is Thamaniya (font.thmanyah.com,
manual download, OTF, no redistribution). No Thamaniya file exists in this
environment and its licence forbids redistribution, including sending the
file to anyone. These previews render with IBM Plex Sans Arabic as a
documented stand-in and are not re-rendered in Thamaniya. Quran text
renders in Noto Naskh Arabic. The licence-compatible model, as in Miraat,
is manual import by the user on their own device, with no file embedded.
"""
import os, math
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.expanduser("~/workspace/maqraa-noor-designs")
os.makedirs(OUT, exist_ok=True)
FONT_DIR = os.path.expanduser("~/workspace/majlis-studio/android/app/src/main/res/font")
NOTO = "/usr/share/fonts/truetype/noto"

def ui_font(size, weight="regular"):
    # Prefer IBM Plex Sans Arabic (Majlis/Miraat font dir); fall back to the
    # system Noto Sans Arabic so a fresh clone still renders.
    plex = {"regular": "ibm_plex_sans_arabic_regular.ttf",
            "medium": "ibm_plex_sans_arabic_medium.ttf",
            "semibold": "ibm_plex_sans_arabic_semibold.ttf",
            "bold": "ibm_plex_sans_arabic_bold.ttf"}
    noto = {"regular": "NotoSansArabic-Regular.ttf", "medium": "NotoSansArabic-Medium.ttf",
            "semibold": "NotoSansArabic-SemiBold.ttf", "bold": "NotoSansArabic-Bold.ttf"}
    for cand in (os.path.join(FONT_DIR, plex[weight]),
                 os.path.join("/usr/share/fonts/truetype/noto", noto[weight])):
        if os.path.exists(cand):
            return ImageFont.truetype(cand, size)
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", size)

def q_font(size, bold=False):
    p = os.path.join(NOTO, "NotoNaskhArabic-Bold.ttf" if bold else "NotoNaskhArabic-Regular.ttf")
    return ImageFont.truetype(p, size)

W, H = 1080, 1920
DEEP = (11, 53, 39); DEEP2 = (6, 36, 27); GREEN = (18, 74, 54)
GOLD = (201, 162, 39); GOLD_L = (232, 199, 102); GOLD_D = (150, 116, 22)
CREAM = (247, 240, 220); PAPER = (255, 253, 246); BG = (244, 241, 232)
INK = (34, 38, 31); MUTED = (107, 114, 104); LINE = (231, 221, 195)
WHITE = (255, 255, 255); SAGE = (230, 237, 230); RED = (196, 48, 43)
HIGHLIGHT = (250, 232, 160)

def F(size, weight="regular", quran=False):
    return q_font(size, weight == "bold") if quran else ui_font(size, weight)

def ar(d, xy, text, font, fill, anchor="ra"):
    d.text(xy, text, font=font, fill=fill, anchor=anchor, direction="rtl", language="ar")

def tw(d, text, font):
    return d.textlength(text, font=font, direction="rtl", language="ar")

def rrect(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

def star8(d, cx, cy, r, fill=None, outline=None, width=2):
    pts = []
    for i in range(16):
        ang = math.pi/8 * i - math.pi/2
        rr = r if i % 2 == 0 else r * 0.42
        pts.append((cx + rr*math.cos(ang), cy + rr*math.sin(ang)))
    if fill: d.polygon(pts, fill=fill)
    if outline: d.line(pts + [pts[0]], fill=outline, width=width, joint="curve")

def pattern(d, color=(201,162,39,28)):
    # faint 8-point stars on dark bg
    ov = Image.new("RGBA", (W, H), (0,0,0,0)); od = ImageDraw.Draw(ov)
    for x in range(90, W, 220):
        for y in range(90, H, 220):
            star8(od, x, y, 42, outline=color, width=2)
    return ov

def status(d, dark=False):
    c = GOLD_L if dark else MUTED
    ar(d, (W-44, 34), "12:30", F(30, "medium"), c, anchor="ra")
    ar(d, (44, 34), "مقرأة النور", F(28, "medium"), c, anchor="la")

def header(canvas, d, title, sub=None):
    d.rectangle([0, 0, W, 200], fill=DEEP)
    ar(d, (W-48, 52), title, F(52, "bold"), WHITE, anchor="ra")
    if sub: ar(d, (W-48, 138), sub, F(30), GOLD_L, anchor="ra")
    if os.path.exists(LOGO):
        paste_logo(canvas, 104, 100, 84)
    else:
        d.ellipse([64, 60, 144, 140], outline=GOLD, width=3, fill=GREEN)
        ar(d, (104, 100), "ن", F(44, "bold"), GOLD_L, anchor="mm")

def bottom_nav(d, active=0):
    d.rectangle([0, H-168, W, H], fill=WHITE)
    d.line([(0, H-168), (W, H-168)], fill=LINE, width=2)
    items = ["الرئيسية", "فصولي", "المصحف", "حسابي"]
    for i, t in enumerate(items):
        x = W - 135 - i*270
        col = GOLD_D if i == active else MUTED
        d.ellipse([x-14, H-138, x+14, H-110], fill=GOLD if i == active else LINE)
        ar(d, (x, H-88), t, F(30, "bold" if i == active else "medium"), col, anchor="mm")

def pill(d, box, text, fill, txtcol, size=34, weight="bold"):
    rrect(d, box, 40, fill=fill)
    ar(d, ((box[0]+box[2])//2, (box[1]+box[3])//2), text, F(size, weight), txtcol, anchor="mm")

def card(d, box, fill=WHITE):
    rrect(d, box, 36, fill=fill, outline=LINE, width=2)

def progress(d, x0, y, w, pct, col=GOLD):
    rrect(d, [x0, y, x0+w, y+14], 7, fill=(232, 228, 210))
    rrect(d, [x0+w-int(w*pct), y, x0+w, y+14], 7, fill=col)

def avatar(d, cx, cy, r, letter, bg=GREEN, fg=GOLD_L):
    d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=bg, outline=GOLD, width=3)
    ar(d, (cx, cy), letter, F(int(r*0.95), "bold"), fg, anchor="mm")

LOGO = os.path.join(OUT, "logo", "logo-circle.png")
_logo_cache = {}
def logo_img(diameter):
    if diameter not in _logo_cache:
        im = Image.open(LOGO).convert("RGBA").resize((diameter, diameter), Image.LANCZOS)
        _logo_cache[diameter] = im
    return _logo_cache[diameter]

def paste_logo(canvas, cx, cy, diameter):
    lg = logo_img(diameter)
    canvas.paste(lg, (cx-diameter//2, cy-diameter//2), lg)

def new_canvas(bg=BG):
    im = Image.new("RGB", (W, H), bg)
    return im, ImageDraw.Draw(im)

def save(im, name):
    p = os.path.join(OUT, name)
    im.save(p, optimize=True)
    print(name, os.path.getsize(p))

# ---------- 01 splash ----------
im, d = new_canvas(DEEP)
im.paste(Image.alpha_composite(im.convert("RGBA"), pattern(d)), (0, 0))
d = ImageDraw.Draw(im)
status(d, dark=True)
# mihrab arch
# Official logo (supplied by the user); the name and tagline are inside it,
# so no duplicate big title is drawn underneath.
paste_logo(im, 540, 620, 640)
ar(d, (540, 1010), "حلقات قرآنية للنساء", F(44, "medium"), GOLD_L, anchor="mm")
ar(d, (540, 1075), "إقراء • حفظ • تجويد", F(36), CREAM, anchor="mm")
d.line([(340, 1160), (740, 1160)], fill=GOLD, width=3)
ar(d, (540, 1225), "خيركم من تعلّم القرآن وعلّمه", F(36, "medium", quran=True), CREAM, anchor="mm")
pill(d, [180, 1420, 900, 1530], "دخول الطالبة", GOLD, DEEP2, 40)
rrect(d, [180, 1560, 900, 1670], 40, outline=GOLD_L, width=3)
ar(d, (540, 1615), "دخول المعلمة", F(40, "bold"), GOLD_L, anchor="mm")
ar(d, (540, 1760), "الدخول بدعوة فقط • لا تسجيل عام", F(32), CREAM, anchor="mm")
ar(d, (540, 1820), "المصحف يعمل دون إنترنت بعد تنزيله", F(30), (200, 210, 195), anchor="mm")
save(im, "01-splash.png")

# ---------- 02 home ----------
im, d = new_canvas()
header(im, d, "السلام عليكم، أمينة", "مستوى التجويد • فصل المتقدمات")
# next halqa
card(d, [48, 240, 1032, 570], fill=DEEP)
ar(d, (984, 280), "الحلقة القادمة", F(36, "bold"), GOLD_L, anchor="ra")
ar(d, (984, 340), "فصل التجويد • مع المعلمة أم سارة", F(34), WHITE, anchor="ra")
ar(d, (984, 400), "اليوم • 17:00 • سورة الكهف", F(32), CREAM, anchor="ra")
pill(d, [560, 462, 984, 528], "انضمي عند الموعد", GOLD, DEEP2, 32)
ar(d, (520, 495), "يتبقى 02:14:30", F(32, "bold"), GOLD_L, anchor="ra")
ar(d, (984, 620), "تقدمك هذا الأسبوع", F(40, "bold"), INK, anchor="ra")
for i, (n, t) in enumerate([("3", "حلقات"), ("12", "صفحة قراءة"), ("4/5", "تقييم التجويد")]):
    x1 = 48 + i*328
    card(d, [x1, 680, x1+304, 850])
    ar(d, (x1+152, 730), n, F(52, "bold"), GOLD_D, anchor="mm")
    ar(d, (x1+152, 800), t, F(30), MUTED, anchor="mm")
ar(d, (984, 910), "فصولي", F(40, "bold"), INK, anchor="ra")
for j, (t, s, p) in enumerate([("فصل التجويد — المتقدمات", "الأحد والثلاثاء • 17:00", 0.68),
                                ("فصل الحفظ — جزء عم", "السبت • 10:00", 0.42)]):
    y = 975 + j*210
    card(d, [48, y, 1032, y+180])
    ar(d, (984, y+42), t, F(36, "bold"), INK, anchor="ra")
    ar(d, (984, y+92), s, F(30), MUTED, anchor="ra")
    progress(d, 96, y+138, 420, p)
    d.text((540, y+145), f"{int(p*100)}%", font=F(28, "bold"), fill=GOLD_D, anchor="mm")
ar(d, (984, 1410), "وصول سريع", F(40, "bold"), INK, anchor="ra")
for i, t in enumerate(["المصحف", "واجباتي (2)", "تسميعي الصوتي"]):
    x1 = 48 + i*328
    card(d, [x1, 1470, x1+304, 1600], fill=PAPER)
    ar(d, (x1+152, 1535), t, F(32, "bold"), DEEP, anchor="mm")
bottom_nav(d, 0)
save(im, "02-home.png")

# ---------- 03 classes & levels ----------
im, d = new_canvas()
header(im, d, "فصولي ومستوياتي", "مسارك من التأسيس إلى الإجازة")
ar(d, (984, 250), "مستواك الحالي", F(40, "bold"), INK, anchor="ra")
levels = ["التأسيس", "التجويد", "الحفظ", "الإجازة"]
d.line([(120, 360), (960, 360)], fill=LINE, width=6)
d.line([(520, 360), (960, 360)], fill=GOLD, width=6)
for i, t in enumerate(levels):
    x = 960 - i*280
    done = i < 1; cur = i == 1
    d.ellipse([x-36, 324, x+36, 396], fill=GOLD if (done or cur) else WHITE, outline=GOLD, width=3)
    ar(d, (x, 360), "✓" if done else str(i+1), F(32, "bold"), DEEP2 if (done or cur) else MUTED, anchor="mm")
    ar(d, (x, 440), t, F(32, "bold" if cur else "medium"), GOLD_D if cur else MUTED, anchor="mm")
ar(d, (984, 520), "أنت هنا: التجويد • 68٪ من المستوى", F(32), GOLD_D, anchor="ra")
classes = [
    ("فصل التجويد — المتقدمات", "المعلمة أم سارة • 14 طالبة", "الأحد والثلاثاء • 17:00 • سورة الكهف", "دخول الفصل", True, 0.68),
    ("فصل الحفظ — جزء عم", "المعلمة أم مريم • 11 طالبة", "السبت • 10:00 • سورة النبأ", "دخول الفصل", True, 0.42),
    ("إقراء فردي", "المعلمة أم سارة • جلسة واحدة أسبوعيا", "الخميس • 18:30 • موعدك القادم", "موعدي", True, 0.80),
    ("مستوى الإجازة", "يفتح بعد إتمام الحفظ والتجويد", "بالسند • مع المشرفة العامة", "مقفل", False, 0.0),
]
for j, (t, s, s2, btn, open_, p) in enumerate(classes):
    y = 580 + j*282
    card(d, [48, y, 1032, y+258])
    ar(d, (984, y+44), t, F(38, "bold"), INK, anchor="ra")
    ar(d, (984, y+96), s, F(30), MUTED, anchor="ra")
    ar(d, (984, y+140), s2, F(30), MUTED, anchor="ra")
    progress(d, 96, y+196, 480, p)
    pill(d, [760, y+182, 984, y+240], btn, GOLD if open_ else LINE, DEEP2 if open_ else MUTED, 30)
bottom_nav(d, 1)
save(im, "03-classes-levels.png")

# ---------- 04 live halqa ----------
im, d = new_canvas(DEEP2)
d.rectangle([0, 0, W, 120], fill=DEEP2)
ar(d, (984, 62), "حلقة التجويد • مباشر", F(38, "bold"), WHITE, anchor="ra")
pill(d, [48, 34, 210, 92], "مباشر", RED, WHITE, 30)
ar(d, (540, 62), "24:10", F(34, "bold"), GOLD_L, anchor="mm")
# teacher tile
rrect(d, [48, 140, 1032, 560], 36, fill=GREEN, outline=GOLD, width=3)
avatar(d, 540, 300, 78, "س", bg=DEEP, fg=GOLD_L)
ar(d, (540, 410), "المعلمة أم سارة", F(42, "bold"), WHITE, anchor="mm")
ar(d, (540, 460), "تقرأ: فاطمة • سورة الكهف، آية 1", F(30), CREAM, anchor="mm")
ar(d, (984, 500), "الكاميرا مغلقة • صوت فقط", F(28), GOLD_L, anchor="ra")
# student tiles
for i, (L, nm, st) in enumerate([("ف", "فاطمة", "تقرأ الآن"), ("م", "مريم", "رافعة يدها"), ("خ", "خديجة", "تستمع"), ("ن", "نور", "تستمع")]):
    x1 = 48 + i*252
    rrect(d, [x1, 580, x1+228, 770], 28, fill=(16, 58, 44), outline=LINE, width=1)
    avatar(d, x1+114, 648, 42, L, bg=DEEP, fg=GOLD_L)
    ar(d, (x1+114, 712), nm, F(30, "bold"), WHITE, anchor="mm")
    ar(d, (x1+114, 744), st, F(24), GOLD_L if "تقرأ" in st or "يدها" in st else CREAM, anchor="mm")
# mushaf strip
rrect(d, [48, 800, 1032, 1060], 32, fill=PAPER)
ar(d, (984, 838), "المصحف • سورة الكهف • صفحة 293", F(30, "bold"), GOLD_D, anchor="ra")
rrect(d, [96, 890, 984, 970], 20, fill=HIGHLIGHT)
ar(d, (540, 930), "الْحَمْدُ لِلَّهِ الَّذِي أَنزَلَ عَلَىٰ عَبْدِهِ الْكِتَابَ", F(36, "bold", quran=True), INK, anchor="mm")
ar(d, (540, 1020), "متابعة المعلمة مفعّلة • الصفحة تنقلب عند الجميع معا", F(28), MUTED, anchor="mm")
# queue
rrect(d, [48, 1090, 1032, 1330], 32, fill=WHITE)
ar(d, (984, 1132), "دور التلاوة", F(36, "bold"), INK, anchor="ra")
for i, t in enumerate(["1 • فاطمة — تقرأ الآن", "2 • مريم — التالية", "3 • خديجة"]):
    ar(d, (984, 1190+i*44), t, F(32), GOLD_D if i == 0 else INK, anchor="ra")
pill(d, [96, 1240, 420, 1300], "ارفعي يدك", GOLD, DEEP2, 30)
# teacher controls
ar(d, (984, 1352), "تحكم المعلمة", F(32, "bold"), GOLD_L, anchor="ra")
for i, t in enumerate(["كتم الجميع", "قفل الحلقة", "إنهاء"]):
    x1 = 48 + i*328
    rrect(d, [x1, 1420, x1+304, 1490], 30, outline=GOLD, width=2)
    ar(d, (x1+152, 1455), t, F(30, "bold"), GOLD_L, anchor="mm")
# bottom controls
for i, t in enumerate(["الميكروفون", "الكاميرا", "الدردشة", "مغادرة"]):
    x1 = 48 + i*252
    rrect(d, [x1, 1560, x1+228, 1660], 30, fill=RED if t == "مغادرة" else GREEN, outline=GOLD if t != "مغادرة" else RED, width=2)
    ar(d, (x1+114, 1610), t, F(30, "bold"), WHITE, anchor="mm")
ar(d, (540, 1740), "مغلق: ميكروفونك مكتوم حتى تعطيك المعلمة الدور", F(30), CREAM, anchor="mm")
save(im, "04-live-halqa.png")

# ---------- 05 mushaf ----------
im, d = new_canvas(PAPER)
d.rectangle([0, 0, W, 170], fill=DEEP)
ar(d, (984, 62), "سورة الفاتحة", F(46, "bold"), WHITE, anchor="ra")
ar(d, (984, 120), "صفحة 1 • رواية حفص • مصحف المدينة", F(30), GOLD_L, anchor="ra")
ar(d, (96, 62), "صفحة 1 من 604", F(30, "bold"), GOLD_L, anchor="la")
# ornamental line
d.line([(180, 230), (900, 230)], fill=GOLD, width=3)
star8(d, 540, 230, 22, fill=GOLD)
verses = [
    ("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", False),
    ("الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", False),
    ("الرَّحْمَٰنِ الرَّحِيمِ", True),
    ("مَالِكِ يَوْمِ الدِّينِ", False),
    ("إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ", False),
    ("اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ", False),
    ("صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ", False),
]
y = 300
for idx, (v, hl) in enumerate(verses, 1):
    lines = [v]
    if idx == 7:
        lines = ["صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ", "غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ"]
    h = 96 * len(lines) + 24
    if hl:
        rrect(d, [80, y-16, 1000, y+h-16], 24, fill=HIGHLIGHT)
    for li, line in enumerate(lines):
        ar(d, (540, y + li*96), line, F(48, "bold", quran=True), INK, anchor="mm")
    cy = y + (len(lines)-1)*48
    d.ellipse([128, cy-28, 184, cy+28], outline=GOLD_D, width=2)
    ar(d, (156, cy), str(idx), F(26, "bold"), GOLD_D, anchor="mm")
    y += h + 18
    if idx < 7:
        d.line([(480, y-6), (600, y-6)], fill=LINE, width=2)
ar(d, (540, 1330), "الآية المظللة هي موضع القراءة الحالي", F(30), GOLD_D, anchor="mm")
# controls
for i, t in enumerate(["الصفحة السابقة", "علامة", "ألوان التجويد", "الصفحة التالية"]):
    x1 = 48 + i*252
    rrect(d, [x1, 1400, x1+228, 1480], 30, fill=WHITE, outline=GOLD, width=2)
    ar(d, (x1+114, 1440), t, F(28, "bold"), DEEP, anchor="mm")
rrect(d, [48, 1510, 1032, 1590], 30, fill=SAGE)
ar(d, (540, 1550), "يعمل دون إنترنت • تكبير الخط من الإعدادات", F(30), DEEP, anchor="mm")
bottom_nav(d, 2)
save(im, "05-mushaf.png")

# ---------- 06 teacher control ----------
im, d = new_canvas()
header(im, d, "تحكم المعلمة", "فصل التجويد • حلقة جارية 24:10")
for i, t in enumerate(["كتم الجميع", "قفل الدخول", "إنهاء الحلقة"]):
    x1 = 48 + i*328
    rrect(d, [x1, 230, x1+304, 300], 30, fill=RED if "إنهاء" in t else DEEP)
    ar(d, (x1+152, 265), t, F(30, "bold"), WHITE, anchor="mm")
ar(d, (984, 360), "الطالبات • 14 حاضرة من 16", F(38, "bold"), INK, anchor="ra")
rows = [
    ("فاطمة", "تقرأ الآن", "حاضرة", True),
    ("مريم", "رافعة يدها", "حاضرة", True),
    ("خديجة", "تستمع", "حاضرة", True),
    ("نور", "تستمع", "حاضرة", True),
    ("آسية", "ميكروفونها مفتوح", "حاضرة", False),
    ("هبة", "لم تنضم بعد", "غائبة", False),
]
for j, (nm, st, att, on) in enumerate(rows):
    y = 410 + j*170
    card(d, [48, y, 1032, y+150])
    avatar(d, 920, y+75, 48, nm[0], bg=GREEN if on else LINE, fg=GOLD_L if on else MUTED)
    ar(d, (850, y+48), nm, F(36, "bold"), INK, anchor="ra")
    ar(d, (850, y+100), st, F(30), GOLD_D if "تقرأ" in st or "يدها" in st else MUTED, anchor="ra")
    pill(d, [96, y+42, 300, y+108], "أعطها الدور" if on else "تنبيه", GOLD if on else LINE, DEEP2 if on else MUTED, 28)
    ar(d, (520, y+75), att, F(30, "bold"), GREEN if att == "حاضرة" else RED, anchor="mm")
ar(d, (984, 1460), "تقييم سريع لفاطمة", F(36, "bold"), INK, anchor="ra")
for i, t in enumerate(["التجويد 4/5", "الحفظ 5/5", "الحضور ✓"]):
    x1 = 48 + i*328
    card(d, [x1, 1510, x1+304, 1600], fill=PAPER)
    ar(d, (x1+152, 1555), t, F(30, "bold"), DEEP, anchor="mm")
pill(d, [48, 1640, 1032, 1720], "حفظ التقييم وإرساله للطالبة", DEEP, WHITE, 34)
bottom_nav(d, 1)
save(im, "06-teacher-control.png")

# ---------- 07 private session ----------
im, d = new_canvas()
header(im, d, "جلسة فردية • مريم", "إقراء • الخميس 18:30 • 25 دقيقة")
rrect(d, [48, 230, 1032, 420], 32, fill=PAPER, outline=LINE, width=2)
ar(d, (984, 272), "موضع اليوم • سورة الكهف، آية 1", F(32, "bold"), GOLD_D, anchor="ra")
ar(d, (540, 340), "الْحَمْدُ لِلَّهِ الَّذِي أَنزَلَ عَلَىٰ عَبْدِهِ الْكِتَابَ", F(36, "bold", quran=True), INK, anchor="mm")
# chat bubbles (right = teacher, left = student in RTL layout: teacher on right)
def bubble(y, text, mine, h=110, sub=None):
    if mine:
        rrect(d, [280, y, 984, y+h], 30, fill=DEEP)
        ar(d, (940, y+h//2), text, F(32), WHITE, anchor="ra")
    else:
        rrect(d, [96, y, 800, y+h], 30, fill=WHITE, outline=LINE, width=2)
        ar(d, (756, y+h//2), text, F(32), INK, anchor="ra")
    # sub captions are omitted: they overlapped the bubble text
bubble(460, "أحسنتِ، أعيدي الآية مع مدّ البدل", True, 100)
bubble(590, "تسميع مريم • تسجيل صوتي 00:42", False, 100)
bubble(720, "المدّ هنا حركتان فقط، لا ستّا", True, 100)
bubble(850, "فهمت، سأعيدها الآن", False, 100)
bubble(980, "تقييم الجلسة: التجويد 4/5 • أعيدي أول 5 آيات واجبا", True, 130)
ar(d, (984, 1180), "الرسائل بين المعلمة والطالبة فقط • لا يراها أحد", F(28), MUTED, anchor="ra")
# input
rrect(d, [48, 1620, 1032, 1710], 40, fill=WHITE, outline=LINE, width=2)
ar(d, (984, 1665), "اكتبي رسالة للمعلمة...", F(32), MUTED, anchor="ra")
pill(d, [80, 1636, 260, 1694], "إرسال", GOLD, DEEP2, 30)
for i, t in enumerate(["اتصال صوتي", "اتصال صورة", "المصحف"]):
    x1 = 48 + i*328
    rrect(d, [x1, 1740, x1+304, 1810], 30, fill=DEEP if i == 0 else WHITE, outline=GOLD, width=2)
    ar(d, (x1+152, 1775), t, F(30, "bold"), WHITE if i == 0 else DEEP, anchor="mm")
save(im, "07-private-session.png")

# ---------- 08 design system ----------
im, d = new_canvas(WHITE)
ar(d, (984, 64), "دليل هوية مقرأة النور", F(56, "bold"), INK, anchor="ra")
ar(d, (984, 152), "النسخة الأولى • v1", F(32), MUTED, anchor="ra")
d.line([(48, 190), (1032, 190)], fill=GOLD, width=3)
ar(d, (984, 250), "الألوان", F(42, "bold"), INK, anchor="ra")
sw = [("أخضر عميق", "#0B3527", DEEP), ("أخضر", "#124A36", GREEN), ("ذهبي", "#C9A227", GOLD),
      ("ذهبي فاتح", "#E8C766", GOLD_L), ("كريمي", "#F7F0DC", CREAM), ("ورقي", "#FFFDF6", PAPER)]
for i, (nm, hx, col) in enumerate(sw):
    x1 = 48 + i*164
    rrect(d, [x1, 300, x1+148, 430], 24, fill=col, outline=LINE, width=2)
    ar(d, (x1+74, 470), nm, F(26, "bold"), INK, anchor="mm")
    d.text((x1+74, 505), hx, font=F(24), fill=MUTED, anchor="mm")
ar(d, (984, 580), "الخط", F(42, "bold"), INK, anchor="ra")
ar(d, (984, 640), "الأساسي للواجهة: خط ثمانية — يُنزَّل يدويا من الموقع الرسمي", F(32, "bold"), INK, anchor="ra")
ar(d, (984, 690), "font.thmanyah.com • صيغة OTF • لا يُضمَّن ملفه ولا يُعاد توزيعه", F(30), MUTED, anchor="ra")
ar(d, (984, 740), "هذه الصور رُسمت بخط IBM Plex Sans Arabic بديلا موثقا، لحين توفر ملف ثمانية.", F(30), MUTED, anchor="ra")
ar(d, (984, 790), "نص المصحف: خط نسخي عثماني (Noto Naskh) — لا يُكتب المصحف بخط ثمانية.", F(30), MUTED, anchor="ra")
ar(d, (984, 870), "أحجام الواجهة", F(42, "bold"), INK, anchor="ra")
for i, (t, s) in enumerate([("عنوان الشاشة", 52), ("عنوان بطاقة", 38), ("نص عادي", 32), ("نص ثانوي", 28)]):
    ar(d, (984, 930+i*70), t, F(s, "bold"), INK, anchor="ra")
    ar(d, (96, 930+i*70), f"{s}px", F(28), MUTED, anchor="la")
ar(d, (984, 1230), "المكونات", F(42, "bold"), INK, anchor="ra")
pill(d, [600, 1280, 984, 1360], "زر ذهبي أساسي", GOLD, DEEP2, 32)
rrect(d, [96, 1280, 480, 1360], 40, outline=GOLD, width=3)
ar(d, (288, 1320), "زر ثانوي", F(32, "bold"), GOLD_D, anchor="mm")
card(d, [48, 1400, 1032, 1560])
ar(d, (984, 1445), "بطاقة فصل • زوايا 36 • حد ذهبي فاتح", F(32, "bold"), INK, anchor="ra")
ar(d, (984, 1500), "خلفية فاتحة دافئة • لا صور لأشخاص • أحرف أولى فقط", F(30), MUTED, anchor="ra")
ar(d, (984, 1640), "قواعد الخصوصية في التصميم", F(42, "bold"), INK, anchor="ra")
for i, t in enumerate(["لا صور شخصية في أي شاشة — دوائر بالأحرف الأولى فقط.",
                        "الكاميرا مغلقة افتراضيا، والصوت هو الأساس.",
                        "الطالبة ترى فصلها فقط، ولا ترى أرقام الأخريات."]):
    ar(d, (984, 1700+i*56), "•  " + t, F(30), INK, anchor="ra")
ar(d, (540, 1880), "مقرأة النور • تصميم النسخة الأولى", F(28), MUTED, anchor="mm")
save(im, "08-design-system.png")
print("done")
