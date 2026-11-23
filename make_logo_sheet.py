#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Logo usage sheet + app icon for Maqraatal-Noor, from the supplied logo.
The supplied JPG is never redrawn or recoloured: it is cropped to a circle
(the mark) or placed whole on a plain background. Small-size mark = the
central emblem (mihrab + open book) cropped from the same file, no new art.
"""
import os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.expanduser("~/workspace/maqraa-noor-designs")
LOGO_JPG = os.path.join(BASE, "logo", "maqraat-al-noor-logo.jpg")
OUT_DIR = os.path.join(BASE, "logo")
FONT_DIR = os.path.expanduser("~/workspace/majlis-studio/android/app/src/main/res/font")
W, H = 1080, 1920
DEEP = (11, 53, 39); GREEN = (18, 74, 54); GOLD = (201, 162, 39)
GOLD_L = (232, 199, 102); GOLD_D = (150, 116, 22); CREAM = (247, 240, 220)
BG = (244, 241, 232); INK = (34, 38, 31); MUTED = (107, 114, 104)
LINE = (231, 221, 195); WHITE = (255, 255, 255); PAPER = (255, 253, 246)

def F(size, weight="regular"):
    plex = {"regular": "ibm_plex_sans_arabic_regular.ttf", "medium": "ibm_plex_sans_arabic_medium.ttf",
            "semibold": "ibm_plex_sans_arabic_semibold.ttf", "bold": "ibm_plex_sans_arabic_bold.ttf"}
    noto = {"regular": "NotoSansArabic-Regular.ttf", "medium": "NotoSansArabic-Medium.ttf",
            "semibold": "NotoSansArabic-SemiBold.ttf", "bold": "NotoSansArabic-Bold.ttf"}
    for cand in (os.path.join(FONT_DIR, plex[weight]),
                 os.path.join("/usr/share/fonts/truetype/noto", noto[weight])):
        if os.path.exists(cand):
            return ImageFont.truetype(cand, size)
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", size)

def ar(d, xy, text, font, fill, anchor="ra"):
    d.text(xy, text, font=font, fill=fill, anchor=anchor, direction="rtl", language="ar")

def circle_crop(im, cx, cy, side, out_size):
    crop = im.crop((cx-side//2, cy-side//2, cx+side//2, cy+side//2)).resize((out_size, out_size), Image.LANCZOS)
    mask = Image.new("L", (out_size, out_size), 0)
    ImageDraw.Draw(mask).ellipse([4, 4, out_size-4, out_size-4], fill=255)
    out = Image.new("RGBA", (out_size, out_size), (0, 0, 0, 0))
    out.paste(crop, (0, 0), mask)
    return out

src = Image.open(LOGO_JPG).convert("RGB")

# --- mark: central emblem only, for small sizes (no invented art) ---
mark = circle_crop(src, 625, 450, 620, 640)
mark.save(os.path.join(OUT_DIR, "logo-mark.png"))

# --- app icon 1024: deep-green rounded square, whole logo centred ---
icon = Image.new("RGB", (1024, 1024), DEEP)
d0 = ImageDraw.Draw(icon)
whole = circle_crop(src, 627, 627, 1080, 880)
icon.paste(whole, (72, 72), whole)
icon.save(os.path.join(OUT_DIR, "app-icon-1024.png"))

# --- usage sheet ---
im = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(im)
d.rectangle([0, 0, W, 200], fill=DEEP)
ar(d, (W-48, 52), "الشعار الرسمي — دليل الاستخدام", F(52, "bold"), WHITE, anchor="ra")
ar(d, (W-48, 138), "الشعار المسلَّم من المقرأة، دون إعادة رسم أو تغيير ألوان", F(30), GOLD_L, anchor="ra")

# 1. whole logo on white
d.rounded_rectangle([48, 240, 1032, 760], radius=36, fill=WHITE, outline=LINE, width=2)
whole_big = circle_crop(src, 627, 627, 1080, 460)
im.paste(whole_big, (310, 260), whole_big)
ar(d, (540, 724), "الشعار الكامل — على خلفية بيضاء أو فاتحة", F(30, "bold"), INK, anchor="mm")

# 2. whole logo on dark green (white disc carries it) — smaller disc, inside the card
d.rounded_rectangle([48, 790, 1032, 1180], radius=36, fill=DEEP)
dark_logo = circle_crop(src, 627, 627, 1080, 300)
im.paste(dark_logo, (390, 800), dark_logo)
ar(d, (540, 1148), "على خلفية داكنة — يبقى داخل قرصه الأبيض، لا يُقلب لونه", F(28, "bold"), GOLD_L, anchor="mm")

# 3. mark + icon row
d.rounded_rectangle([48, 1210, 516, 1550], radius=36, fill=WHITE, outline=LINE, width=2)
im.paste(mark.resize((220, 220), Image.LANCZOS), (148, 1230), mark.resize((220, 220), Image.LANCZOS))
ar(d, (282, 1492), "العلامة المصغرة", F(30, "bold"), INK, anchor="mm")
ar(d, (282, 1528), "للأحجام الصغيرة فقط", F(26), MUTED, anchor="mm")
d.rounded_rectangle([564, 1210, 1032, 1550], radius=36, fill=DEEP)
icon_small = Image.open(os.path.join(OUT_DIR, "app-icon-1024.png")).resize((220, 220), Image.LANCZOS)
im.paste(icon_small, (798, 1230))
ar(d, (798, 1492), "أيقونة التطبيق", F(30, "bold"), WHITE, anchor="mm")
ar(d, (798, 1528), "1024×1024", F(26), GOLD_L, anchor="mm")

# 4. small sizes
ar(d, (984, 1610), "الأحجام الصغيرة — النص يفقد وضوحه تحت 96px، فاستخدم العلامة", F(30, "bold"), INK, anchor="ra")
for i, sz in enumerate([96, 64, 48, 32]):
    x = 900 - i * 220
    m = mark.resize((sz, sz), Image.LANCZOS)
    im.paste(m, (x - sz//2, 1670), m)
    d.text((x, 1790), f"{sz}px", font=F(26), fill=MUTED, anchor="mm")
ar(d, (984, 1852), "قواعد: لا تمديد، لا تغيير ألوان، لا خلفية مزخرفة تحته، واترك هامشا حوله.", F(28), MUTED, anchor="ra")
ar(d, (540, 1896), "الملف الأصلي محفوظ دون تعديل في logo/maqraat-al-noor-logo.jpg", F(26), MUTED, anchor="mm")

im.save(os.path.join(BASE, "09-logo-usage.png"), optimize=True)
print("09-logo-usage.png", os.path.getsize(os.path.join(BASE, "09-logo-usage.png")))
print("logo-mark.png", os.path.getsize(os.path.join(OUT_DIR, "logo-mark.png")))
print("app-icon-1024.png", os.path.getsize(os.path.join(OUT_DIR, "app-icon-1024.png")))
