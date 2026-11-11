"""Renders the screen-plan mockups in opinions/drive-watch-screens.md to PNG.

Run: python3 docs/screens/render.py   (needs Google Chrome)
"""
import pathlib
import subprocess
import tempfile

OUT = pathlib.Path(__file__).parent
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
CAR = (1000, 460)
PHONE = (360, 720)

CSS = """
*{box-sizing:border-box;margin:0}
body{font-family:-apple-system,'Apple SD Gothic Neo',sans-serif;background:#000;color:#fff}
.car{width:1000px;height:460px;background:#0F1720;padding:16px;display:flex;flex-direction:column;gap:12px}
.bar{display:flex;align-items:center;gap:8px}
.logo{font-weight:700;font-size:18px;margin-right:8px}
.chip{border:1px solid #223040;border-radius:99px;padding:6px 14px;font-size:13px;color:#8AA0B5}
.chip.on{border-color:#5FA8FF;background:#223040;color:#fff;font-weight:700}
.sp{flex:1}
.dim{color:#8AA0B5}.ok{color:#5FD38D}.warn{color:#F5B85C}
.panel{background:#18232F;border-radius:12px;padding:12px 14px}
.raised{background:#223040;border-radius:12px}
.h1{font-size:22px;font-weight:700}.h2{font-size:18px;font-weight:700}
.s{font-size:13px}.xs{font-size:11px}
.btn{display:inline-block;border-radius:10px;padding:10px 18px;font-weight:700;font-size:15px;background:#223040;color:#fff;text-align:center}
.btn.p{background:#5FA8FF}
.state{border-radius:99px;padding:5px 12px;font-weight:700;font-size:13px;color:#0F1720}
.g-ok{background:#5FD38D}.g-warn{background:#F5B85C}.g-talk{background:#FF8A5C}.g-dim{background:#8AA0B5}
.row{display:flex;gap:12px}.col{display:flex;flex-direction:column;gap:10px}
.center{align-items:center;justify-content:center;text-align:center}
.f1{flex:1}
.input{border:1px solid #3A5875;border-radius:8px;padding:12px;font-size:15px;color:#fff}
.input .l{font-size:11px;color:#8AA0B5;display:block;margin-bottom:2px}
.tts{display:flex;gap:12px;align-items:center;border-radius:12px;padding:12px 14px;border:1px solid}
.tts.blue{border-color:#5FA8FF;background:#0F1720}.tts.amber{border-color:#F5B85C;background:#2B2110}
.tag{font-size:11px;padding:2px 8px;border-radius:6px;background:#3A2A55;color:#D9C7FF}
.demo{font-size:11px;color:#D9C7FF;background:#2A2040;border:1px dashed #8C6BD9;border-radius:8px;padding:6px 10px}
.toggle{width:40px;height:22px;border-radius:99px;background:#3A5875;position:relative;flex:none}
.toggle::after{content:'';position:absolute;top:3px;left:3px;width:16px;height:16px;border-radius:50%;background:#fff}
.toggle.on{background:#5FA8FF}.toggle.on::after{left:21px}
.phone{width:360px;height:720px;background:#fff;color:#111;border:8px solid #222;border-radius:34px;padding:18px 18px;display:flex;flex-direction:column;gap:10px}
.phone .top{font-size:11px;color:#888}
.phone .h{font-size:20px;font-weight:700}
.phone .card{background:#F5F6F8;border-radius:12px;padding:12px;font-size:14px;line-height:1.5}
.phone .card b{display:block;font-size:12px;color:#666;font-weight:600;margin-bottom:2px}
.phone .blue{background:#EEF5FF}
.phone .pin{border:1px solid #bbb;border-radius:8px;padding:12px;font-size:15px}
.phone .pin .l{font-size:11px;color:#777;display:block}
.phone .btn{background:#1F6FEB;width:100%;padding:14px}
.phone .btn.ghost{background:#EEF1F5;color:#111}
.phone .pill{align-self:flex-start;border-radius:99px;padding:4px 12px;font-size:13px;font-weight:700}
.bars{display:flex;align-items:flex-end;gap:3px;height:60px}
.bars i{flex:1;background:#3A5875;border-radius:2px}
.bars i.w{background:#F5B85C}
"""


def bar(active="김정숙", locked=False, demo=False):
    chips = "".join(f'<span class="chip{" on" if n == active else ""}">👤 {n}</span>' for n in ["김정숙", "박영호"])
    right = ('<span class="dim s">주행 중에는 운전자를 바꿀 수 없어요</span>' if locked
             else '<span class="chip">+ 운전자 추가</span><span class="sp"></span>'
             + ('<span class="demo">시연 패널 · 밤 9시로 · 다음 주로 · 4주 뒤로</span>' if demo else '')
             + '<span class="dim xs">⚙ 설정</span>')
    return f'<div class="bar"><span class="logo">DriveWatch</span>{chips}{right}</div>'


def car(body, active="김정숙", locked=False, demo=False):
    return f'<div class="car">{bar(active, locked, demo)}{body}</div>'


def phone(body):
    return f'<div class="phone"><div class="top">9:41 · 자녀 폰 · DriveWatch</div>{body}</div>'


def home_body(banner, state='<span class="state g-ok">안정 · 12주째 기록</span>', drive="모두 평소와 같아요"):
    places = "".join(
        f'<div class="raised f1 col center" style="gap:4px"><div style="font-size:34px">{i}</div><div class="h2">{n}</div></div>'
        for i, n in [("🏥", "병원"), ("⛪", "성당"), ("🛒", "마트")])
    cards = "".join(f'<div class="panel f1"><div class="dim xs">{t}</div><div style="font-size:15px;margin-top:4px">{b}</div></div>'
                    for t, b in [("이번 주 운전 ›", drive), ("약속", "해 지면 안 하기 · 이번 주 지킴"), ("가족", "김민수 님과 연결됨")])
    return f"""
    <div class="row" style="align-items:center"><div class="h1 f1">김정숙 님, 안녕하세요</div>{state}</div>
    {banner}
    <div class="row f1"><div class="panel col" style="flex:1.3"><div class="h2">오늘 어디 가세요?</div><div class="row f1">{places}</div></div>
    <div class="col f1">{cards}</div></div>"""


SCREENS = {}

SCREENS["C1"] = car("""
<div class="col center f1" style="gap:12px"><div class="h1">운전자 등록</div>
<div class="col" style="width:460px;text-align:left">
<div class="input"><span class="l">이름</span>김정숙</div>
<div class="input"><span class="l">휴대폰 번호</span>010-1234-5678 · <span style="color:#5FA8FF">인증번호 받기</span></div>
<div class="dim s">🔒 기록은 연결한 가족에게만 보여요. 위치는 보내지 않아요.</div>
<div class="row"><span class="btn">취소</span><span class="btn p f1">다음</span></div></div></div>""")

SCREENS["C2"] = car("""
<div class="h1">DriveWatch가 기록하는 것</div>
<div class="row f1">
 <div class="panel f1 col"><div class="ok h2">✓ 기록해요</div>
  <div>익숙한 길에서 길 안내를 다시 받은 횟수</div><div>회전할 때 깜빡이</div><div>급제동</div><div>밤 운전</div></div>
 <div class="panel f1 col"><div class="h2" style="color:#FF8A8A">✗ 기록하지 않아요</div>
  <div>위치와 지나간 경로</div><div>카메라 영상</div><div>음주 여부</div></div>
 <div class="panel f1 col"><div class="h2">가족에게 보낼 내용</div>
  <div class="raised" style="padding:10px;border:2px solid #5FA8FF"><b>상태만</b><div class="dim s">안정 · 관찰 · 대화 권장</div></div>
  <div class="raised" style="padding:10px"><b>상태와 근거</b><div class="dim s">"재탐색 0.2 → 0.9회" 같은 숫자 포함</div></div></div>
</div>
<div class="row" style="align-items:center"><span class="dim s f1">언제든 설정에서 끌 수 있어요</span><span class="btn">🔊 읽어 주기</span><span class="btn p">동의하고 시작</span></div>""")

promises = [("🌙", "해 지면 운전 안 하기", True), ("📍", "표시한 구간 피하기", True), ("🛣", "고속도로 줄이기", False),
            ("⏱", "1시간 넘게 연속 운전 안 하기", False), ("🌧", "비 오는 날은 가까운 곳만", False)]
SCREENS["C3"] = car(f"""
<div class="row" style="align-items:baseline"><div class="h1">지키고 싶은 약속을 골라 주세요</div><span class="dim">0개도 괜찮아요</span></div>
<div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px" class="f1">
{"".join(f'<div class="panel row" style="align-items:center;{"border:1px solid #5FA8FF" if on else ""}"><span style="font-size:24px">{i}</span><span class="f1" style="font-size:15px">{t}</span><span class="toggle{" on" if on else ""}"></span></div>' for i, t, on in promises)}
<div class="panel dim s col center">어겨도 막지 않아요.<br>기록하고 다음에 다시 물어봐요.</div></div>
<div class="row"><span class="sp"></span><span class="btn p">다음 · 가족 연결</span></div>""")

SCREENS["C4"] = car("""
<div class="col center f1" style="gap:16px"><div class="h1">자녀분 폰에서 이 코드를 입력하세요</div>
<div class="row" style="align-items:center;gap:24px"><div style="width:90px;height:90px;background:#fff;border-radius:10px;color:#0F1720;font-weight:700;display:flex;align-items:center;justify-content:center">QR</div>
<div style="font-size:44px;font-weight:700;letter-spacing:6px">482 913</div></div>
<div class="dim s">김정숙 님 프로필 · 10분 동안 유효 · 연결을 기다리는 중…</div><span class="btn">나중에 연결</span></div>""")

SCREENS["C5-day"] = car(home_body('<div class="panel s">☀️ 안녕하세요, 김정숙 님. 오늘도 천천히 다녀오세요.</div>'))

SCREENS["C5-night"] = car(home_body(
    '<div class="tts amber"><span style="font-size:24px">🔊</span><div class="f1"><div class="h2">오늘은 늦었네요. 꼭 가셔야 하면 천천히 가요.</div>'
    '<div class="warn s">21:04 · 약속 "해 지면 운전 안 하기"</div></div><span class="btn p">다른 방법으로 가기</span></div>'), demo=True)

SCREENS["C6"] = car("""
<div class="h1" style="text-align:center">병원까지 어떤 길로 갈까요?</div>
<div class="row f1" style="padding:0 60px">
 <div class="raised f1 col" style="padding:18px;gap:6px"><div class="h2">빠른 길 · 24분</div><div class="dim">표시한 <b style="color:#fff">중앙대로 합류</b>를 지나요</div>
  <div class="sp"></div><div class="dim s">지난번 이 합류에서 길 안내를 2번 다시 받았어요</div></div>
 <div class="f1 col" style="padding:18px;gap:6px;background:#1B3A5C;border:2px solid #5FA8FF;border-radius:12px">
  <div class="row" style="align-items:center"><div class="h2 f1">편한 길 · 28분</div><span class="tag">약속: 표시한 구간 피하기</span></div>
  <div>그 합류를 피해요 (+4분)</div><div class="sp"></div><div class="dim s">큰길 위주 · 좌회전 2번 적어요</div></div>
</div>
<div class="row"><span class="dim s f1">고르면 내비 경로가 바로 바뀌어요</span><span class="dim s">오늘은 다른 방법으로 가기 ›</span></div>""")

SCREENS["C7"] = car("""
<div class="col center f1" style="gap:14px"><div class="h1">병원까지 다른 방법으로 가요</div>
<div class="row" style="width:780px">
""" + "".join(f'<div class="raised f1 col center" style="padding:22px;gap:6px"><div style="font-size:34px">{i}</div><div class="h2">{t}</div><div class="dim s">{s}</div></div>'
              for i, t, s in [("🚕", "택시 부르기", "병원까지 예상 9,800원"), ("🚗", "대리운전", "내 차로 병원까지"), ("👨‍👩‍👦", "가족에게 부탁", "김민수 님에게 알림")]) + """
</div><div class="dim s">이유는 묻지 않고, 저장하지 않아요</div><span class="btn">돌아가기</span></div>""")

SCREENS["C8"] = car("""
<div class="panel f1" style="position:relative;padding:0;background:#16222E">
 <div class="dim" style="padding:16px">🗺 내비게이션 · 병원 (편한 길)</div>
 <div style="position:absolute;right:12px;top:12px;width:240px;background:#0F1720;border-radius:10px;padding:10px" class="s">
  <div class="dim xs">차량 신호 (D단) · 화면 조작 없음</div><div>좌회전 안내 · 깜빡이 안 켬 ✗</div><div>경로 재탐색 (1)</div><div>경로 재탐색 (2)</div></div>
 <div class="tts blue" style="position:absolute;left:14px;right:14px;bottom:14px"><span style="font-size:24px">🔊</span>
  <div><div class="h2">천천히 가셔도 괜찮아요. 길 다시 안내할게요.</div><div class="dim s">3초 뒤 사라짐 · 한 주행에 최대 2번</div></div></div>
</div>""", locked=True)

SCREENS["C9"] = car("""
<div class="h1">병원에 도착했어요 (P)</div>
<div class="tts blue" style="background:#18232F"><span style="font-size:24px">🔊</span><div class="h2">잘 다녀오셨어요. 오늘 밤 운전 약속도 지키셨어요.</div></div>
<div class="row f1">
 <div class="f1 col" style="background:#2B2110;border:1px solid #F5B85C;border-radius:12px;padding:14px">
  <div class="h2">중앙대로 합류 전후로 길 안내를 다시 받았어요.<br>표시해 둘까요?</div>
  <div class="warn s">재탐색 2번 · 14:12</div><div class="sp"></div>
  <div class="row"><span class="btn">괜찮아요</span><span class="btn p f1">📍 표시하기</span></div></div>
 <div class="panel f1 col" style="gap:8px"><div class="dim s">다른 불편했던 곳이 있었나요?</div>
  <div class="raised" style="padding:10px">시장 앞 좌회전</div><div class="raised" style="padding:10px">중앙대로 합류 <span class="warn s">· 차가 찾음</span></div>
  <div class="raised" style="padding:10px">병원 주차장 진입</div></div>
</div>""")

SCREENS["C10"] = car("""
<div class="row" style="align-items:center"><div class="h1 f1">이번 주 운전 카드</div><span class="state g-talk">대화 권장</span></div>
<div class="row f1">
 <div class="f1 col" style="background:#2B1A12;border:1px solid #FF8A5C;border-radius:12px;padding:14px">
  <div class="h2">요즘 익숙한 성당 가는 길에서 길 안내를 다시 받는 일이 늘었어요.</div>
  <div style="color:#FFB08F" class="s">익숙한 길 재탐색 · 평소 0.2 → 이번 주 0.9회/10km · 4주 연속</div><div class="sp"></div>
  <div>가족과 이야기해 보실래요?</div></div>
 <div class="col f1">
  <div class="panel"><div class="dim xs">잘하신 점</div><div>깜빡이 다 잘 켜셨어요 · 밤 운전 0회</div></div>
  <div class="panel"><div class="dim xs">약속</div><div>해 지면 안 하기 · 이번 주 지킴</div></div>
  <div class="panel"><div class="dim xs">피한 구간</div><div>📍 중앙대로 합류 2번 피했어요</div></div></div>
</div>
<div class="row" style="align-items:center"><span class="dim s f1">보낼 내용: 상태와 근거 (설정에서 바꿀 수 있어요)</span><span class="btn">이번 주는 안 보내기</span><span class="btn p">가족에게 보내기</span></div>""", demo=True)


def tile(label, value, base, heights, warn=0, color="#5FD38D"):
    bars = "".join(f'<i class="{"w" if k >= len(heights) - warn else ""}" style="height:{h}%"></i>' for k, h in enumerate(heights))
    return (f'<div class="panel f1 col" style="gap:4px"><div class="dim s">{label}</div><div style="font-size:22px;font-weight:700;color:{color}">{value}</div>'
            f'<div class="dim xs">평소 {base}</div><div class="sp"></div><div class="bars">{bars}</div><div class="dim xs">12주 전 → 이번 주</div></div>')


SCREENS["C11"] = car(f"""
<div class="row" style="align-items:center"><span class="h2">←</span><span class="dim f1">김정숙 님의 운전 · 이번 주 vs 평소(8주)</span><span class="state g-talk">대화 권장 · 16주째 기록</span></div>
<div class="row f1">
{tile("익숙한 길 재탐색", "0.9회/10km", "0.2회/10km", [20,30,15,25,20,30,15,25,70,80,85,95], warn=4, color="#F5B85C")}
{tile("깜빡이 이행률", "95%", "93%", [90,95,92,94,93,95,96,92,94,95,93,95])}
{tile("급제동", "1.4회/100km", "1.1회/100km", [30,20,35,25,30,20,25,35,30,25,30,35])}
<div class="panel f1 col"><div class="dim s">밤 운전</div><div style="font-size:22px;font-weight:700;color:#5FD38D">0회</div><div class="dim xs">약속: 해 지면 안 하기</div><div class="sp"></div><div class="dim s">📍 표시한 구간 1곳<br>중앙대로 합류 · 2번 피함</div></div>
</div>""")

SCREENS["P1"] = phone("""
<div class="h">가족으로 가입</div><div style="color:#777;font-size:13px">부모님의 운전 요약을 받아보세요</div>
<div class="pin"><span class="l">이름</span>김민수</div><div class="pin"><span class="l">휴대폰 번호</span>010-9876-5432</div>
<div class="pin"><span class="l">인증번호</span>482 1··</div><div style="flex:1"></div><div class="btn">다음</div>""")

SCREENS["P2"] = phone("""
<div class="h">부모님 차와 연결</div><div style="color:#777;font-size:13px">부모님 차 화면의 6자리 코드를 입력하세요</div>
<div style="display:grid;grid-template-columns:repeat(6,1fr);gap:6px">""" + "".join(f'<div class="pin" style="text-align:center;font-weight:700">{c}</div>' for c in "482913") + """</div>
<div style="color:#1F6FEB;font-size:15px">📷 QR로 연결</div><div style="flex:1"></div><div class="btn">연결하기</div>""")

SCREENS["P3"] = phone("""
<div style="flex:1"></div><div style="font-size:40px;text-align:center">🔗</div>
<div class="h" style="text-align:center">엄마(김정숙) 차와<br>연결됐어요</div>
<div style="color:#777;font-size:14px;text-align:center;line-height:1.6">매주 금요일에 운전 요약이 와요.<br>처음 2주는 평소 운전을 배우는 기간이에요.<br>엄마가 보내기로 한 것만 보여요.</div>
<div style="flex:1"></div><div class="btn">시작하기</div>""")

SCREENS["P4"] = phone("""
<div class="h">가족 운전</div>
<div class="card blue">🔔 엄마의 이번 주 운전 요약이 도착했어요</div>
<div class="card"><b>엄마 · 김정숙</b><span style="color:#C2410C;font-weight:700">● 대화 권장</span> · 요약 도착</div>
<div class="card"><b>아빠 · 박영호</b><span style="color:#15803D;font-weight:700">● 안정</span> · 확인함 👍</div>
<div style="flex:1"></div><div style="color:#1F6FEB">+ 부모님 차 추가</div>""")


def summary(pill, pill_bg, pill_fg, cards, cta=True):
    return phone(f"""
<div class="h">← 엄마 운전 · 이번 주</div>
<span class="pill" style="background:{pill_bg};color:{pill_fg}">{pill}</span>
{cards}<div style="flex:1"></div>
<div class="row" style="gap:8px"><div class="btn ghost" style="width:auto;white-space:nowrap">📞 전화하기</div><div class="btn f1">👍 확인했어요</div></div>""")


SCREENS["P5-stable"] = summary("● 안정 · 평소와 같아요", "#DCFCE7", "#15803D", """
<div class="card"><b>한 줄 요약</b>이번 주도 평소처럼 운전하셨어요.</div>
<div class="card"><b>잘하신 점</b>깜빡이 다 잘 켜셨어요 · 밤 운전 0회 (약속 지킴)</div>
<div class="card"><b>피한 구간</b>중앙대로 합류 2번 피하셨어요</div>""")

SCREENS["P5-watch"] = summary("● 관찰 중인 변화 1개", "#FEF3C7", "#92400E", """
<div class="card"><b>달라진 점</b>익숙한 길에서 길 안내를 다시 받는 일이 조금 늘었어요.
<div style="color:#999;font-size:12px;margin-top:4px">자세한 숫자는 엄마가 보내기로 한 경우에만 보여요</div></div>
<div class="card"><b>잘하신 점</b>밤 운전 0회 (약속 지킴)</div>
<div class="card blue"><b>지금은</b>따로 하실 일은 없어요. 다음 주 요약을 같이 봐요.</div>""")

SCREENS["P5-talk"] = summary("● 대화 권장", "#FFEDD5", "#C2410C", """
<div class="card"><b>근거</b>익숙한 성당 길 재탐색<br><span style="font-size:16px;font-weight:700">10km당 0.2회 → 0.9회</span> · 4주 연속</div>
<div class="card blue"><b>💬 이렇게 물어보세요</b>"엄마, 요즘 성당 가는 길 헷갈린 적 있어?"
<div style="color:#999;font-size:12px;margin-top:4px">"나이", "위험해서", "그만"은 피해 주세요</div></div>
<div class="card"><b>함께 할 일</b>치매안심센터 선별검사 같이 가기 · 한 번 동승하기 · 약속 같이 정하기</div>""")


def render(name, html):
    w, h = PHONE if name.startswith("P") else CAR
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False, encoding="utf-8") as f:
        f.write(f'<!doctype html><meta charset="utf-8"><style>{CSS}</style><body>{html}</body>')
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=2",
                    f"--window-size={w},{h}", f"--screenshot={OUT / (name + '.png')}", f"file://{f.name}"],
                   check=True, capture_output=True)


if __name__ == "__main__":
    for name, html in SCREENS.items():
        render(name, html)
        print("rendered", name)
