#!/usr/bin/env python3
"""WCAG contrast audit for the palette in app/src/main/java/net/zash/clashpanel/ui/Theme.kt.

Android port of the HarmonyOS scripts/check-contrast.py (same thresholds, same accent presets).
Parses the Palette data-class defaults, the darkBase overrides and the ACCENTS presets, replicates
buildPalette() (accent tones, glass translucency) and Glass.kt (frosted header / bottom bar tint, GlassBackdrop
gradient with its accent and upload glows), then checks every text/icon token against every surface it is drawn on,
plus the Material 3 pairs MimiTheme() sets up (TextButton / OutlinedButton labels, focused / error text fields,
switch, radio, checkbox, slider, progress, filter chip, pull-to-refresh indicator, filled buttons via primaryButtonColors()).

Thresholds: body text >= 4.5, titles/icons/controls/large >= 3.0.
Translucent cards are checked at the default glass opacity (0.55, Prefs "glass_alpha"); a lower opacity chosen by the
user over a custom wallpaper is outside what a token check can guarantee.
Exit code 1 if anything fails.  Usage: python3 scripts/check-contrast.py [-v]
"""
import re, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
THEME = os.path.join(ROOT, 'app/src/main/java/net/zash/clashpanel/ui/Theme.kt')
src = open(THEME, encoding='utf-8').read()
VERBOSE = '-v' in sys.argv


def kcolor(expr):
    """Color(0xAARRGGBB) / Color.White / Color.Black -> '#RRGGBB'."""
    expr = expr.strip()
    if expr == 'Color.White':
        return '#FFFFFF'
    if expr == 'Color.Black':
        return '#000000'
    m = re.fullmatch(r'Color\(0x([0-9A-Fa-f]{8})\)', expr)
    assert m, 'unparsed color ' + expr
    return '#' + m.group(1)[2:].upper()


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def hexs(c):
    return '#' + ''.join('%02X' % max(0, min(255, round(v))) for v in c)


def lum(c):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(v) for v in rgb(c))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def cr(a, b):
    la, lb = lum(a), lum(b)
    if la < lb:
        la, lb = lb, la
    return (la + 0.05) / (lb + 0.05)


def over(fg, alpha, bg):
    f, b = rgb(fg), rgb(bg)
    return hexs(tuple(b[i] + (f[i] - b[i]) * alpha for i in range(3)))


def mix(a, b, t):  # Compose lerp(a, b, t) (sRGB approximation)
    return over(b, t, a)


# ---- parse Theme.kt ----
COL = r'(Color\(0x[0-9A-Fa-f]{8}\)|Color\.White|Color\.Black)'
pal_block = re.search(r'data class Palette\((.*?)\n\)', src, re.S).group(1)
LIGHT = {k: kcolor(v) for k, v in re.findall(r'val (\w+): Color = ' + COL, pal_block)}
dark_block = re.search(r'private val darkBase = Palette\((.*?)\n\)', src, re.S).group(1)
DARK = dict(LIGHT)
DARK.update({k: kcolor(v) for k, v in re.findall(r'(\w+) = ' + COL, dark_block)})
TONE = r'AccentTone\(%s, %s, %s, %s\)' % (COL, COL, COL, COL)
ACC = []
for m in re.finditer(r'AccentDef\("(\w+)", "(\w+)", %s, %s,\s*%s, %s\)' % (COL, COL, TONE, TONE), src):
    g = m.groups()
    ACC.append((g[0], g[1]) + tuple(kcolor(x) for x in g[2:]))
assert len(ACC) == 7, 'expected 7 accent presets, parsed %d' % len(ACC)
for need in ('accentUi', 'accentFill', 'upText', 'downText', 'info', 'subtle'):
    assert need in LIGHT, 'Palette has no ' + need


def build(dark, a):
    """Mirror of Theme.buildPalette() (accent part)."""
    p = dict(DARK if dark else LIGHT)
    key, _, light, darkc = a[:4]
    ui, text, fill, on = a[8:12] if dark else a[4:8]
    p.update(accentFill=darkc if dark else light, accent=text, accentUi=ui, primary=fill, onPrimary=on, onAccent=on)
    p['accentSoftA'] = 0.20 if dark else 0.11
    p['down'] = ui
    p['up'] = mix(ui, '#FFFFFF', 0.45) if dark else mix(ui, p['text'], 0.45)
    p['downText'] = text
    p['upText'] = mix(text, '#FFFFFF', 0.45) if dark else mix(text, p['text'], 0.45)
    p['mOnPrimary'] = '#15181C' if dark else '#FFFFFF'  # MimiTheme(): Material onPrimary paired with primary = accent
    return p


fails = []
worst = {}


def check(mode, acc, what, fg, bg, need):
    v = cr(fg, bg)
    k = (mode, what)
    if k not in worst or v < worst[k][0]:
        worst[k] = (v, acc, fg, bg)
    if v < need:
        fails.append('%-5s %-6s %-52s %s on %s = %.2f (< %.1f)' % (mode, acc, what, fg, bg, v, need))


GLASS_ALPHA = 0.55  # Prefs glass_alpha default; chips +0.15, sheet +0.2 (buildPalette)
for dark in (False, True):
    mode = 'dark' if dark else 'light'
    for a in ACC:
        p = build(dark, a)
        key = a[0]
        page = p['bg']
        deco = p['accentFill']
        # GlassBackdrop: linear gradient whose middle stop mixes bg with accentFill, an accentFill glow and an `up` glow
        if dark:
            mid = mix('#111316', deco, 0.18)
            glow = over(deco, 0.38, mid)
            upglow = over(p['up'], 0.30, '#0E1013')
        else:
            mid = mix('#F4F4F6', deco, 0.16)
            glow = over(deco, 0.30, mid)
            upglow = over(p['up'], 0.26, '#EEF0F4')
        backdrops = {'page': page, 'glass-mid': mid, 'glass-glow': glow, 'glass-upglow': upglow}
        base = '#1C1F24' if dark else '#FFFFFF'
        bar_a = 0.62 if dark else 0.66       # Glass.kt GlassKind.Bar minimum tint (blur supported)
        head_a = 0.55 if dark else 0.58      # Glass.kt GlassKind.Top minimum tint
        surfaces = {'page bg': page, 'card': p['card'], 'chip': p['chip'], 'sheet': p['sheet'], 'solidCard (dialog/menu)': p['solidCard']}
        for bname, b in backdrops.items():
            surfaces['glass card/%s' % bname] = over(p['card'], GLASS_ALPHA, b)
            surfaces['glass chip/%s' % bname] = over(p['chip'], GLASS_ALPHA + 0.15, b)
            surfaces['glass sheet/%s' % bname] = over(p['sheet'], min(GLASS_ALPHA + 0.2, 0.92), b)
            surfaces['bar/%s' % bname] = over(base, bar_a, b)
            surfaces['header/%s' % bname] = over(base, head_a, b)
        for sname, s in surfaces.items():
            check(mode, key, 'text on ' + sname, p['text'], s, 4.5)
            check(mode, key, 'subtle (secondary/label/placeholder) on ' + sname, p['subtle'], s, 4.5)
            check(mode, key, 'accent text (links, TextButton, M3 primary) on ' + sname, p['accent'], s, 4.5)
            check(mode, key, 'accentUi icon/control on ' + sname, p['accentUi'], s, 3.0)
            for t in ('bad', 'good', 'warn', 'info', 'upText', 'downText'):
                check(mode, key, t + ' text on ' + sname, p[t], s, 4.5)
            for t in ('up', 'down'):
                check(mode, key, t + ' icon / large stat on ' + sname, p[t], s, 3.0)
        # nav: selected = accentUi icon + accent label on an accentSoft pill over the bar
        for bname, b in backdrops.items():
            bar = over(base, bar_a, b)
            pill = over(p['accentUi'], p['accentSoftA'], bar)
            check(mode, key, 'nav selected label (accent) on pill/' + bname, p['accent'], pill, 4.5)
            check(mode, key, 'nav selected icon (accentUi) on pill/' + bname, p['accentUi'], pill, 3.0)
        for sname, s in (('card', p['card']), ('solidCard', p['solidCard']), ('glass card/glow', over(p['card'], GLASS_ALPHA, glow))):
            pill = over(p['accentUi'], p['accentSoftA'], s)
            check(mode, key, 'accent tag / FilterChip label on accentSoft/' + sname, p['accent'], pill, 4.5)
            check(mode, key, 'text on accentSoft (FilterChip, M3 containers)/' + sname, p['text'], pill, 4.5)
        # buttons / segmented tabs / active round icon buttons (primaryButtonColors, SegTabs, RoundIconButton)
        check(mode, key, 'onPrimary on primary (button, segtab, active icon)', p['onPrimary'], p['primary'], 4.5)
        check(mode, key, 'Material onPrimary on Material primary (accent)', p['mOnPrimary'], p['accent'], 4.5)
        check(mode, key, 'text on chip button', p['text'], p['chip'], 4.5)
        check(mode, key, 'subtle on segtab track (chip)', p['subtle'], p['chip'], 4.5)
        # Material controls on their usual surfaces (switch track, radio, progress, pull-refresh on solidCard)
        for sname, s in (('card', p['card']), ('solidCard', p['solidCard'])):
            check(mode, key, 'switch/radio/progress (accentUi) on ' + sname, p['accentUi'], s, 3.0)
        check(mode, key, 'progress (accentUi) vs track (chip)', p['accentUi'], p['chip'], 3.0)
        check(mode, key, 'checkbox checkmark / switch thumb on accentUi', p['mOnPrimary'], p['accentUi'], 3.0)
        check(mode, key, 'unchecked checkbox border (subtle) on solidCard', p['subtle'], p['solidCard'], 3.0)
        check(mode, key, 'text-field error border/label (bad) on solidCard', p['bad'], p['solidCard'], 4.5)
        # error banner (MainActivity): bad text on a tinted pill at 0.94 over the page
        banner = over('#3A1F22' if dark else '#FDECEC', 0.94, page)
        check(mode, key, 'bad text on error banner', p['bad'], banner, 4.5)
        check(mode, key, 'text on error banner', p['text'], banner, 4.5)

if VERBOSE or fails:
    print('Worst case per (mode, pair):')
    for (mode, what), (v, acc, fg, bg) in sorted(worst.items(), key=lambda x: x[1][0]):
        if VERBOSE or v < 4.5:
            print('  %-5s %-62s %5.2f  (%s %s on %s)' % (mode, what, v, acc, fg, bg))
print('%d failures' % len(fails))
for f in fails[:400] if VERBOSE else fails[:60]:
    print('  ' + f)
sys.exit(1 if fails else 0)
