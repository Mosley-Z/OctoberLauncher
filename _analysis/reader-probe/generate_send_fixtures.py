"""生成 RD002 外部来源用例所需的合成样本。

只使用标准库，重复执行会覆盖同名样本。missing.txt 必须保持缺失：若工作目录里
残留该文件会被删除。pdfbin 优先复制既有 normal.pdf，缺失时内嵌一份最小 PDF。
输出清单写入 send-fixture-manifest.json，字段 deploy 供设备脚本核对部署集合。
"""
from __future__ import annotations

import hashlib
import io
import json
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent
OUT = ROOT / "fixtures"
MANIFEST = ROOT / "send-fixture-manifest.json"
DEPLOY = ("plain", "epubbin", "docxzip", "pdfbin", "multi-a.txt", "multi-b.txt")
PLAIN_MIME = "text/plain"


def sha256(data: bytes) -> str:
    """返回字节内容的十六进制摘要，供部署后核对与报告引用。"""
    return hashlib.sha256(data).hexdigest()


def plain_text() -> bytes:
    """构造无扩展名的 UTF-8 长文本，用于观察阅读进度百分比。"""
    paragraph = "十雨阅读器合成外部来源样本：中文、标点与段落用于校验分页进度。0123456789\n"
    blocks = [f"第{index + 1}段\n{paragraph}{paragraph}" for index in range(400)]
    return "".join(blocks).encode("utf-8")


def epub(chapters: list[tuple[str, str]]) -> bytes:
    """构造合法 EPUB3 字节：mimetype 为首个存储条目，其余资源使用压缩。"""
    manifest_items = "".join(
        f'<item id="c{index}" href="chapter{index}.xhtml" media-type="application/xhtml+xml"/>'
        for index in range(1, len(chapters) + 1)
    )
    spine = "".join(f'<itemref idref="c{index}"/>' for index in range(1, len(chapters) + 1))
    opf = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">\n'
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">\n'
        '<dc:identifier id="bookid">urn:uuid:rd002-epubbin</dc:identifier>\n'
        '<dc:title>epubbin 合成样本</dc:title><dc:language>zh-CN</dc:language>\n'
        "</metadata>\n"
        f'<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>{manifest_items}</manifest>\n'
        f'<spine>{spine}</spine>\n'
        "</package>\n"
    )
    nav = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN">\n'
        "<head><title>目录</title></head><body><nav epub:type=\"toc\" id=\"toc\"><ol>"
        + "".join(f'<li><a href="chapter{index}.xhtml">第{index}章</a></li>' for index in range(1, len(chapters) + 1))
        + "</ol></nav></body></html>\n"
    )
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        archive.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        archive.writestr(
            "META-INF/container.xml",
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">\n'
            '<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>\n'
            "</container>\n",
            compress_type=zipfile.ZIP_DEFLATED,
        )
        archive.writestr("OEBPS/content.opf", opf, compress_type=zipfile.ZIP_DEFLATED)
        archive.writestr("OEBPS/nav.xhtml", nav, compress_type=zipfile.ZIP_DEFLATED)
        for index, body in enumerate(chapters, 1):
            archive.writestr(
                f"OEBPS/chapter{index}.xhtml",
                '<?xml version="1.0" encoding="UTF-8"?>\n'
                '<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN">\n'
                f"<head><title>第{index}章</title></head><body><h1>第{index}章</h1>"
                + "".join(f"<p>{line}</p>" for line in body.splitlines())
                + "</body></html>\n",
                compress_type=zipfile.ZIP_DEFLATED,
            )
    return stream.getvalue()


def docx() -> bytes:
    """构造最小 OOXML 文档，外层容器为 ZIP，用于校验“不支持类型”的观察。"""
    content_types = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\n'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\n'
        '<Default Extension="xml" ContentType="application/xml"/>\n'
        '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>\n'
        "</Types>\n"
    )
    relationships = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\n'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>\n'
        "</Relationships>\n"
    )
    document = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>\n'
        "<w:p><w:r><w:t>docxzip 合成 DOCX 正文：阅读器应将其识别为不支持的类型。</w:t></w:r></w:p>\n"
        "<w:sectPr/></w:body></w:document>\n"
    )
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", relationships)
        archive.writestr("word/document.xml", document)
    return stream.getvalue()


def minimal_pdf() -> bytes:
    """生成单页最小 PDF；仅在 normal.pdf 缺失时作为替代。"""
    stream = b"BT /F1 24 Tf 40 560 Td (Synthetic PDF fixture page 1) Tj ET"
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 480 640] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 6\n0000000000 65535 f \n"
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode()
    out += b"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + str(xref).encode() + b"\n%%EOF\n"
    return bytes(out)


def pdf_bytes() -> tuple[bytes, str]:
    """优先复用既有 normal.pdf，缺失时退回内嵌最小 PDF 并注明来源。"""
    existing = OUT / "normal.pdf"
    if existing.is_file() and existing.stat().st_size > 0:
        return existing.read_bytes(), "copied fixtures/normal.pdf"
    return minimal_pdf(), "embedded minimal PDF"


def write(name: str, data: bytes, mime: str, purpose: str, source: str) -> dict:
    """写入单个样本并返回清单记录，包含尺寸与摘要。"""
    path = OUT / name
    path.write_bytes(data)
    return {
        "name": name,
        "mime": mime,
        "bytes": len(data),
        "sha256": sha256(data),
        "purpose": purpose,
        "source": source,
    }


def validate() -> list[str]:
    """复核 EPUB 与 DOCX 容器结构，返回问题列表；空列表表示通过。"""
    problems = []
    with zipfile.ZipFile(OUT / "epubbin") as archive:
        first = archive.infolist()[0]
        if first.filename != "mimetype" or first.compress_type != zipfile.ZIP_STORED:
            problems.append("epubbin 的首个条目必须是未压缩的 mimetype")
        if archive.read("mimetype") != b"application/epub+zip":
            problems.append("epubbin 的 mimetype 内容不正确")
        if "OEBPS/content.opf" not in archive.namelist():
            problems.append("epubbin 缺少 OEBPS/content.opf")
    with zipfile.ZipFile(OUT / "docxzip") as archive:
        if "word/document.xml" not in archive.namelist():
            problems.append("docxzip 缺少 word/document.xml")
    return problems


def main() -> int:
    """生成全部部署样本与清单，删除遗留的 missing.txt 并核对目录状态。"""
    OUT.mkdir(parents=True, exist_ok=True)
    stale = OUT / "missing.txt"
    if stale.exists():
        stale.unlink()
        print("removed stale missing.txt")

    pdf, pdf_source = pdf_bytes()
    records = [
        write("plain", plain_text(), PLAIN_MIME, "SEND 文本成功路径：观察阅读页标题与进度百分比", "generated UTF-8 text"),
        write(
            "epubbin",
            epub([
                "合成 EPUB 第一章。用于验证无扩展名样本经 getType 声明为 application/epub+zip 后可以进入阅读页。",
                "合成 EPUB 第二章。正文保持 UTF-8 且不含混淆资源，覆盖重排版路径。",
            ]),
            "application/epub+zip",
            "VIEW 无扩展名 EPUB：观察阅读页标题与章节翻页",
            "generated EPUB3",
        ),
        write("docxzip", docx(), "application/zip", "SEND ZIP 容器（DOCX 内容）：观察不支持类型提示", "generated minimal DOCX"),
        write("pdfbin", pdf, "application/pdf", "无扩展名 PDF 对照样本，验证 MIME 回退路径", pdf_source),
        write(
            "multi-a.txt",
            "多文件发送样本 A。\n".encode("utf-8"),
            PLAIN_MIME,
            "SEND_MULTIPLE 首个 URI：观察是否提示一次只能打开一个文件",
            "generated UTF-8 text",
        ),
        write(
            "multi-b.txt",
            "多文件发送样本 B。\n".encode("utf-8"),
            PLAIN_MIME,
            "SEND_MULTIPLE 第二个 URI：与 multi-a.txt 组成多文件集合",
            "generated UTF-8 text",
        ),
    ]
    manifest = {
        "generator": "generate_send_fixtures.py",
        "deploy": list(DEPLOY),
        "excluded": [
            {"name": "missing.txt", "reason": "缺失用例依赖实体文件不存在，部署前必须保持未生成"},
        ],
        "fixtures": records,
    }
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    missing = [name for name in DEPLOY if not (OUT / name).is_file()]
    if missing:
        print("missing after generation:", ", ".join(missing))
        return 1
    if stale.exists():
        print("missing.txt still present")
        return 1
    problems = validate()
    if problems:
        print("样本结构校验失败:", "; ".join(problems))
        return 1
    for record in records:
        print(f"{record['name']:12} {record['bytes']:8} B  {record['mime']:24} {record['sha256'][:12]}")
    print("manifest:", MANIFEST)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except OSError as error:
        print(f"写入样本失败: {error}")
        raise SystemExit(1)
