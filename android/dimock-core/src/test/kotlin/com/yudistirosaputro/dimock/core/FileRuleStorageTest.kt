package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.engine.FileRuleStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileRuleStorageTest {
    @Test
    fun `missing file reads null, write creates parents and is readable, no temp file left`() {
        val dir = Files.createTempDirectory("dimock").toFile()
        val file = File(dir, "nested/rules.json")
        val storage = FileRuleStorage(file)
        assertNull(storage.read())
        storage.write("[]")
        assertEquals("[]", storage.read())
        storage.write("""[{"id":"a"}]""")
        assertEquals("""[{"id":"a"}]""", FileRuleStorage(file).read())
        assertFalse(File(dir, "nested/rules.json.tmp").exists())
    }
}
