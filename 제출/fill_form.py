"""제출 양식 02장 1~7번 박스(표 1~7의 첫 셀)를 기획서 요약으로 채운다.
원본 셀의 제목·본문·글머리표 문단을 서식 견본으로 복제하고, 양식의 나머지 구조는 건드리지 않는다.
usage: venv/bin/python fill_form.py <in.docx> <out.docx> <content.json>
content.json: {"1": [["h"|"p"|"b", "텍스트"], ...], ...}
"""
import copy
import json
import sys

import docx
from docx.oxml.ns import qn


def classify(p):
    t = p.text.strip()
    if t.startswith("•"):
        return "b"
    runs = [r for r in p.runs if r.text.strip()]
    if runs and all(r.bold for r in runs):
        return "h"
    return "p"


def fill_cell(cell, items):
    paras = cell.paragraphs
    templates = {}
    for p in paras:
        templates.setdefault(classify(p), p)
    templates.setdefault("p", paras[0])
    templates.setdefault("h", templates["p"])
    templates.setdefault("b", templates["p"])
    tc = cell._tc
    new = []
    for kind, text in items:
        src = templates[kind]._p
        el = copy.deepcopy(src)
        runs = el.findall(qn("w:r"))
        for r in runs[1:]:
            el.remove(r)
        if not runs:
            raise SystemExit("template paragraph has no run")
        r0 = runs[0]
        for child in list(r0):
            if child.tag != qn("w:rPr"):
                r0.remove(child)
        t = r0.makeelement(qn("w:t"), {})
        t.text = ("• " + text) if kind == "b" else text
        t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        r0.append(t)
        new.append(el)
    for p in paras:
        tc.remove(p._p)
    for el in new:
        tc.append(el)


def main(src, dst, content_path):
    d = docx.Document(src)
    content = json.load(open(content_path, encoding="utf-8"))
    for key, items in content.items():
        fill_cell(d.tables[int(key)].rows[0].cells[0], items)
    cp = d.core_properties
    cp.last_modified_by = ""
    cp.author = ""
    cp.comments = ""
    d.save(dst)


if __name__ == "__main__":
    main(*sys.argv[1:4])
