/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.local.data.model

import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedLibraryPolicyTest {
    private data class Item(val id: String, val title: String)
    private val device = Item("42", "Same recording")
    private val catalog = Item("rhythm-catalog:rendition:42", "Same recording")
    private val lan = Item(LanSubsonicPlaybackPolicy.mediaId("42"), "Same recording")

    @Test
    fun `connection adds a third source without replacing existing recordings`() {
        val before = library(emptyList())
        val connected = library(listOf(lan))
        val disconnected = library(emptyList())

        assertEquals(listOf(device, catalog), before)
        assertEquals(listOf(device, catalog, lan), connected)
        assertEquals(before, disconnected)
    }

    @Test
    fun `unavailable catalog does not hide device or LAN songs`() {
        assertEquals(
            listOf(device, lan),
            UnifiedLibraryPolicy.mergeById(listOf(device), emptyList(), listOf(lan), id = Item::id),
        )
    }

    @Test
    fun `duplicates within a provider collapse by identity rather than title`() {
        assertEquals(listOf(device, catalog, lan), library(listOf(lan, lan.copy(title = "Stale copy"))))
    }

    @Test
    fun `remote rows cannot receive device file actions`() {
        assertTrue(UnifiedLibraryPolicy.isDevice(device.id))
        assertFalse(UnifiedLibraryPolicy.isDevice(catalog.id))
        assertFalse(UnifiedLibraryPolicy.isDevice(lan.id))
        assertTrue(UnifiedLibraryPolicy.isLan(lan.id))
        assertFalse(UnifiedLibraryPolicy.isLan(catalog.id))
    }

    private fun library(lanItems: List<Item>) = UnifiedLibraryPolicy.mergeById(
        listOf(device), listOf(catalog), lanItems, id = Item::id,
    )
}
