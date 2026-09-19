package com.yudistirosaputro.dimock.core.engine

import java.io.File

/** Rules persisted as one JSON file, written atomically (temp file + rename) so a crash never leaves half a file. */
class FileRuleStorage(private val file: File) : RuleStorage {
    override fun read(): String? = if (file.isFile) runCatching { file.readText() }.getOrNull() else null

    override fun write(json: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }
}
