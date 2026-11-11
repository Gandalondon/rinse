"""Rinse widgets, scaled up, with the app icon, on the 3560 x 1717 portfolio canvas.
Run: python3 tools/portfolio/widgets.py <path to roboto-latin-500-normal.woff2>
Writes design/portfolio/rinse_widgets.png. Widgets use Roboto Medium, as on the phone (Android widgets use the system font)."""
import os, sys, asyncio
from playwright.async_api import async_playwright
D = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../design/portfolio"))
ROBOTO = sys.argv[1]
INK, PAPER, BRICK, FOG = "#1A1A1A", "#FFFFFF", "#7A1E1E", "#F1F1F1"
K = 2.2  # widget scale against its real dp size
def plate(c, s): return f'<svg width="{s}" height="{s}" viewBox="0 0 24 24"><path fill="{c}" d="M3,16 A9,9 0 0 1 21,16 Z"/></svg>'
def tall(bg, fg, text, size=26):
    return (f'<div class="w" style="width:{164*K}px;height:{164*K}px;background:{bg};flex-direction:column;padding:{16*K}px">'
            f'{plate(fg, 32*K)}<div style="flex:1;display:flex;align-items:flex-end;margin-top:{8*K}px;font-size:{size*K}px;line-height:1.15;color:{fg}">{text}</div></div>')
def row(bg, fg, text, w):
    return (f'<div class="w" style="width:{w}px;height:{72*K}px;background:{bg};align-items:center;padding:0 {16*K}px;gap:{12*K}px">'
            f'{plate(fg, 28*K)}<div style="font-size:{18*K}px;color:{fg}">{text}</div></div>')
CELL, GAP = 164 * K, 48
WIDE = (4 * CELL + 3 * GAP - GAP) / 2
icon = (f'<svg width="{CELL}" height="{CELL}" viewBox="0 0 120 120"><rect width="120" height="120" rx="27" fill="{INK}"/>'
        f'<path d="M28 72 A32 32 0 0 1 92 72 Z" fill="{PAPER}"/></svg>')
html = f'''<html><head><style>@font-face{{font-family:R;src:url(file://{ROBOTO});font-weight:500}}
*{{margin:0;box-sizing:border-box}}body{{width:1780px;height:858px;background:{FOG};display:flex;align-items:center;justify-content:center;font-family:R;font-weight:500;-webkit-font-smoothing:antialiased}}
.g{{display:flex;flex-direction:column;gap:{GAP}px}}.r{{display:flex;gap:{GAP}px}}.w{{display:flex;border-radius:{24*K}px;text-wrap:balance}}</style></head><body>
<div class="g"><div class="r">{icon}{tall(INK, PAPER, "Tap to start Eco 50.")}{tall(PAPER, INK, "Clean.")}{tall(BRICK, PAPER, "Door open. Close it, then try again.", 20)}</div>
<div class="r">{row(INK, PAPER, "Washing, done at 03:31.", WIDE)}{row(PAPER, INK, "Clean. Add salt and rinse aid.", WIDE)}</div></div></body></html>'''
async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path="/usr/bin/google-chrome", args=["--allow-file-access-from-files"])
        pg = await b.new_page(viewport={"width": 1780, "height": 858}, device_scale_factor=2)
        f = os.path.join(D, "_widgets.html"); open(f, "w").write(html)
        await pg.goto("file://" + f); await pg.wait_for_timeout(400)
        await pg.screenshot(path=os.path.join(D, "rinse_widgets.png")); os.remove(f); await b.close()
asyncio.run(main())
