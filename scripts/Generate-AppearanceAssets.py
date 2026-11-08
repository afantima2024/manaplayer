"""Regenerate the centered logos and shared, day/night button gradients."""

from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)


def fitted(source, size):
    content = source.crop(source.getbbox())
    content.thumbnail((round(size * 0.88), round(size * 0.88)), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size))
    canvas.alpha_composite(content, ((size - content.width) // 2, (size - content.height) // 2))
    return canvas


for name, launcher, player in (
    ("music", "ic_launcher", "player_logo"),
    ("radio", "ic_launcher_radio", "player_radio_logo"),
):
    with Image.open(ROOT / "artwork" / f"{name}-source.png") as original:
        image = original.convert("RGBA")
        for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
            destination = RES / f"drawable-{density}"
            destination.mkdir(exist_ok=True)
            fitted(image, size).save(destination / f"{launcher}.png", optimize=True)
        fitted(image, 512).save(RES / "drawable-nodpi" / f"{player}.png", optimize=True)

palettes = (
    ("Original", "original", ("#087C6C", "#2154B7"), ("#79EBC6", "#75BFFF")),
    ("Ocean", "ocean", ("#007B95", "#344DB4"), ("#62E2F2", "#98ADFF")),
    ("Aurora", "aurora", ("#328000", "#007A70"), ("#C2EA7E", "#66DBC8")),
    ("Sunset", "sunset", ("#A55E00", "#B32C69"), ("#FFD083", "#FF94BB")),
    ("Orchid", "orchid", ("#6246B6", "#A52C86"), ("#BFA9FF", "#F69FD7")),
    ("Logo", "logo",
     ("#98640D", "#007F7B", "#2B67CB", "#7648BD", "#B32A70"),
     ("#FFD172", "#45DBDA", "#74A9FF", "#B995FF", "#FF80B8")),
    ("CopilotAurora", "copilot_aurora",
     ("#B3367E", "#8B44B3", "#4D51BD", "#006BAE", "#007D82"),
     ("#F594CF", "#CB9AFF", "#9692FF", "#6BBFFF", "#53DDD7")),
    ("CopilotSunrise", "copilot_sunrise",
     ("#405FC3", "#8144B4", "#B12F85", "#B84636", "#98630B"),
     ("#8FAAFF", "#C29BFF", "#FF8ACD", "#FFAA91", "#FFD477")),
)
stops = ("Start", "First", "Middle", "Last", "End")


def expanded(colors):
    if len(colors) == len(stops):
        return colors
    start, end = (tuple(int(color[i:i + 2], 16) for i in (1, 3, 5)) for color in colors)
    return tuple("#" + "".join(f"{round(a + (b - a) * index / 4):02X}" for a, b in zip(start, end))
                 for index in range(5))


for night in (False, True):
    resources = ET.Element("resources")
    if not night:
        for stop in stops:
            ET.SubElement(resources, "attr", {"name": f"buttonGradient{stop}", "format": "color"})
    for style, name, day, dark in palettes:
        for stop, color in zip(stops, expanded(dark if night else day)):
            ET.SubElement(resources, "color", {"name": f"icon_{name}_{stop.lower()}"}).text = color
        if not night:
            overlay = ET.SubElement(resources, "style", {"name": f"ThemeOverlay.ManaPlayer.Icons.{style}", "parent": ""})
            for stop in stops:
                ET.SubElement(overlay, "item", {"name": f"buttonGradient{stop}"}).text = f"@color/icon_{name}_{stop.lower()}"
    ET.indent(resources, space="    ")
    ET.ElementTree(resources).write(RES / ("values-night" if night else "values") / "icon_palettes.xml",
                                    encoding="utf-8", xml_declaration=True)

icons = """play pause stop previous next rewind forward shuffle repeat repeat_one
radio folder volume_on volume_off favorite favorite_border pin library playlist queue note
lyrics_next lyrics_timing lyrics_versions info more settings close back expand_less expand_more"""
for icon in icons.split():
    source = ET.parse(RES / "drawable" / f"ic_{icon}.xml").getroot()
    source.attrib.pop(f"{{{ANDROID}}}tint", None)
    for path in source.iter("path"):
        for paint in ("fillColor", "strokeColor"):
            color = path.attrib.get(f"{{{ANDROID}}}{paint}")
            if color in (None, "#00000000", "@android:color/transparent"):
                continue
            del path.attrib[f"{{{ANDROID}}}{paint}"]
            attribute = ET.SubElement(path, f"{{{AAPT}}}attr", {"name": f"android:{paint}"})
            gradient = ET.SubElement(attribute, "gradient", {
                f"{{{ANDROID}}}type": "linear",
                f"{{{ANDROID}}}startX": "5", f"{{{ANDROID}}}startY": "5",
                f"{{{ANDROID}}}endX": "19", f"{{{ANDROID}}}endY": "19",
            })
            for index, stop in enumerate(stops):
                ET.SubElement(gradient, "item", {
                    f"{{{ANDROID}}}offset": str(index / 4),
                    f"{{{ANDROID}}}color": f"?attr/buttonGradient{stop}",
                })
    ET.indent(source, space="    ")
    ET.ElementTree(source).write(RES / "drawable" / f"ic_{icon}_color.xml", encoding="utf-8", xml_declaration=True)
