"""Generate original radio-program receivers and convert the attributed CCTV-13 SVG."""

from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res" / "drawable"
A = "http://schemas.android.com/apk/res/android"
T = "http://schemas.android.com/aapt"
ET.register_namespace("android", A)
ET.register_namespace("aapt", T)


def path(parent, data, fill="#FFFFFF", stroke=None, width=4):
    attributes = {f"{{{A}}}pathData": data, f"{{{A}}}fillColor": fill}
    if stroke:
        attributes.update({
            f"{{{A}}}strokeColor": stroke,
            f"{{{A}}}strokeWidth": str(width),
            f"{{{A}}}strokeLineCap": "round",
            f"{{{A}}}strokeLineJoin": "round",
        })
    return ET.SubElement(parent, "path", attributes)


def line(parent, data, color="#FFFFFF", width=4):
    return path(parent, data, "#00000000", color, width)


def circle(parent, x, y, radius, fill):
    path(parent, f"M{x-radius},{y} a{radius},{radius} 0,1 0,{radius*2},0 "
                 f"a{radius},{radius} 0,1 0,{-radius*2},0", fill)


def badge(start, end):
    vector = ET.Element("vector", {
        f"{{{A}}}width": "96dp", f"{{{A}}}height": "96dp",
        f"{{{A}}}viewportWidth": "96", f"{{{A}}}viewportHeight": "96",
    })
    shape = path(vector, "M28,6 H68 Q90,6 90,28 V68 Q90,90 68,90 H28 Q6,90 6,68 V28 Q6,6 28,6")
    del shape.attrib[f"{{{A}}}fillColor"]
    attribute = ET.SubElement(shape, f"{{{T}}}attr", {"name": "android:fillColor"})
    ET.SubElement(attribute, "gradient", {
        f"{{{A}}}startX": "12", f"{{{A}}}startY": "8",
        f"{{{A}}}endX": "82", f"{{{A}}}endY": "90",
        f"{{{A}}}startColor": start, f"{{{A}}}endColor": end,
    })
    path(vector, "M28,6 H68 Q90,6 90,28 V30 C62,17 36,47 6,43 V28 Q6,6 28,6", "#18FFFFFF")
    return vector


def save(name, vector):
    ET.indent(vector, space="    ")
    ET.ElementTree(vector).write(RES / f"radio_mark_{name}.xml", encoding="utf-8", xml_declaration=True)


def radio_badge(start, end):
    vector = badge(start, end)
    line(vector, "M57,26 L76,12", "#FFF2C1", 3)
    circle(vector, 76, 12, 2, "#FFF2C1")
    line(vector, "M27,26 V21 H49 V26", "#ECFFFFFF", 3)
    circle(vector, 18, 20, 3, "#FFDE83")
    path(vector, "M20,27 H76 Q85,27 85,36 V72 Q85,82 76,82 H20 Q11,82 11,72 "
                 "V36 Q11,27 20,27", "#240B1831", "#EDFFFFFF", 2.5)
    path(vector, "M60,44 V34 H67 V36 H62 V38 H66 V40 H62 V44 Z "
                 "M69,44 V34 H71 L74,38 L77,34 H79 V44 H77 V38 L74,42 L71,38 V44 Z")
    circle(vector, 70, 60, 9, "#F4FAFF")
    circle(vector, 70, 60, 5, end)
    line(vector, "M70,60 L73,56", "#FFFFFF", 2)
    line(vector, "M20,76 H77", "#B3FFFFFF", 1.5)
    for x in range(24, 76, 8):
        line(vector, f"M{x},74 V78", "#B3FFFFFF", 1)
    line(vector, "M43,73 V79", "#FFE18C", 2)
    glyph = ET.SubElement(vector, "group", {
        f"{{{A}}}scaleX": "0.60", f"{{{A}}}scaleY": "0.60",
        f"{{{A}}}translateX": "7", f"{{{A}}}translateY": "27",
    })
    return vector, glyph


marks = {
    "news": ("#218DA1", "#24449A"),
    "talk": ("#EB8045", "#B83955"),
    "pop": ("#A459D3", "#5A42A5"),
    "classic": ("#C18F22", "#805028"),
    "piano": ("#5367B1", "#29385D"),
    "acoustic": ("#E79B3B", "#A45132"),
    "jazz": ("#B64C87", "#633D84"),
    "world": ("#31A5A4", "#246CB0"),
    "ambient": ("#299EC6", "#3555AB"),
    "folk": ("#53996A", "#2B6356"),
    "dance": ("#D35A9F", "#5554C0"),
}
for name, colors in marks.items():
    vector, v = radio_badge(*colors)
    if name == "news":
        path(v, "M23,22 H69 Q74,22 74,27 V68 Q74,73 69,73 H23 Q18,73 18,68 V27 Q18,22 23,22")
        path(v, "M25,31 H46 V49 H25 Z", "#27639B")
        line(v, "M54,32 H66 M54,41 H66 M54,50 H66 M26,58 H66 M26,65 H58", "#27639B", 3)
    elif name == "talk":
        path(v, "M40,20 Q40,16 48,16 Q56,16 56,24 V46 Q56,56 48,56 Q40,56 40,46 Z")
        line(v, "M32,43 V47 Q32,63 48,63 Q64,63 64,47 V43 M48,63 V75 M37,75 H59")
        line(v, "M44,28 H52 M44,35 H52 M44,42 H52", "#C35656", 2)
    elif name == "pop":
        line(v, "M25,52 V45 A23,23 0,0 1,71,45 V52", width=6)
        path(v, "M22,45 H30 Q35,45 35,50 V65 Q35,70 30,70 H27 Q20,70 20,62 V52 Q20,45 22,45 "
                "M74,45 H66 Q61,45 61,50 V65 Q61,70 66,70 H69 Q76,70 76,62 V52 Q76,45 74,45")
        line(v, "M43,45 V58 M53,39 V62", "#FFD3F4", 3)
    elif name == "classic":
        circle(v, 48, 48, 29, "#FDE6AD")
        circle(v, 48, 48, 23, "#895636")
        circle(v, 48, 48, 17, "#D4A45A")
        circle(v, 48, 48, 11, "#FDE6AD")
        circle(v, 48, 48, 4, "#895636")
        line(v, "M30,38 Q34,29 44,28 M54,68 Q64,65 68,56", "#FFEFCB", 2)
    elif name == "piano":
        path(v, "M26,23 H70 Q75,23 75,28 V68 Q75,73 70,73 H26 Q21,73 21,68 V28 Q21,23 26,23")
        line(v, "M34,24 V72 M48,24 V72 M62,24 V72", "#536292", 2)
        path(v, "M29,23 H39 V50 H29 Z M43,23 H53 V50 H43 Z M57,23 H67 V50 H57 Z", "#29385D")
    elif name == "acoustic":
        path(v, "M54,37 L69,20 L77,28 L60,44 Q66,55 57,63 Q54,65 51,65 "
                "Q49,76 39,77 Q26,77 22,66 Q18,55 28,49 Q27,40 35,36 Q44,31 54,37")
        circle(v, 42, 55, 7, "#98542F")
        line(v, "M36,61 L69,28 M32,67 L42,67", "#E1A764", 2)
        line(v, "M65,21 L70,26 M71,18 L77,24", "#FFFFFF", 2)
    elif name == "jazz":
        path(v, "M27,37 H55 Q62,37 66,31 L77,21 V67 L66,57 Q62,51 55,51 H27 Z")
        line(v, "M21,37 V51 M21,44 H27 M39,51 V64 Q39,72 47,72 H53 Q60,72 60,64 V51", width=4)
        line(v, "M40,30 V37 M50,30 V37 M60,30 V37", "#FFE0B2", 3)
    elif name == "world":
        circle(v, 48, 48, 28, "#E4FFFF")
        path(v, "M22,39 L33,27 L40,30 L42,40 L34,47 L33,56 L26,55 Z "
                "M47,23 L61,28 L68,39 L61,45 L54,43 L49,51 L58,57 L56,68 L48,75 "
                "L43,62 L46,54 L40,47 L46,39 Z M68,59 L75,56 L70,70 L62,71 Z", "#318CA9")
        line(v, "M21,48 H75 M48,20 Q30,48 48,76 M48,20 Q66,48 48,76", "#6CC4D4", 2)
    elif name == "ambient":
        path(v, "M52,19 C35,25 38,50 58,50 Q65,50 70,46 C69,66 45,76 29,61 "
                "C12,44 26,20 44,19 Z", "#FFF0C0")
        line(v, "M64,26 V41 M64,26 L76,23", "#FFFFFF", 4)
        circle(v, 60, 43, 5, "#FFFFFF")
        circle(v, 30, 74, 2, "#B8EEFF")
    elif name == "folk":
        path(v, "M43,45 L65,20 L74,27 L52,53 Z", "#FFE6A5")
        circle(v, 38, 57, 21, "#F5FAE8")
        circle(v, 38, 57, 15, "#3D7C5E")
        circle(v, 38, 57, 11, "#F5FAE8")
        line(v, "M31,64 L69,25 M28,68 L39,68", "#518B63", 2)
        line(v, "M62,21 L67,26 M69,17 L75,22", "#FFFFFF", 3)
    else:
        for x, top, bottom in ((25,39,61), (36,27,73), (47,20,78), (58,32,68), (69,42,58)):
            line(v, f"M{x},{top} V{bottom}", "#FFFFFF" if x == 47 else "#F5D7FF", 6)
        circle(v, 72, 23, 3, "#FFE7AD")
    save(name, vector)

cctv = badge("#FFFFFF", "#E8EDF4")
group = ET.SubElement(cctv, "group", {
    f"{{{A}}}scaleX": "0.06713781", f"{{{A}}}scaleY": "0.06713781",
    f"{{{A}}}translateX": "10", f"{{{A}}}translateY": "32",
})
source = ET.parse(ROOT / "artwork" / "cctv13-source.svg").getroot()
for item in source.iter("{http://www.w3.org/2000/svg}path"):
    converted = path(group, item.attrib["d"], "#E50000" if item.attrib.get("class") == "s1" else "#151B24")
    if item.attrib.get("fill-rule") == "evenodd":
        converted.set(f"{{{A}}}fillType", "evenOdd")
save("cctv", cctv)
