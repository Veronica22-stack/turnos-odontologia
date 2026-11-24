"""Genera docs/discovery/informe-discovery.html a partir del .md.
Uso (desde la raiz del repo, en Windows):
  py -m pip install markdown
  py docs/discovery/generar-pdf.py
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe" --headless --no-pdf-header-footer --print-to-pdf="%CD%\\docs\\discovery\\informe-discovery.pdf" "%CD%\\docs\\discovery\\informe-discovery.html"
(o abrir el .html en el navegador, Ctrl+P, "Guardar como PDF", sin encabezados ni pies de pagina)
"""
import markdown,sys,os
if len(sys.argv)<3:
    here=os.path.dirname(os.path.abspath(__file__))
    sys.argv=[sys.argv[0],os.path.join(here,'informe-discovery.md'),os.path.join(here,'informe-discovery.html')]
src=open(sys.argv[1],encoding='utf-8').read()
body=markdown.markdown(src,extensions=['tables','md_in_html','sane_lists','fenced_code'])
css='''
@page{size:A4;margin:18mm 16mm 18mm 16mm}
body{font-family:"Open Sans","Noto Sans","Carlito","Liberation Sans",sans-serif;font-size:10pt;line-height:1.45;color:#222}
h1,h2,h3{color:#0b4f6c;line-height:1.25}
h2{font-size:15pt;border-bottom:1.5px solid #0b4f6c;padding-bottom:3px;margin-top:22px;break-after:avoid}
h3{font-size:12pt;margin-top:16px;break-after:avoid}
code{font-family:Consolas,"DejaVu Sans Mono",monospace;font-size:8.6pt;background:#f2f4f6;padding:0 3px;border-radius:3px}
table{border-collapse:collapse;width:100%;font-size:7.8pt;margin:8px 0;page-break-inside:auto}
tr{break-inside:avoid}
th{background:#0b4f6c;color:#fff;text-align:left;padding:4px}
td{border:1px solid #cfd6dc;padding:3px 4px;vertical-align:top;word-break:break-word}
tr:nth-child(even) td{background:#f6f8fa}
blockquote{border-left:3px solid #0b4f6c;margin:8px 0;padding:4px 10px;background:#eef5f8}
.cover{height:250mm;display:flex;flex-direction:column;justify-content:center;break-after:page}
.cover h1{font-size:28pt;border-bottom:2px solid #0b4f6c;padding-bottom:6px;margin-bottom:6px}
.cover .subtitle{font-size:13pt;color:#0b4f6c;margin-bottom:28px}
.cover p{margin:4px 0;font-size:10pt}
a{color:#0b4f6c}
'''
open(sys.argv[2],'w',encoding='utf-8').write(f'<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"><title>Informe de Discovery</title><style>{css}</style></head><body>{body}</body></html>')
