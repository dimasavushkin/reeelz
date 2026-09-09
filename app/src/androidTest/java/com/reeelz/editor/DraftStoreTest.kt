package com.reeelz.editor

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DraftStoreTest {
    @Test fun recoveryAcrossProcessStop() = runBlocking<Unit> {
        val phase = InstrumentationRegistry.getArguments().getString("draftPhase")
        org.junit.Assume.assumeTrue(phase == "write" || phase == "read")
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.filesDir, "process-recovery-test").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir(): File = root }
        val store = DraftStore(context)
        if (phase == "write") {
            val input = File(root, "fixture.mov").apply { writeBytes(ByteArray(128) { 42 }) }
            val source = store.save(EditorUiState(source = VideoSource(Uri.fromFile(input), 5000, 720, 1280), endMs = 5000))
            store.save(EditorUiState(source = source, startMs = 500, endMs = 4000,
                crop = CropParameters(2f, .2f, -.4f), text = TextParameters("После перезапуска", 96f)))
        } else {
            try {
                val restored = store.load()
                assertEquals(500L, restored.startMs)
                assertEquals(4000L, restored.endMs)
                assertEquals(CropParameters(2f, .2f, -.4f), restored.crop)
                assertEquals(TextParameters("После перезапуска", 96f), restored.text)
                assertEquals(1, File(root, "draft").listFiles()!!.count { it.extension == "video" })
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun survivesRecreationAndFailedReplacementWithoutTouchingSource() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "draft-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir(): File = root
        }
        try {
            val original = File(root, "original.mov").apply { writeBytes(ByteArray(4096) { (it % 255).toByte() }) }
            val expected = EditorUiState(
                source = VideoSource(Uri.fromFile(original), 16000, 720, 1280),
                startMs = 1234, endMs = 12000,
                crop = CropParameters(2.2f, -0.4f, 0.7f),
                text = TextParameters("Привет\nМир = 🎬", 80f, 0xFFFFEB3B.toInt(), 0.3f, -0.2f),
            )
            DraftStore(context).save(expected)
            val reopened = DraftStore(context)
            assertTrue(reopened.exists())
            val restored = reopened.load()
            val restoredSource = requireNotNull(restored.source)
            val expectedSource = requireNotNull(expected.source)
            assertEquals(expected.copy(source = restored.source), restored)
            assertEquals(16000L, restoredSource.durationMs)
            assertEquals(720, restoredSource.width)
            assertEquals(1280, restoredSource.height)
            assertNotEquals(expectedSource.uri, restoredSource.uri)
            val savedFile = File(requireNotNull(restoredSource.uri.path))
            assertArrayEquals(original.readBytes(), savedFile.readBytes())
            val changed = restored.copy(startMs = 2500, text = restored.text.copy(content = "Автосохранено", size = 110f))
            repeat(5) { reopened.save(changed) }
            assertEquals(changed, DraftStore(context).load())
            assertEquals(1, File(root, "draft").listFiles()!!.count { it.extension == "video" })
            assertArrayEquals(original.readBytes(), savedFile.readBytes())
            try {
                reopened.save(expected.copy(source = expectedSource.copy(uri = Uri.fromFile(File(root, "missing.mov")))))
                fail("Missing input must not replace a committed draft")
            } catch (_: java.io.FileNotFoundException) { }
            assertEquals(changed, DraftStore(context).load())
            original.delete()
            assertTrue(savedFile.isFile)
            assertEquals(changed, DraftStore(context).load())
            reopened.delete()
            assertFalse(reopened.exists())
            assertFalse(savedFile.exists())
        } finally { root.deleteRecursively() }
    }
}


