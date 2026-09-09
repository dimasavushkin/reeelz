package com.reeelz.editor

import android.content.Context
import android.content.ContextWrapper
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import com.reeelz.export.ExportEngine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

@UnstableApi
class ExpandedEditorTest {
    @Test fun recoveryDeletesOnlyPendingExportAndOwnTemporaryFile() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "recovery-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getCacheDir() = root
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("test-${root.name}-$name", mode)
        }
        val name = "Reeelz_test_${UUID.randomUUID()}.mp4"
        val recovery = com.reeelz.export.ExportRecovery(context)
        val collection = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        var uri: Uri? = null
        try {
            recovery.begin(name)
            uri = app.contentResolver.insert(collection, android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/Reeelz")
                put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
            })!!
            val temporary = File(root, "reeelz-interrupted.mp4").apply { writeText("partial") }
            val unrelated = File(root, "source.mp4").apply { writeText("original") }
            recovery.recover()
            assertFalse(temporary.exists())
            assertTrue(unrelated.exists())
            @Suppress("DEPRECATION")
            val pendingCollection = android.provider.MediaStore.setIncludePending(collection)
            app.contentResolver.query(pendingCollection, arrayOf(android.provider.MediaStore.Video.Media._ID),
                "${android.provider.MediaStore.Video.Media.DISPLAY_NAME}=?", arrayOf(name), null)!!.use {
                assertFalse(it.moveToFirst())
            }
            recovery.recover() // Repeating startup recovery is harmless.
        } finally {
            uri?.let { runCatching { app.contentResolver.delete(it, null, null) } }
            root.deleteRecursively()
        }
    }

    @Test fun projectsAreIndependentAndLegacyDraftRemainsReadable() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "projects-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = root }
        try {
            val file = File(root, "original.mp4")
            MediaFixtures.video(file, 2)
            val initial = EditorUiState(source = VideoSource(Uri.fromFile(file), 2000, 320, 240), endMs = 2000)
            DraftStore(context).save(initial)
            val projects = ProjectStore(context)
            val first = projects.create(initial.copy(projectName = "Первый"))
            val second = projects.create(initial.copy(projectName = "Второй"))
            assertEquals(3, projects.list().size)
            assertEquals(initial.endMs, projects.load("legacy").endMs)
            assertNotEquals(first.source!!.uri, second.source!!.uri)
            val music = File(root, "tone.wav").also(MediaFixtures::music)
            val audio = projects.importMusic(requireNotNull(first.projectId), Uri.fromFile(music))
            val edited = first.copy(startMs = 250, crop = CropParameters(1.5f, .2f, -.1f, 90),
                text = TextParameters("Первый", alignment = 0, background = true, startMs = 300, endMs = 1000),
                extraTexts = listOf(TextParameters("Второй", alignment = 2)), audio = audio)
            projects.save(edited)
            assertEquals(edited, ProjectStore(context).load(requireNotNull(first.projectId)))
            assertEquals(second, projects.load(requireNotNull(second.projectId)))
            projects.rename(requireNotNull(first.projectId), "Переименован")
            assertEquals("Переименован", projects.load(requireNotNull(first.projectId)).projectName)
            projects.delete(requireNotNull(second.projectId))
            assertEquals(2, projects.list().size)
            assertTrue(file.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun realExportIncludesLoopedMusicAndTimedTextAfterTrim() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "media-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getCacheDir() = root
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("test-${root.name}-$name", mode)
        }
        var output: Uri? = null
        try {
            val video = File(root, "source.mp4").also { MediaFixtures.video(it) }
            val music = File(root, "tone.wav").also(MediaFixtures::music)
            val state = EditorUiState(source = VideoSource(Uri.fromFile(video), 4000, 320, 240), startMs = 1000, endMs = 3000,
                crop = CropParameters(rotation = 90),
                text = TextParameters("TEST", 120f, 0xFFFFFF00.toInt(), 0f, 0f, startMs = 500, endMs = 1500),
                extraTexts = listOf(TextParameters("SECOND", 64f, 0xFFFF80AB.toInt(), y = .6f, startMs = 1500)),
                audio = AudioParameters(originalVolume = 0f, musicUri = Uri.fromFile(music).toString(), musicDurationMs = 500, musicVolume = .25f))
            output = withTimeout(90000) { ExportEngine(context).export(state) {} }
            MediaMetadataRetriever().use { reader ->
                reader.setDataSource(app, output)
                val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("duration=$duration", duration in 1850..2150)
                assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                fun yellow(time: Long): Int {
                    val bitmap = reader.getScaledFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST, 270, 480)!!
                    try {
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        return pixels.count { android.graphics.Color.red(it) > 150 && android.graphics.Color.green(it) > 150 && android.graphics.Color.blue(it) < 120 }
                    } finally { bitmap.recycle() }
                }
                assertEquals("Text should be hidden before 0.5s", 0, yellow(100_000))
                assertTrue("Text should appear at 0.9s", yellow(900_000) > 10)
                assertEquals("Text should be hidden after 1.5s", 0, yellow(1_800_000))
            }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(app, requireNotNull(output), null)
                val audioTrack = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString("mime")!!.startsWith("audio/") }
                extractor.selectTrack(audioTrack)
                var last = 0L
                while (extractor.sampleTime >= 0) { last = extractor.sampleTime; extractor.advance() }
                assertTrue("Music must repeat beyond the original 0.5s", last > 1_800_000)
            } finally { extractor.release() }
        } finally {
            output?.let { app.contentResolver.delete(it, null, null) }
            root.deleteRecursively()
        }
    }

    @Test fun cancellationCleansTemporaryFileOnLongSource() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "cancel-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir() = root }
        var output: Uri? = null
        try {
            val input = File(root, "long.mp4").also { MediaFixtures.video(it, seconds = 120, fps = 2) }
            val state = EditorUiState(source = VideoSource(Uri.fromFile(input), 120000, 320, 240), endMs = 120000)
            val job = launch { output = ExportEngine(context).export(state) {} }
            delay(200)
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertFalse(root.listFiles()!!.any { it.name.startsWith("reeelz-") })
            assertTrue(input.exists())
        } finally {
            output?.let { app.contentResolver.delete(it, null, null) }
            root.deleteRecursively()
        }
    }
}
