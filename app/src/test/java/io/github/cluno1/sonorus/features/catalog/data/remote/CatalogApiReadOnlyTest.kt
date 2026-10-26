package io.github.cluno1.sonorus.features.catalog.data.remote

import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT

class CatalogApiReadOnlyTest {
    @Test
    fun secondPhaseApiDeclaresOnlyGetAndHead() {
        CatalogApi::class.java.declaredMethods.filterNot { it.name.endsWith("\$default") }.forEach { method ->
            val annotations = method.annotations.map { it.annotationClass.java }
            assertTrue("${method.name} must be GET or HEAD", annotations.any { it == GET::class.java || it == HEAD::class.java })
            assertTrue(
                "${method.name} must not declare a write verb",
                annotations.none { it == POST::class.java || it == PUT::class.java || it == PATCH::class.java || it == DELETE::class.java },
            )
        }
    }

    @Test
    fun lyricsWriteApiDeclaresOnlyTheNarrowPut() {
        val methods = CatalogLyricsWriteApi::class.java.declaredMethods
            .filterNot { it.name.endsWith("\$default") }
        assertTrue(methods.size == 1)
        assertTrue(methods.single().getAnnotation(PUT::class.java) != null)
    }

    @Test
    fun workTagsWriteApiDeclaresOnlyTheNarrowTagPatch() {
        val methods = CatalogWorkTagsWriteApi::class.java.declaredMethods
            .filterNot { it.name.endsWith("\$default") }
        assertTrue(methods.size == 1)
        val patch = methods.single().getAnnotation(PATCH::class.java)
        assertTrue(patch != null && patch.value == "v2/works/{id}/tags")
    }

    @Test
    fun roomsApiOnlyTouchesRoomPaths() {
        // issue 124: the rooms surface must not become a general write channel.
        val methods = CatalogRoomsApi::class.java.declaredMethods.filterNot { it.name.endsWith("\$default") }
        assertTrue(methods.size == 7)
        methods.forEach { method ->
            val path = method.getAnnotation(GET::class.java)?.value
                ?: method.getAnnotation(POST::class.java)?.value
                ?: method.getAnnotation(PUT::class.java)?.value
                ?: method.getAnnotation(DELETE::class.java)?.value
            assertTrue("${method.name} must declare a verb", path != null)
            assertTrue("${method.name} path $path must stay under v2/rooms", path!!.startsWith("v2/rooms"))
            assertTrue("${method.name} must not PATCH", method.getAnnotation(PATCH::class.java) == null)
        }
    }

    @Test
    fun workLyricsWriteApiDeclaresOnlyTheNarrowLyricsPut() {
        // issue 125: plain lyrics of one language; general Work writes stay out of the APK.
        val methods = CatalogWorkLyricsWriteApi::class.java.declaredMethods
            .filterNot { it.name.endsWith("\$default") }
        assertTrue(methods.size == 1)
        val put = methods.single().getAnnotation(PUT::class.java)
        assertTrue(put != null && put.value == "v2/works/{id}/lyrics/{language}")
    }
}
