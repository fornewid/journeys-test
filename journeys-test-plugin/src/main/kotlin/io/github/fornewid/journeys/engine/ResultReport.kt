package io.github.fornewid.journeys.engine

import java.io.File
import java.util.Base64

/**
 * Renders journey verdicts into one self-contained HTML report, inlining any screenshots
 * the agent captured (as data URIs) so the single file can be opened or shared as-is.
 */
internal object ResultReport {

    fun write(verdicts: List<Verdict>, outFile: File) {
        outFile.writeText(page(verdicts.joinToString("\n", transform = ::journey)))
    }

    private fun journey(v: Verdict): String {
        val passed = v.results.count { it.status == "PASSED" }
        val ok = v.results.isNotEmpty() && passed == v.results.size
        return """
            |<section class="journey">
            |  <div class="jh"><span class="badge ${cls(ok)}">${if (ok) "PASSED" else "FAILED"}</span>
            |    <h2>${esc(v.journey)}</h2><span class="count">$passed/${v.results.size}</span></div>
            |  ${v.results.joinToString("\n  ", transform = ::action)}
            |</section>
        """.trimMargin()
    }

    private fun action(r: ActionResult): String {
        val c = when (r.status) { "PASSED" -> "pass"; "FAILED" -> "fail"; else -> "skip" }
        val reason = r.reasoning?.ifBlank { null }?.let { "<p class=\"reason\">${esc(it)}</p>" }.orEmpty()
        val comment = r.comment?.ifBlank { null }?.let { "<p class=\"comment\">${esc(it)}</p>" }.orEmpty()
        val cmds = r.commands.ifEmpty { null }
            ?.joinToString("<br>") { esc(it) }?.let { "<div class=\"cmds\">$it</div>" }.orEmpty()
        val shots = r.artifacts.mapNotNull(::dataUri).joinToString("") { "<img src=\"$it\" alt=\"\">" }
            .ifEmpty { null }?.let { "<div class=\"shots\">$it</div>" }.orEmpty()
        return """<div class="act $c"><div class="ah"><span class="pill $c">${esc(r.status)}</span>""" +
            """<span>${esc(r.action)}</span></div>$reason$comment$cmds$shots</div>"""
    }

    private fun dataUri(path: String): String? {
        val f = File(path).let { if (it.isAbsolute) it else File(System.getProperty("user.dir"), path) }
        if (!f.isFile) return null
        val mime = when (f.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; else -> "image/png"
        }
        return "data:$mime;base64,${Base64.getEncoder().encodeToString(f.readBytes())}"
    }

    private fun cls(ok: Boolean) = if (ok) "pass" else "fail"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun page(body: String) = """
        |<!doctype html><html lang="ko"><head><meta charset="utf-8">
        |<meta name="viewport" content="width=device-width,initial-scale=1"><title>Journey results</title>
        |<style>
        |:root{--bg:#e7e9ed;--surf:#f5f6f8;--ink:#171b22;--muted:#5c6675;--line:#c7ccd4;
        |--pass:#3c8557;--fail:#ad463a;--skip:#8a93a1;--code:#eaecef;
        |--mono:ui-monospace,"SF Mono",Menlo,Consolas,monospace;--sans:system-ui,-apple-system,"Segoe UI",sans-serif}
        |@media(prefers-color-scheme:dark){:root{--bg:#0f131a;--surf:#161b23;--ink:#e7eaef;--muted:#929cab;
        |--line:#2a323d;--pass:#5fb07b;--fail:#d2685a;--skip:#6b7686;--code:#1b212b}}
        |*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:var(--sans);line-height:1.55}
        |.wrap{max-width:920px;margin:0 auto;padding:32px 20px 64px}h1{font-family:var(--mono);font-size:20px}
        |.journey{background:var(--surf);border:1px solid var(--line);border-radius:14px;padding:20px;margin:0 0 22px}
        |.jh{display:flex;align-items:center;gap:12px;margin:0 0 14px}.jh h2{font-family:var(--mono);font-size:17px;margin:0;flex:1}
        |.count{font-family:var(--mono);font-size:12px;color:var(--muted)}
        |.badge,.pill{font-family:var(--mono);font-size:11px;font-weight:700;padding:3px 9px;border-radius:6px;color:#fff}
        |.badge.pass,.pill.pass{background:var(--pass)}.badge.fail,.pill.fail{background:var(--fail)}.pill.skip{background:var(--skip)}
        |.act{border-left:3px solid var(--skip);padding:10px 0 10px 14px;margin:0 0 12px}
        |.act.pass{border-color:var(--pass)}.act.fail{border-color:var(--fail)}
        |.ah{display:flex;align-items:baseline;gap:10px}.ah span:last-child{font-size:14.5px}
        |.reason{color:var(--muted);font-size:13px;margin:6px 0 0}.comment{color:var(--fail);font-size:13px;margin:6px 0 0}
        |.cmds{font-family:var(--mono);font-size:11.5px;background:var(--code);border-radius:8px;padding:8px 12px;margin:8px 0 0;color:var(--muted);overflow-x:auto}
        |.shots{display:flex;gap:12px;flex-wrap:wrap;margin:12px 0 0}.shots img{max-width:210px;width:100%;height:auto;border:1px solid var(--line);border-radius:10px}
        |</style></head><body><div class="wrap"><h1>Journey results</h1>
        |$body
        |</div></body></html>
    """.trimMargin()
}
