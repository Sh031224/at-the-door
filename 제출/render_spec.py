"""기획서 Markdown을 한글 폰트를 포함한 PDF로 만든다.

실행: python 제출/render_spec.py
의존성: reportlab, markdown-it-py
"""
from pathlib import Path
from html import escape
import re
from markdown_it import MarkdownIt
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "문앞_앱개발_시나리오_기능명세.md"
FONTS = ROOT / "docs/ux-concept-2026-11-14/assets/fonts"
for name, file in [("Asta", "AstaSans-Regular.ttf"), ("AstaBold", "AstaSans-SemiBold.ttf")]:
    pdfmetrics.registerFont(TTFont(name, str(FONTS / file)))
pdfmetrics.registerFontFamily("Asta", normal="Asta", bold="AstaBold", italic="Asta", boldItalic="AstaBold")
body = ParagraphStyle("body", fontName="Asta", fontSize=9.2, leading=14, textColor=colors.HexColor("#252b31"), wordWrap="CJK", spaceAfter=8)
small = ParagraphStyle("cell", parent=body, fontSize=8.1, leading=12, spaceAfter=0)
heads = {i: ParagraphStyle(f"h{i}", parent=body, fontName="AstaBold", fontSize={1:23,2:15,3:12,4:10.5}.get(i,10.5), leading={1:32,2:22,3:18}.get(i,16), spaceBefore=15 if i>1 else 0, spaceAfter=9, keepWithNext=True) for i in range(1,7)}


def inline(text):
    value = escape(text)
    value = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r"\1 (\2)", value)
    value = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", value)
    value = re.sub(r"`([^`]+)`", r"\1", value)
    return value


tokens = MarkdownIt("commonmark").enable("table").parse(SOURCE.read_text())
flow = []
i = 0
while i < len(tokens):
    token = tokens[i]
    if token.type == "heading_open":
        level = int(token.tag[1:])
        flow.append(Paragraph(inline(tokens[i+1].content), heads[level]))
        i += 3
        continue
    if token.type == "table_open":
        rows, row = [], []
        i += 1
        while tokens[i].type != "table_close":
            t = tokens[i]
            if t.type == "tr_open": row = []
            if t.type in ("th_open", "td_open"):
                text = tokens[i+1].content
                row.append(Paragraph(("<b>" + inline(text) + "</b>") if t.type == "th_open" else inline(text), small))
            if t.type == "tr_close": rows.append(row)
            i += 1
        count = len(rows[0])
        ratios = {2:[.27,.73],3:[.18,.40,.42],4:[.16,.38,.34,.12]}.get(count,[1/count]*count)
        table = Table(rows, colWidths=[r*(A4[0]-92) for r in ratios], repeatRows=1, hAlign="LEFT")
        table.setStyle(TableStyle([("BACKGROUND",(0,0),(-1,0),colors.HexColor("#e8ecef")),("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white,colors.HexColor("#f7f8f9")]),("GRID",(0,0),(-1,-1),.4,colors.HexColor("#d9d9d9")),("VALIGN",(0,0),(-1,-1),"MIDDLE"),("LEFTPADDING",(0,0),(-1,-1),8),("RIGHTPADDING",(0,0),(-1,-1),8),("TOPPADDING",(0,0),(-1,-1),8),("BOTTOMPADDING",(0,0),(-1,-1),8)]))
        flow.extend([table,Spacer(1,12)])
    elif token.type == "paragraph_open":
        text = tokens[i+1].content
        # 항목은 표와 별개로 자연스럽게 이어지도록 표시한다.
        prefix = "• " if i > 0 and tokens[i-1].type == "list_item_open" else ""
        flow.append(Paragraph(prefix + inline(text), body))
        i += 2
    elif token.type in ("fence", "code_block"):
        for line in token.content.strip().splitlines():
            flow.append(Paragraph(escape(line) or " ", small))
        flow.append(Spacer(1,10))
    i += 1


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("Asta",8)
    canvas.setFillColor(colors.HexColor("#727b83"))
    canvas.drawString(46,29,"문앞 · 앱 개발 시나리오와 기능 명세")
    canvas.drawRightString(A4[0]-46,29,str(doc.page))
    canvas.restoreState()


target = SOURCE.with_suffix(".pdf")
SimpleDocTemplate(str(target),pagesize=A4,rightMargin=46,leftMargin=46,topMargin=43,bottomMargin=45,title="문앞 앱 개발 시나리오와 기능 명세",author="",pageCompression=1).build(flow,onFirstPage=footer,onLaterPages=footer)
print(target.name)
