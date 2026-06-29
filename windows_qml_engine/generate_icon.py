from PIL import Image, ImageDraw, ImageFont
import os

def create_icon():
    size = 512
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    gold = (212, 175, 55, 255)
    dark_gold = (180, 140, 30, 255)
    bg = (10, 15, 26, 255)

    # خلفية داكنة
    draw.ellipse((20, 20, 492, 492), fill=bg)

    # إطار ذهبي خارجي
    draw.ellipse((25, 25, 487, 487), outline=gold, width=8)

    # دائرة ذهبية داخلية
    draw.ellipse((120, 120, 392, 392), fill=gold)

    # تاج مبسط
    crown_color = dark_gold
    draw.ellipse((230, 170, 282, 222), fill=crown_color)  # مركز
    draw.ellipse((170, 210, 222, 262), fill=crown_color)  # يسار
    draw.ellipse((290, 210, 342, 262), fill=crown_color)  # يمين

    # قاعدة التاج
    draw.polygon([(150, 260), (362, 260), (340, 300), (172, 300)], fill=gold)

    # نص "GP"
    try:
        font = ImageFont.truetype("arialbd.ttf", 80)
    except:
        font = ImageFont.load_default()
    draw.text((256, 340), "GP", fill=dark_gold, font=font, anchor="mm")

    # حفظ بصيغة ICO بأحجام متعددة
    sizes = [(16,16), (32,32), (48,48), (64,64), (128,128), (256,256)]
    img_resized = img.resize((256, 256), Image.Resampling.LANCZOS)
    img_resized.save("app.ico", format="ICO", sizes=sizes)
    print("✅ تم إنشاء app.ico بنجاح!")

if __name__ == "__main__":
    create_icon()