"""submission/application.md를 공식 접수양식 DOCX의 02 칸(1~8번)에 넣는다.

사용: python3 submission/build_application.py [출력 경로]
양식 원본: submission/contest-application-consent.docx (공모전 사이트 배포본, 그대로 둔다)
01 참가신청서·03 동의서·04 서명란은 건드리지 않는다. 앱명 줄만 채운다.
"""
import html
import re
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
TEMPLATE = HERE / "contest-application-consent.docx"
SOURCE = HERE / "application.md"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "M92_DriveWatch_접수양식.docx"

BOX_TAGS = ["app_summary", "planning_background", "vehicle_scenario", "key_features",
            "pleos_plan", "reference_files", "reference_link", "shortform_link"]
CELL_WIDTH = 9866 - 220  # 양식 칸 너비 - 좌우 여백
BODY_SZ, TABLE_SZ = 20, 18
NAVY = "16324F"
HYPERLINK_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"

links = []  # (rId, url)


def esc(text):
    return html.escape(text, quote=False)


def run(text, *, bold=False, italic=False, code=False, mark=False, color=None, sz=BODY_SZ):
    props = []
    if code:
        props.append('<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/>')
    if bold:
        props.append("<w:b/><w:bCs/>")
    if italic:
        props.append("<w:i/><w:iCs/>")
    if color:
        props.append(f'<w:color w:val="{color}"/>')
    props.append(f'<w:sz w:val="{sz}"/><w:szCs w:val="{sz}"/>')
    if mark:
        props.append('<w:highlight w:val="yellow"/>')
    if code:
        props.append('<w:shd w:val="clear" w:color="auto" w:fill="EEF1F4"/>')
    props.append('<w:lang w:eastAsia="ko-KR"/>')
    return f'<w:r><w:rPr>{"".join(props)}</w:rPr><w:t xml:space="preserve">{esc(text)}</w:t></w:r>'


INLINE = re.compile(r"(\[\[.+?\]\]|\*\*.+?\*\*|`.+?`|\*[^*\s][^*]*?\*|https?://[^\s)]+)")


def inline(text, **base):
    out = []
    for part in INLINE.split(text):
        if not part:
            continue
        if part.startswith("[["):
            out.append(run(part[2:-2], mark=True, bold=True, **base))
        elif part.startswith("**"):
            out.append(run(part[2:-2], **{**base, "bold": True}))
        elif part.startswith("`"):
            out.append(run(part[1:-1], code=True, **base))
        elif part.startswith("*"):
            out.append(run(part[1:-1], **{**base, "italic": True}))
        elif part.startswith("http"):
            rid = f"rIdDW{len(links) + 1}"
            links.append((rid, part))
            out.append(f'<w:hyperlink r:id="{rid}" w:history="1">'
                       + run(part, color="1F5FBF", **base).replace("<w:rPr>", '<w:rPr><w:u w:val="single"/>', 1)
                       + "</w:hyperlink>")
        else:
            out.append(run(part, **base))
    return "".join(out)


def para(content, *, before=0, after=60, ind=None, keep=False, numbered=False, jc=None):
    ppr = []
    if keep:
        ppr.append("<w:keepNext/>")
    if numbered:
        ppr.append('<w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr>')
    ppr.append(f'<w:spacing w:before="{before}" w:after="{after}" w:line="300" w:lineRule="auto"/>')
    if numbered:
        ppr.append('<w:ind w:left="360" w:hanging="240"/>')
    elif ind:
        ppr.append(f'<w:ind w:left="{ind}"/>')
    if jc:
        ppr.append(f'<w:jc w:val="{jc}"/>')
    return f'<w:p><w:pPr>{"".join(ppr)}</w:pPr>{content}</w:p>'


def column_widths(rows, total):
    def weight(cell):
        return len(re.sub(r"[*`\[\]]", "", cell))
    cols = len(rows[0])
    need = [max(min(max(weight(r[c]) for r in rows), 60), 4) for c in range(cols)]
    raw = [n ** 0.75 for n in need]
    widths = [max(int(total * w / sum(raw)), 900) for w in raw]
    widths[-1] += total - sum(widths)
    return widths


def table(lines):
    rows = [[c.strip() for c in line.strip().strip("|").split("|")] for line in lines]
    align = ["right" if re.fullmatch(r"-+:", c) else None for c in rows[1]]
    body = [rows[0]] + rows[2:]
    widths = column_widths(body, CELL_WIDTH)
    border = '<w:{0} w:val="single" w:sz="4" w:space="0" w:color="A6B4C2"/>'
    xml = ['<w:tbl><w:tblPr>',
           f'<w:tblW w:w="{CELL_WIDTH}" w:type="dxa"/>',
           "<w:tblBorders>" + "".join(border.format(s) for s in ("top", "left", "bottom", "right", "insideH", "insideV")) + "</w:tblBorders>",
           '<w:tblLayout w:type="fixed"/>',
           '<w:tblCellMar><w:top w:w="50" w:type="dxa"/><w:left w:w="80" w:type="dxa"/><w:bottom w:w="50" w:type="dxa"/><w:right w:w="80" w:type="dxa"/></w:tblCellMar>',
           '<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="0" w:lastColumn="0" w:noHBand="0" w:noVBand="1"/>',
           "</w:tblPr><w:tblGrid>" + "".join(f'<w:gridCol w:w="{w}"/>' for w in widths) + "</w:tblGrid>"]
    for i, row in enumerate(body):
        header = i == 0
        xml.append("<w:tr>" + ('<w:trPr><w:tblHeader/><w:cantSplit/></w:trPr>' if header else "<w:trPr><w:cantSplit/></w:trPr>"))
        for c, cell in enumerate(row):
            shade = '<w:shd w:val="clear" w:color="auto" w:fill="E3EAF1"/>' if header else ""
            content = inline(cell, sz=TABLE_SZ, **({"bold": True, "color": NAVY} if header else {}))
            p = ('<w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="260" w:lineRule="auto"/>'
                 + (f'<w:jc w:val="{align[c]}"/>' if align[c] and not header else "")
                 + f"</w:pPr>{content}</w:p>")
            xml.append(f'<w:tc><w:tcPr><w:tcW w:w="{widths[c]}" w:type="dxa"/>{shade}<w:vAlign w:val="center"/></w:tcPr>{p}</w:tc>')
        xml.append("</w:tr>")
    xml.append("</w:tbl>")
    # 표 뒤에 간격용 빈 문단
    return "".join(xml) + para("", after=40)


def render_box(lines):
    out, i = [], 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if not stripped:
            i += 1
            continue
        if stripped.startswith("|"):
            block = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                block.append(lines[i])
                i += 1
            out.append(table(block))
            continue
        if stripped.startswith("### "):
            out.append(para(inline(stripped[4:], bold=True, color=NAVY, sz=21), before=160 if out else 0, after=60, keep=True))
        elif re.match(r"^\s{2,}- ", line):
            out.append(para(inline(stripped[2:]), after=30, ind=720))
        elif stripped.startswith("- "):
            out.append(para(inline(stripped[2:]), after=40, numbered=True))
        elif stripped.startswith("> "):
            out.append(para(inline(stripped[2:], italic=True, color="34495E"), after=80, ind=240))
        else:
            out.append(para(inline(stripped), after=80))
        i += 1
    # 칸 안의 마지막 문단은 여백 없이
    return "".join(out) or para("")


def parse_source():
    text = re.sub(r"<!--.*?-->", "", SOURCE.read_text(encoding="utf8"), flags=re.S)
    name = re.search(r"^@앱명 (.+)$", text, re.M).group(1).strip()
    boxes = {}
    for m in re.finditer(r"^## (\d)\. .*?\n(.*?)(?=^## \d\. |\Z)", text, re.S | re.M):
        boxes[int(m.group(1))] = m.group(2).splitlines()
    missing = [n for n in range(1, 9) if n not in boxes]
    if missing:
        raise SystemExit(f"application.md에 칸이 없습니다: {missing}")
    return name, boxes


def fill_name(doc, name):
    """'앱명 / 프로젝트명: ____' 문단의 밑줄을 앱명으로 바꾼다. 줄바꿈 run은 유지한다."""
    def repl(m):
        p = m.group(0)
        if "프로젝트명" not in "".join(re.findall(r"<w:t[^>]*>([^<]*)</w:t>", p)):
            return p
        head = re.match(r"<w:p[ >].*?(?:</w:pPr>|(?=<w:r[ >]))", p, re.S)
        prefix = head.group(0) if head else p[:p.index(">") + 1]
        runs = re.findall(r"<w:r[ >].*?</w:r>|<w:proofErr[^>]*/>", p[len(prefix):], re.S)
        before, after, seen_text = [], [], False
        for r in runs:
            if "<w:t" in r:
                seen_text = True
            elif "<w:br/>" in r:
                (after if seen_text else before).append(r)
        label = '<w:r><w:rPr><w:lang w:eastAsia="ko-KR"/></w:rPr><w:t xml:space="preserve">앱명 / 프로젝트명: </w:t></w:r>'
        value = f'<w:r><w:rPr><w:b/><w:lang w:eastAsia="ko-KR"/></w:rPr><w:t>{esc(name)}</w:t></w:r>'
        return prefix + "".join(before) + label + value + "".join(after) + "</w:p>"
    pattern = re.compile(r"<w:p[ >](?:(?!<w:p[ >]|</w:p>).)*?프로젝트명.*?</w:p>", re.S)
    doc, count = pattern.subn(repl, doc)
    return doc, count


def fill_boxes(doc, boxes):
    for n, tag in enumerate(BOX_TAGS, start=1):
        sdt = re.search(rf'<w:sdt><w:sdtPr>(?:(?!</w:sdtPr>).)*?<w:tag w:val="{tag}"/>.*?</w:sdt>', doc, re.S)
        if not sdt:
            raise SystemExit(f"양식에서 {n}번 칸({tag})을 찾지 못했습니다")
        # 칸 = sdt를 감싼 문단. 그 문단 전체를 내용 문단들로 바꾼다.
        p_start = doc.rfind("<w:p ", 0, sdt.start())
        p_end = doc.index("</w:p>", sdt.end()) + len("</w:p>")
        doc = doc[:p_start] + render_box(boxes[n]) + doc[p_end:]
    return doc


def main():
    name, boxes = parse_source()
    with zipfile.ZipFile(TEMPLATE) as z:
        files = {i.filename: z.read(i.filename) for i in z.infolist() if not i.filename.endswith("/")}
    doc = files["word/document.xml"].decode("utf8")
    doc, named = fill_name(doc, name)
    doc = fill_boxes(doc, boxes)
    files["word/document.xml"] = doc.encode("utf8")
    rels = files["word/_rels/document.xml.rels"].decode("utf8")
    extra = "".join(f'<Relationship Id="{rid}" Type="{HYPERLINK_TYPE}" Target="{html.escape(url)}" TargetMode="External"/>'
                    for rid, url in links)
    files["word/_rels/document.xml.rels"] = rels.replace("</Relationships>", extra + "</Relationships>").encode("utf8")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        for fn, data in files.items():
            z.writestr(fn, data)
    print(f"{OUT} · 앱명 {named}곳, 칸 8개, 링크 {len(links)}개")


if __name__ == "__main__":
    main()
