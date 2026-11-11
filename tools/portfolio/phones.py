"""Rinse screens in Tony's Pixel 8 frame, same layout and canvas as Relay and Sniff (3560 x 1717).
Run from the repo root: python3 tools/portfolio/phones.py
Writes design/portfolio/rinse_app.png (four phones, transparent) and rinse_pair-*.jpg (two phones, white)."""
from PIL import Image, ImageChops
import os
D = os.path.join(os.path.dirname(__file__), "../../design/portfolio")
S, W, H, PW, STEP = 2, 3560, 1717, 837.457, 907.514   # from the Figma frame (1x)
APP = ["01-night-start", "02-night-washing", "03-morning-clean", "06-error-door-open"]
PAIRS = {"night": ["01-night-start", "02-night-washing"], "morning": ["03-morning-clean", "04-morning-clean-top-up"],
         "fails": ["06-error-door-open", "07-error-no-connection"], "settings": ["05-night-top-up", "08-settings"]}
frame = Image.open(os.path.join(D, "pixel8-frame@2x.png")).convert("RGBA")
mask = ImageChops.multiply(frame.getchannel("R").point(lambda v: max(0, min(255, round((46 - v) / 20 * 255)))), frame.getchannel("A"))
sx, sy, sw, sh = [round(v * S) for v in (44.077, 42.073, 751.307, 1626.830)]
def phone(name):
    p = frame.copy()
    shot = Image.open(os.path.join(D, f"rinse-{name}.png")).convert("RGBA").resize((sw, sh), Image.LANCZOS)
    layer = Image.new("RGBA", p.size, (0, 0, 0, 0)); layer.paste(shot, (sx, sy))
    p.paste(layer, (0, 0), ImageChops.multiply(mask, layer.getchannel("A")))
    return p
c = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
for i, n in enumerate(APP): c.alpha_composite(phone(n), (round(i * STEP * S), 0))
c.resize((W, H), Image.LANCZOS).save(os.path.join(D, "rinse_app.png"), optimize=True)
for key, shots in PAIRS.items():
    c = Image.new("RGBA", (W * S, H * S), (255, 255, 255, 255))
    x0 = (W - (STEP + PW)) / 2
    for i, n in enumerate(shots): c.alpha_composite(phone(n), (round((x0 + i * STEP) * S), 0))
    c.resize((W, H), Image.LANCZOS).convert("RGB").save(os.path.join(D, f"rinse_pair-{key}.jpg"), quality=90)
print("done")
