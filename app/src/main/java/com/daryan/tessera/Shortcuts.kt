// Copyright (C) 2026 Daryan Kamaran Karim
// SPDX-License-Identifier: GPL-3.0-or-later

package com.daryan.tessera

import com.daryan.tessera.container.FsGrant
import com.daryan.tessera.container.PermissionManifest
import com.daryan.tessera.container.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

const val SHORTCUTS_TAB = "Shortcuts"
private const val ID_PREFIX = "shortcut-"

/** A command the user put on the launcher as an icon of its own. */
@Serializable
data class Shortcut(
    val id: String,
    val name: String,
    /** Run by bash as a login shell, so it sees the user's PATH and profile. */
    val command: String,
    /** In a window of its own (with a display) rather than a terminal. */
    val window: Boolean = false,
    val root: Boolean = false,
    /** Key into the launcher's icon set. */
    val icon: String = "terminal",
    /** Distro packages the command needs; offered for install like an app's. */
    val packages: List<String> = emptyList(),
    /** Shown under the name; the command's first line when empty. */
    val description: String = "",
) {
    /**
     * [tesseraDir]: where Tessera keeps its notes and the logs of sessions that just ended. Only
     * the Claude Code shortcut is shown them, read-only, so it can diagnose what the user sees.
     */
    fun toManifest(tesseraDir: File? = null) = PermissionManifest(
        id = ID_PREFIX + id,
        name = name,
        description = description.ifEmpty { command.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty() },
        category = SHORTCUTS_TAB,
        icon = icon,
        exec = listOf("/bin/bash", "-lc", command),
        gui = window,
        packages = packages,
        permissions = Permissions(
            network = true,
            display = window,
            audio = window,
            root = root,
            filesystem = listOfNotNull(
                tesseraDir?.takeIf { id == CLAUDE_CODE.id }?.let { FsGrant(it.path, TESSERA_DIR, readOnly = true) },
            ),
        ),
    )

    companion object {
        /** Where [com.daryan.tessera.container.Diagnostics] appears inside the container. */
        const val TESSERA_DIR = "/home/user/.tessera"

        fun isShortcut(app: PermissionManifest) = app.id.startsWith(ID_PREFIX)

        fun idOf(app: PermissionManifest) = app.id.removePrefix(ID_PREFIX)

        /** A new shortcut's id from its name, unique among [taken]. */
        fun newId(name: String, taken: Collection<String>): String {
            val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "command" }
            return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { it !in taken }
        }

        /**
         * Claude Code in a terminal. The first run installs it with Anthropic's installer
         * (into ~/.local/bin, no root needed), later runs start it straight away.
         *
         * Before it starts, Tessera's notes about itself are added to ~/CLAUDE.md, which Claude
         * Code reads from the directory it runs in: how the container is built, what it cannot
         * see from inside, the problems that recur, and that a bug report is the user's to post.
         * Appended once, by a marker, so an edited CLAUDE.md is never overwritten.
         */
        val CLAUDE_CODE = Shortcut(
            id = "claude-code",
            name = "Claude Code",
            command = """
                export PATH="${'$'}HOME/.local/bin:${'$'}PATH"
                if [ -f ~/.tessera/TESSERA.md ] && ! grep -q TESSERA-CONTEXT ~/CLAUDE.md 2>/dev/null; then
                  cat ~/.tessera/TESSERA.md >> ~/CLAUDE.md
                fi
                if ! command -v claude >/dev/null 2>&1; then
                  echo "Installing Claude Code (first run only)..."
                  curl -fsSL https://claude.ai/install.sh | bash || { echo "Install failed; see above."; exec bash -l; }
                fi
                cd ~ && exec claude
            """.trimIndent(),
            icon = "spark",
            packages = listOf("curl", "ca-certificates", "git"),
            description = "Anthropic's coding agent, in a terminal. Knows how Tessera works, and reads what just failed.",
        )

        /**
         * OpenDesign, the AI design tool, run from the user's own fork (hachikoEita/open-design,
         * branch tessera-n6). The first run installs everything with no root: Node 24 from nodejs.org
         * (checked against the published SHA-256), pnpm, then the project in copy mode. Copy mode matters
         * here: PRoot fakes hard links with hidden .l2s.* files, which breaks pnpm's default store and
         * makes the TypeScript compiler fail at random. Later runs start the engine straight away on
         * fixed ports, so the address never changes. Open it in the browser; nothing signs in anywhere.
         */
        val OPEN_DESIGN = Shortcut(
            id = "open-design",
            name = "OpenDesign",
            command = script(
                """
                export PATH="§HOME/.local/node/bin:§HOME/.local/bin:§PATH"
                OD="§HOME/open-design"
                WEB=17575
                DAEMON=17576
                fail() { echo; echo "§1"; echo "Fix the problem above, then open OpenDesign again."; exec bash -l; }

                if ! { command -v node >/dev/null 2>&1 && node -v | grep -q '^v24[.]'; }; then
                  echo "Installing Node 24 (first run only)..."
                  base=https://nodejs.org/dist/latest-v24.x
                  tmp=§(mktemp -d)
                  cd "§tmp" || fail "No temporary folder."
                  curl -fsSL "§base/SHASUMS256.txt" -o sums || fail "Could not download the Node checksum list."
                  f=§(awk '/linux-arm64[.]tar[.]xz§/ {print §2}' sums)
                  [ -n "§f" ] || fail "Could not find Node for this phone."
                  curl -fsSL "§base/§f" -o "§f" || fail "Could not download Node."
                  grep " §f§" sums | sha256sum -c - || fail "Node did not match its published checksum."
                  rm -rf "§HOME/.local/node" && mkdir -p "§HOME/.local/node" \
                    && tar -xJf "§f" -C "§HOME/.local/node" --strip-components=1 || fail "Could not unpack Node."
                  cd ~ && rm -rf "§tmp"
                fi

                if ! command -v pnpm >/dev/null 2>&1; then
                  echo "Installing pnpm (first run only)..."
                  npm install -g pnpm@10.33.2 --prefix "§HOME/.local" || fail "Could not install pnpm."
                fi

                if [ ! -d "§OD/.git" ]; then
                  echo "Downloading OpenDesign (first run only)..."
                  git clone --depth 1 --branch tessera-n6 https://github.com/hachikoEita/open-design.git "§OD" || fail "Could not download OpenDesign."
                fi

                cd "§OD" || fail "OpenDesign folder is missing."
                if [ ! -f .tessera-installed ]; then
                  echo "Installing OpenDesign (first run only, about 15 to 30 minutes)..."
                  pnpm install --ignore-scripts --config.package-import-method=copy || fail "The install failed."
                  node scripts/postinstall.mjs build || fail "The build failed."
                  node scripts/postinstall.mjs dependencies || fail "The database part failed to build."
                  touch .tessera-installed
                fi

                if ! command -v opencode >/dev/null 2>&1; then
                  echo "Installing OpenCode (first run only)..."
                  npm install -g opencode-ai --prefix "§HOME/.local" || echo "OpenCode did not install. Try again later: npm install -g opencode-ai --prefix ~/.local"
                fi

                echo
                echo "OpenDesign is starting. When it says ready, open this in your browser:"
                echo "  http://localhost:§WEB/"
                echo "Do not press Sign up or Log in there. Use Local AI, then OpenCode."
                echo
                exec pnpm tools-dev run web --web-port §WEB --daemon-port §DAEMON
                """,
            ),
            icon = "palette",
            packages = listOf("curl", "ca-certificates", "git", "xz-utils", "build-essential", "python3"),
            description = "AI design tool, from your own fork. Starts a local page; open it in your browser.",
        )

        /** A shell script written with § where it needs a literal dollar sign. */
        private fun script(text: String) = text.trimIndent().replace('§', '\u0024')
    }
}

/** The user's shortcuts, in [file]; starts with Claude Code and OpenDesign. */
class ShortcutStore(private val file: File) {
    private val _shortcuts = MutableStateFlow(load())
    val shortcuts: StateFlow<List<Shortcut>> = _shortcuts

    /** Remembers that OpenDesign was added once, so deleting it stays deleted. */
    private val offered = File(file.parentFile, file.name + ".open-design-offered")

    init {
        if (!offered.exists()) {
            if (_shortcuts.value.none { it.id == Shortcut.OPEN_DESIGN.id }) edit { it + Shortcut.OPEN_DESIGN }
            runCatching { offered.parentFile?.mkdirs(); offered.writeText("1") }
        }
    }

    /** Adds [shortcut], or replaces the one with its id. */
    fun save(shortcut: Shortcut) = edit { list ->
        if (list.any { it.id == shortcut.id }) list.map { if (it.id == shortcut.id) shortcut else it } else list + shortcut
    }

    fun delete(id: String) = edit { list -> list.filterNot { it.id == id } }

    private fun edit(f: (List<Shortcut>) -> List<Shortcut>) {
        _shortcuts.update(f)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(SERIALIZER, _shortcuts.value))
        }
    }

    private fun load(): List<Shortcut> =
        if (!file.exists()) listOf(Shortcut.CLAUDE_CODE)
        else runCatching { json.decodeFromString(SERIALIZER, file.readText()) }.getOrDefault(emptyList()).map(::refreshed)

    /**
     * Tessera's own Claude Code shortcut gains abilities between versions (latest: the notes that
     * tell it how Tessera works). A stored copy that still runs the installer but lacks them is
     * replaced by the current one, keeping the user's icon and window choices. A command rewritten
     * past recognition is left alone — as is anything the user wrote themselves.
     */
    private fun refreshed(s: Shortcut): Shortcut =
        if (s.id == Shortcut.CLAUDE_CODE.id && "claude.ai/install.sh" in s.command && "TESSERA-CONTEXT" !in s.command) {
            Shortcut.CLAUDE_CODE.copy(window = s.window, root = s.root, icon = s.icon)
        } else {
            s
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        val SERIALIZER = ListSerializer(Shortcut.serializer())
    }
}
