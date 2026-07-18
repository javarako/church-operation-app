from __future__ import annotations

import html
import re
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_RIGHT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    Image,
    KeepTogether,
    ListFlowable,
    ListItem,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


GUIDE_DIR = Path(__file__).resolve().parent
CONTENT_PATH = GUIDE_DIR / "user-guide-content-ko.md"
OUTPUT_PATH = GUIDE_DIR / "교회운영 메뉴얼.pdf"
SCREENSHOT_DIR = GUIDE_DIR / "screenshots"
RESOURCE_DIR = (
    GUIDE_DIR.parents[1]
    / "backend"
    / "src"
    / "main"
    / "resources"
    / "static"
    / "branding"
)
FONT_PATH = Path("/System/Library/Fonts/Supplemental/AppleGothic.ttf")
FONT_NAME = "AppleGothic"

NAVY = colors.HexColor("#102f43")
BLUE = colors.HexColor("#245d83")
TEAL = colors.HexColor("#0a7180")
MID_GRAY = colors.HexColor("#627184")
LIGHT_BLUE = colors.HexColor("#eaf2f7")


def register_fonts() -> None:
    pdfmetrics.registerFont(TTFont(FONT_NAME, str(FONT_PATH)))
    pdfmetrics.registerFontFamily(
        FONT_NAME,
        normal=FONT_NAME,
        bold=FONT_NAME,
        italic=FONT_NAME,
        boldItalic=FONT_NAME,
    )


def inline_markup(text: str) -> str:
    escaped = html.escape(text)
    escaped = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", escaped)
    escaped = re.sub(r"`(.+?)`", r'<font name="Courier">\1</font>', escaped)
    return escaped


def scaled_image(path: Path, max_width: float, max_height: float) -> Image:
    image = Image(str(path))
    scale = min(max_width / image.imageWidth, max_height / image.imageHeight)
    image.drawWidth = image.imageWidth * scale
    image.drawHeight = image.imageHeight * scale
    return image


def page_decoration(canvas, doc) -> None:
    canvas.saveState()
    canvas.setFont(FONT_NAME, 8)
    canvas.setFillColor(MID_GRAY)
    canvas.drawString(0.7 * inch, 10.55 * inch, "교회운영  |  사용자 안내서")
    canvas.drawRightString(
        7.8 * inch,
        0.42 * inch,
        f"Capstone Presbyterian Church   |   {doc.page}",
    )
    canvas.restoreState()


def styles():
    base = getSampleStyleSheet()
    return {
        "body": ParagraphStyle(
            "KoreanBody",
            parent=base["BodyText"],
            fontName=FONT_NAME,
            fontSize=9.4,
            leading=14.2,
            textColor=NAVY,
            spaceAfter=6,
        ),
        "h2": ParagraphStyle(
            "KoreanH2",
            parent=base["Heading1"],
            fontName=FONT_NAME,
            fontSize=22,
            leading=27,
            textColor=NAVY,
            spaceAfter=12,
        ),
        "h3": ParagraphStyle(
            "KoreanH3",
            parent=base["Heading2"],
            fontName=FONT_NAME,
            fontSize=14,
            leading=18,
            textColor=BLUE,
            spaceBefore=8,
            spaceAfter=7,
        ),
        "quote": ParagraphStyle(
            "KoreanQuote",
            parent=base["BodyText"],
            fontName=FONT_NAME,
            fontSize=9,
            leading=13.5,
            textColor=NAVY,
            backColor=LIGHT_BLUE,
            borderColor=TEAL,
            borderWidth=0.8,
            borderPadding=7,
            leftIndent=8,
            rightIndent=8,
            spaceBefore=5,
            spaceAfter=8,
        ),
        "caption": ParagraphStyle(
            "KoreanCaption",
            parent=base["BodyText"],
            fontName=FONT_NAME,
            fontSize=8,
            leading=10,
            alignment=TA_CENTER,
            textColor=MID_GRAY,
            spaceBefore=3,
            spaceAfter=8,
        ),
    }


def add_cover(story, style_map) -> None:
    logo = RESOURCE_DIR / "church_logo.png"
    banner = RESOURCE_DIR / "church-banner.png"
    if logo.exists():
        logo_image = scaled_image(logo, 2.2 * inch, 1.0 * inch)
        logo_image.hAlign = "CENTER"
        story.extend([logo_image, Spacer(1, 0.3 * inch)])
    story.append(
        Paragraph(
            "CAPSTONE PRESBYTERIAN CHURCH",
            ParagraphStyle(
                "CoverChurch",
                parent=style_map["body"],
                fontSize=11,
                leading=14,
                alignment=TA_CENTER,
                textColor=TEAL,
            ),
        )
    )
    story.append(
        Paragraph(
            "교회운영 메뉴얼",
            ParagraphStyle(
                "CoverTitle",
                parent=style_map["h2"],
                fontSize=30,
                leading=36,
                alignment=TA_CENTER,
                spaceBefore=8,
                spaceAfter=4,
            ),
        )
    )
    story.append(
        Paragraph(
            "사용자 안내서",
            ParagraphStyle(
                "CoverSubtitle",
                parent=style_map["h3"],
                fontSize=18,
                leading=22,
                alignment=TA_CENTER,
                spaceAfter=22,
            ),
        )
    )
    if banner.exists():
        banner_image = scaled_image(banner, 6.7 * inch, 2.7 * inch)
        banner_image.hAlign = "CENTER"
        story.extend([banner_image, Spacer(1, 0.35 * inch)])
    story.append(
        Paragraph(
            "Admin, Treasurer, Pastor, Membership, Viewer 및 Member 역할용",
            ParagraphStyle(
                "CoverAudience",
                parent=style_map["body"],
                alignment=TA_CENTER,
                textColor=MID_GRAY,
            ),
        )
    )
    story.append(
        Paragraph(
            "버전 1.0  |  2026년 7월",
            ParagraphStyle(
                "CoverVersion",
                parent=style_map["body"],
                alignment=TA_CENTER,
                textColor=MID_GRAY,
            ),
        )
    )
    story.append(PageBreak())


def add_contents(story, style_map) -> None:
    story.append(Paragraph("목차", style_map["h2"]))
    chapters = [
        "이 안내서 소개",
        "역할 및 접근 권한",
        "시작하기",
        "대시보드",
        "교인 정보",
        "헌금 관리",
        "재정 관리",
        "예산 관리",
        "기준정보",
        "보고서",
        "내 프로필",
        "로그아웃",
        "문제 해결 및 보안 안내",
    ]
    items = [
        ListItem(Paragraph(chapter, style_map["body"]), leftIndent=12)
        for chapter in chapters
    ]
    story.append(
        ListFlowable(
            items,
            bulletType="bullet",
            leftIndent=18,
            bulletFontName=FONT_NAME,
            bulletFontSize=7,
        )
    )
    story.append(PageBreak())


def add_table(story, rows, style_map) -> None:
    data = [
        [Paragraph(inline_markup(cell), style_map["body"]) for cell in row]
        for row in rows
    ]
    column_count = max(len(row) for row in data)
    table = Table(data, colWidths=[6.8 * inch / column_count] * column_count, repeatRows=1)
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), NAVY),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, -1), FONT_NAME),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#7a8793")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ]
        )
    )
    story.extend([table, Spacer(1, 8)])


def parse_content(story, style_map) -> None:
    lines = CONTENT_PATH.read_text(encoding="utf-8").splitlines()
    index = 2
    first_section = True
    while index < len(lines):
        line = lines[index].strip()
        if not line:
            index += 1
            continue
        if line.startswith("## "):
            if not first_section:
                story.append(PageBreak())
            first_section = False
            story.append(Paragraph(inline_markup(line[3:]), style_map["h2"]))
        elif line.startswith("### "):
            story.append(Paragraph(inline_markup(line[4:]), style_map["h3"]))
        elif line.startswith("|"):
            rows = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                cells = [cell.strip() for cell in lines[index].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-+:?", cell) for cell in cells):
                    rows.append(cells)
                index += 1
            add_table(story, rows, style_map)
            continue
        elif match := re.fullmatch(r"\[\[FIGURE:([^|]+)\|(.+)\]\]", line):
            image_path = SCREENSHOT_DIR / match.group(1)
            figure = scaled_image(image_path, 6.8 * inch, 5.7 * inch)
            figure.hAlign = "CENTER"
            story.append(
                KeepTogether(
                    [
                        Spacer(1, 5),
                        figure,
                        Paragraph(inline_markup(match.group(2)), style_map["caption"]),
                    ]
                )
            )
        elif line.startswith("> "):
            story.append(Paragraph(inline_markup(line[2:]), style_map["quote"]))
        elif re.match(r"\d+\.\s+", line):
            items = []
            while index < len(lines):
                item_match = re.match(r"\d+\.\s+(.+)", lines[index].strip())
                if not item_match:
                    break
                items.append(
                    ListItem(
                        Paragraph(inline_markup(item_match.group(1)), style_map["body"]),
                        leftIndent=14,
                    )
                )
                index += 1
            story.append(
                ListFlowable(
                    items,
                    bulletType="1",
                    start="1",
                    leftIndent=20,
                    bulletFontName=FONT_NAME,
                    bulletFontSize=9,
                    spaceAfter=5,
                )
            )
            continue
        elif line.startswith("- "):
            items = []
            while index < len(lines) and lines[index].strip().startswith("- "):
                items.append(
                    ListItem(
                        Paragraph(inline_markup(lines[index].strip()[2:]), style_map["body"]),
                        leftIndent=14,
                    )
                )
                index += 1
            story.append(
                ListFlowable(
                    items,
                    bulletType="bullet",
                    leftIndent=20,
                    bulletFontName=FONT_NAME,
                    bulletFontSize=7,
                    spaceAfter=5,
                )
            )
            continue
        else:
            paragraph_lines = [line]
            while index + 1 < len(lines):
                candidate = lines[index + 1].strip()
                if not candidate or re.match(
                    r"(#{2,3} |\||\[\[FIGURE:|> |\d+\.\s+|- )", candidate
                ):
                    break
                index += 1
                paragraph_lines.append(candidate)
            story.append(
                Paragraph(inline_markup(" ".join(paragraph_lines)), style_map["body"])
            )
        index += 1


def build() -> None:
    register_fonts()
    style_map = styles()
    story = []
    add_cover(story, style_map)
    add_contents(story, style_map)
    parse_content(story, style_map)
    document = SimpleDocTemplate(
        str(OUTPUT_PATH),
        pagesize=letter,
        rightMargin=0.7 * inch,
        leftMargin=0.7 * inch,
        topMargin=0.7 * inch,
        bottomMargin=0.65 * inch,
        title="교회운영 메뉴얼",
        author="",
    )
    document.build(story, onFirstPage=page_decoration, onLaterPages=page_decoration)


if __name__ == "__main__":
    build()
