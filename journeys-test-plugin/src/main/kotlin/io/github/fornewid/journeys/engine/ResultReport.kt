package io.github.fornewid.journeys.engine

import java.io.BufferedWriter
import java.io.File
import java.util.Base64

/**
 * Renders journey verdicts into one self-contained HTML report, inlining any screenshots
 * the agent captured (as data URIs) so the single file can be opened or shared as-is.
 *
 * Everything is streamed to the file, and screenshots are encoded straight into the writer, so a
 * run with many large screenshots does not have to fit the whole document in the daemon's heap.
 */
internal object ResultReport {
    fun write(
        verdicts: List<Verdict>,
        outFile: File,
        workingDir: File,
    ) {
        outFile.parentFile?.mkdirs()
        outFile.bufferedWriter().use { out ->
            out.write(HEAD)
            verdicts.forEach { verdict ->
                val ok = verdict.allPassed
                out.write(
                    """<section class="journey"><div class="jh">""" +
                        """<span class="badge ${if (ok) "pass" else "fail"}">${if (ok) "PASSED" else "FAILED"}</span>""" +
                        """<h2>${esc(verdict.journey)}</h2>""" +
                        """<span class="count">${verdict.passedCount}/${verdict.results.size}</span></div>""",
                )
                verdict.results.forEach { out.writeAction(it, workingDir) }
                out.write("</section>")
            }
            out.write(TAIL)
        }
    }

    private fun BufferedWriter.writeAction(
        result: ActionResult,
        workingDir: File,
    ) {
        val status =
            when (result.status) {
                ActionStatus.PASSED -> "pass"
                ActionStatus.FAILED -> "fail"
                else -> "skip"
            }
        write("""<div class="act $status"><div class="ah"><span class="pill $status">${esc(result.status.name)}</span>""")
        write("""<span>${esc(result.action)}</span></div>""")
        writeParagraph("reason", result.reasoning)
        writeParagraph("comment", result.comment)
        if (result.commands.isNotEmpty()) {
            write("""<div class="cmds">${result.commands.joinToString("<br>") { esc(it) }}</div>""")
        }
        val screenshots = result.artifacts.mapNotNull { screenshot(it, workingDir) }
        if (screenshots.isNotEmpty()) {
            write("""<div class="shots">""")
            screenshots.forEach { writeImage(it) }
            write("</div>")
        }
        write("</div>")
    }

    private fun BufferedWriter.writeParagraph(
        cssClass: String,
        text: String?,
    ) {
        if (!text.isNullOrBlank()) write("""<p class="$cssClass">${esc(text)}</p>""")
    }

    /** Streams the image straight into the document instead of building a data URI in memory. */
    private fun BufferedWriter.writeImage(image: File) {
        val encoder = Base64.getEncoder()
        write("""<img alt="" src="data:${image.mimeType()};base64,""")
        image.inputStream().buffered().use { input ->
            val buffer = ByteArray(3 * 8192) // a multiple of 3 keeps intermediate chunks padding-free
            while (true) {
                val read = input.readNBytes(buffer, 0, buffer.size)
                if (read <= 0) break
                write(encoder.encodeToString(if (read == buffer.size) buffer else buffer.copyOf(read)))
            }
        }
        write("""">""")
    }

    private fun screenshot(
        path: String,
        workingDir: File,
    ): File? = File(path).let { if (it.isAbsolute) it else workingDir.resolve(path) }.takeIf { it.isFile }

    private fun File.mimeType(): String =
        when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val HEAD =
        """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Journey results</title>
<style>
:root{--bg:#e7e9ed;--surf:#f5f6f8;--ink:#171b22;--muted:#5c6675;--line:#c7ccd4;
--pass:#3c8557;--fail:#ad463a;--skip:#8a93a1;--code:#eaecef;
--mono:ui-monospace,"SF Mono",Menlo,Consolas,monospace;--sans:system-ui,-apple-system,"Segoe UI",sans-serif}
@media(prefers-color-scheme:dark){:root{--bg:#0f131a;--surf:#161b23;--ink:#e7eaef;--muted:#929cab;
--line:#2a323d;--pass:#5fb07b;--fail:#d2685a;--skip:#6b7686;--code:#1b212b}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:var(--sans);line-height:1.55}
.wrap{max-width:920px;margin:0 auto;padding:32px 20px 64px}h1{font-family:var(--mono);font-size:20px}
.journey{background:var(--surf);border:1px solid var(--line);border-radius:14px;padding:20px;margin:0 0 22px}
.jh{display:flex;align-items:center;gap:12px;margin:0 0 14px}.jh h2{font-family:var(--mono);font-size:17px;margin:0;flex:1}
.count{font-family:var(--mono);font-size:12px;color:var(--muted)}
.badge,.pill{font-family:var(--mono);font-size:11px;font-weight:700;padding:3px 9px;border-radius:6px;color:#fff}
.badge.pass,.pill.pass{background:var(--pass)}.badge.fail,.pill.fail{background:var(--fail)}.pill.skip{background:var(--skip)}
.act{border-left:3px solid var(--skip);padding:10px 0 10px 14px;margin:0 0 12px}
.act.pass{border-color:var(--pass)}.act.fail{border-color:var(--fail)}
.ah{display:flex;align-items:baseline;gap:10px}.ah span:last-child{font-size:14.5px}
.reason{color:var(--muted);font-size:13px;margin:6px 0 0}.comment{color:var(--fail);font-size:13px;margin:6px 0 0}
.cmds{font-family:var(--mono);font-size:11.5px;background:var(--code);border-radius:8px;padding:8px 12px;margin:8px 0 0;color:var(--muted);overflow-x:auto}
.shots{display:flex;gap:12px;flex-wrap:wrap;margin:12px 0 0}.shots img{max-width:210px;width:100%;height:auto;border:1px solid var(--line);border-radius:10px}
</style></head><body><div class="wrap"><h1>Journey results</h1>"""

    private const val TAIL = "</div></body></html>"
}
