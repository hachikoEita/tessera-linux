// Copyright (C) 2026 Daryan Kamaran Karim
// SPDX-License-Identifier: GPL-3.0-or-later

package com.daryan.tessera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShortcutsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `shortcuts become valid launcher apps`() {
        val m = Shortcut.CLAUDE_CODE.toManifest()
        assertEquals(emptyList<String>(), m.validate())
        assertEquals(listOf("/bin/bash", "-lc"), m.exec.take(2))
        assertTrue(Shortcut.isShortcut(m))
        assertEquals("claude-code", Shortcut.idOf(m))
        val window = Shortcut("x", "X", "xeyes", window = true).toManifest()
        assertEquals(emptyList<String>(), window.validate())
        assertTrue(window.gui && window.permissions.display)
    }

    @Test fun `ids are slugs, unique`() {
        assertEquals("update-system", Shortcut.newId("Update system!", emptyList()))
        assertEquals("update-system-2", Shortcut.newId("Update system", listOf("update-system")))
        assertEquals("command", Shortcut.newId("!!!", emptyList()))
    }

    @Test fun `store starts with Claude Code and OpenDesign and persists edits`() {
        val file = File(tmp.root, "shortcuts.json")
        val store = ShortcutStore(file)
        assertEquals(listOf("claude-code", "open-design"), store.shortcuts.value.map { it.id })
        store.save(Shortcut("up", "Update", "sudo apt update"))
        store.delete("claude-code")
        store.delete("open-design")
        // Deleting OpenDesign sticks: it is only offered once.
        assertEquals(listOf("up"), ShortcutStore(file).shortcuts.value.map { it.id })
    }

    @Test fun `an older shortcuts file gains OpenDesign once`() {
        val file = File(tmp.root, "shortcuts.json")
        file.writeText("[{\"id\":\"up\",\"name\":\"Update\",\"command\":\"sudo apt update\"}]")
        assertEquals(listOf("up", "open-design"), ShortcutStore(file).shortcuts.value.map { it.id })
    }

    @Test fun `OpenDesign shortcut is a valid launcher app without root`() {
        val m = Shortcut.OPEN_DESIGN.toManifest()
        assertEquals(emptyList<String>(), m.validate())
        assertTrue("\$HOME/.local/node/bin" in Shortcut.OPEN_DESIGN.command)
        assertTrue("§" !in Shortcut.OPEN_DESIGN.command)
        assertTrue(!m.permissions.root)
    }
}
