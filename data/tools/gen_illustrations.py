import sys
W,H=1200,800
def svg(title, sign, sign_color, door_label, ramp, steps_note, extra=""):
    ramp_svg = ('<polygon points="700,620 980,620 980,560 760,560" fill="#c9cfd8"/>'
                '<line x1="760" y1="560" x2="980" y2="545" stroke="#7a8696" stroke-width="6"/>'
                '<text x="840" y="660" font-size="30" fill="#334">경사로</text>') if ramp else ''
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<rect width="{W}" height="{H}" fill="#e9eef4"/>
<rect x="0" y="620" width="{W}" height="180" fill="#b8c0ca"/>
<rect x="140" y="140" width="920" height="480" fill="#f7f4ee" stroke="#8a8f98" stroke-width="4"/>
<rect x="140" y="140" width="920" height="70" fill="#5b6b7f"/>
<text x="600" y="188" font-size="38" fill="#fff" text-anchor="middle" font-family="Apple SD Gothic Neo, sans-serif" font-weight="700">{title}</text>
<rect x="470" y="330" width="260" height="290" fill="#a9c7e2" stroke="#4a5d73" stroke-width="6"/>
<line x1="600" y1="330" x2="600" y2="620" stroke="#4a5d73" stroke-width="4"/>
<text x="600" y="310" font-size="40" fill="#223" text-anchor="middle" font-family="Apple SD Gothic Neo, sans-serif" font-weight="700">{door_label}</text>
<rect x="210" y="300" width="200" height="120" rx="10" fill="{sign_color}"/>
<text x="310" y="372" font-size="34" fill="#fff" text-anchor="middle" font-family="Apple SD Gothic Neo, sans-serif" font-weight="700">{sign}</text>
<rect x="300" y="420" width="20" height="200" fill="#6b7480"/>
{ramp_svg}
{extra}

</svg>'''
items = {
 "saebom-hall/e-back-1": svg("새봄문화회관 후문", "후문 →", "#1f5fbf", "후문 자동문", False, "파란 안내판을 지나 후문으로"),
 "saebom-hall/e-back-2": svg("새봄문화회관 후문", "주차장 출구", "#1f5fbf", "후문", False, "주차장 출구 쪽 보행로 → 후문"),
 "saebom-hall/e-front-1": svg("새봄문화회관", "정문", "#2e7d4f", "정문 자동문", True, "정문 오른쪽 경사로로 올라가기"),
 "hanbit-hospital/e-main-1": svg("한빛종합병원 본관", "외래 접수", "#2e7d4f", "본관 정문", True, "회차로에서 본관 정문 자동문으로"),
 "hanbit-hospital/e-b2-1": svg("본관 지하 2층", "본관 엘리베이터", "#c26a00", "엘리베이터", False, "표지판을 따라 엘리베이터 홀로"),
}
import os
for k,v in items.items():
    os.makedirs(os.path.dirname(k), exist_ok=True)
    open(k+".svg","w").write(v)
print("\n".join(items))
