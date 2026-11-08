package com.tim.futureai.data.chat

import com.tim.futureai.core.security.SecureValueStore
import com.tim.futureai.domain.chat.SystemInstructionTemplate
import org.junit.Assert.*
import org.junit.Test

class SystemInstructionLibraryStoreTest {
    private class MemoryStore : SecureValueStore {
        val data = mutableMapOf<String, String>()
        override fun getString(key: String) = data[key]
        override fun putString(key: String, plainText: String) = true.also { data[key] = plainText }
        override fun contains(key: String) = key in data
        override fun remove(key: String) = data.remove(key) != null
    }
    @Test fun categoriesAndFavoritesSurviveStoreRecreationAndPortableTransfer() {
        val memory = MemoryStore()
        val templates = listOf(SystemInstructionTemplate("work", "代码审查", "检查边界条件\n说明理由", "开发", true))
        assertTrue(SystemInstructionLibraryStore(memory).save(templates))
        val restored = SystemInstructionLibraryStore(memory).load()
        assertEquals(templates, restored)
        assertEquals(templates, SystemInstructionLibraryStore.importPortable(SystemInstructionLibraryStore.exportPortable(restored)).getOrThrow())
    }
    @Test fun legacyStoredTemplatesGetDefaultCategoryWithoutLosingText() {
        val memory = MemoryStore()
        memory.data[SystemInstructionLibraryStore.KEY] = """[{"id":"old","title":"旧模板","instruction":"保留内容"}]"""
        assertEquals(SystemInstructionTemplate("old", "旧模板", "保留内容"), SystemInstructionLibraryStore(memory).load().single())
    }
    @Test fun invalidPortableDocumentIsRejected() {
        assertTrue(SystemInstructionLibraryStore.importPortable("{broken").isFailure)
        assertTrue(SystemInstructionLibraryStore.importPortable("""{"format":"unrelated","version":"2","templates":[]}""").isFailure)
    }
}
