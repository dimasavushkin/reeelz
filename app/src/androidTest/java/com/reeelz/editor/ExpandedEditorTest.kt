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
    @Test fun multipleClipsKeepIndependentParametersAndOrder() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "multi-clip-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = root }
        try {
            val firstFile = File(root, "first.mp4").also { MediaFixtures.video(it, 2) }
            val secondFile = File(root, "second.mp4").also { MediaFixtures.video(it, 3) }
            val first = VideoClip("first", VideoSource(Uri.fromFile(firstFile), 2000, 320, 240), 100, 1600,
                CropParameters(1.4f, .2f, 0f, 90))
            val second = VideoClip("second", VideoSource(Uri.fromFile(secondFile), 3000, 320, 240), 500, 2800,
                CropParameters(2f, -.3f, .4f, 180))
            val projects = ProjectStore(context)
            val created = projects.create(EditorUiState(source = first.source, startMs = first.startMs, endMs = first.endMs,
                crop = first.crop, clips = listOf(first, second)))
            assertEquals(listOf("first", "second"), created.clips.map { it.id })
            assertEquals(first.copy(source = created.clips[0].source), created.clips[0])
            assertEquals(second.copy(source = created.clips[1].source), created.clips[1])
            assertNotEquals(firstFile, File(requireNotNull(created.clips[0].source.uri.path)))
            val reordered = created.copy(clips = created.clips.reversed(), selectedClipIndex = 0).withSelectedClip(0)
            val reopened = projects.saveAndLoad(reordered)
            assertEquals(listOf("second", "first"), reopened.clips.map { it.id })
            assertEquals(500L, reopened.startMs)
            assertEquals(180, reopened.crop.rotation)
            val one = projects.saveAndLoad(reopened.copy(clips = reopened.clips.take(1)).withSelectedClip(0))
            assertEquals(1, one.clips.size)
            val projectFolder = File(root, "projects/${one.projectId}")
            assertEquals(2, projectFolder.listFiles()!!.count { it.extension == "video" })
            projects.prune(one)
            assertEquals(1, projectFolder.listFiles()!!.count { it.extension == "video" })
            assertTrue(firstFile.exists())
            assertTrue(secondFile.exists())
        } finally { root.deleteRecursively() }
    }

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

    @Test fun realMultiClipExportIncludesLoopedMusicAndGlobalTimedText() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "media-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getCacheDir() = root
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("test-${root.name}-$name", mode)
        }
        var output: Uri? = null
        try {
            val firstVideo = File(root, "source-first.mp4").also { MediaFixtures.video(it, 2) }
            val secondVideo = File(root, "source-second.mp4").also { MediaFixtures.video(it, 2) }
            val music = File(root, "tone.wav").also(MediaFixtures::music)
            val first = VideoClip("first", VideoSource(Uri.fromFile(firstVideo), 2000, 320, 240), 500, 1500,
                CropParameters(rotation = 90))
            val second = VideoClip("second", VideoSource(Uri.fromFile(secondVideo), 2000, 320, 240), 500, 1500,
                CropParameters(zoom = 1.2f, x = .2f, rotation = 180))
            val state = EditorUiState(source = first.source, startMs = first.startMs, endMs = first.endMs,
                crop = first.crop, clips = listOf(first, second),
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
