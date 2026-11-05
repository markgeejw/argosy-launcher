package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BaseRomFileResolver"

/**
 * Resolves the file that is the game itself from the path the library recorded for it.
 *
 * A game's local path can land on an update, a dlc or a file inside a content subfolder, and on
 * title id platforms the largest file in the game's folder is the game. Launching and title id
 * extraction share this so they can never read different files for the same game.
 */
@Singleton
class BaseRomFileResolver @Inject constructor(
    private val gameDao: GameDao,
    private val platformFolderResolver: PlatformFolderResolver
) {
    /**
     * An update or DLC file is unbootable alone, so redirect to the base rom and persist it.
     */
    suspend fun resolve(game: GameEntity, romFile: File): File {
        val base = locate(game, romFile)
        if (base.absolutePath == romFile.absolutePath) return romFile
        Logger.info(TAG, "redirecting ${romFile.name} -> ${base.name} for ${game.title}")
        gameDao.updateLocalPath(game.id, base.absolutePath, game.source, game.fileOrigin)
        return base
    }

    /**
     * The base rom for [romFile], or [romFile] itself when there is nothing better.
     *
     * Title id platforms always re-elect the base, other platforms only when the target is
     * provably update/dlc content, preferring an m3u so multi-disc folders keep their playlist.
     * A Switch update or dlc whose folder holds no base falls back to the platform roots, where
     * a flat download leaves the base beside the game folders.
     */
    suspend fun locate(game: GameEntity, romFile: File): File {
        val excluded = game.platformSlug in VariantCategory.TITLE_ID_PLATFORMS
        val parent = romFile.parentFile ?: return romFile
        val inContentSubfolder = parent.name.lowercase() in ZipExtractor.ADDON_FOLDERS
        if (!excluded && !inContentSubfolder && !isUpdateOrDlc(romFile.name)) return romFile
        val gameFolder = if (inContentSubfolder) {
            parent.parentFile ?: return romFile
        } else {
            parent
        }
        val switchAddonId = SwitchTitleId.of(romFile.name)
            ?.takeIf { game.platformSlug == SWITCH && !SwitchTitleId.isBase(it) }
        val roots = platformFolderResolver.romRootsForPlatform(game.platformId, game.platformSlug)
        if (isPlatformRoot(gameFolder, roots)) {
            return switchAddonId?.let { switchBaseIn(roots, romFile, it) } ?: romFile
        }
        val candidates = (gameFolder.listFiles()?.filter { it.isFile } ?: return romFile)
            .filterNot { isUpdateOrDlc(it.name) || isSwitchAddon(game, it.name) }
        val base = if (excluded) {
            candidates.maxByOrNull { it.length() }
        } else {
            candidates.firstOrNull { it.extension.equals("m3u", ignoreCase = true) }
                ?: candidates.maxByOrNull { it.length() }
        }
        return base
            ?: switchAddonId?.let { switchBaseIn(roots, romFile, it) }
            ?: romFile
    }

    private fun isSwitchAddon(game: GameEntity, fileName: String): Boolean =
        game.platformSlug == SWITCH && SwitchTitleId.of(fileName)?.let { !SwitchTitleId.isBase(it) } == true

    /**
     * The base for a Switch add-on among the files directly inside [roots]: one carrying the
     * add-on's base title id, or else an untagged dump with the same name before its tags.
     */
    private fun switchBaseIn(roots: List<File>, addon: File, addonId: Long): File? {
        val baseId = SwitchTitleId.baseOf(addonId)
        val prefix = SwitchTitleId.namePrefix(addon.name)
        val files = roots.flatMap { root -> root.listFiles()?.filter { it.isFile }.orEmpty() }
            .filter { it.extension.lowercase() in SwitchTitleId.CONTAINER_EXTENSIONS }
        return files.filter { SwitchTitleId.of(it.name) == baseId }.maxByOrNull { it.length() }
            ?: files.filter {
                SwitchTitleId.of(it.name) == null &&
                    prefix.isNotEmpty() &&
                    SwitchTitleId.namePrefix(it.name).equals(prefix, ignoreCase = true)
            }.maxByOrNull { it.length() }
    }

    private fun isUpdateOrDlc(fileName: String): Boolean {
        val stem = fileName.substringBeforeLast('.')
        return UPDATE_DLC_TAG.containsMatchIn(stem) || UPDATE_DLC_SUFFIX.containsMatchIn(stem)
    }

    /**
     * True when [dir] is a directory roms sit directly inside rather than a game's own folder.
     *
     * Checked against every root discovery is allowed to link from, not just the download
     * destination: a rom found in one of the other roots would otherwise look like it lived in a
     * game folder, and the folder logic would repoint the game at the platform directory's
     * largest file.
     */
    private fun isPlatformRoot(dir: File, roots: List<File>): Boolean {
        val target = runCatching { dir.canonicalFile }.getOrDefault(dir.absoluteFile)
        return roots.any { root ->
            runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile) == target
        }
    }

    private companion object {
        const val SWITCH = "switch"
        val UPDATE_DLC_SUFFIX = Regex("(?i)[ _-]+(update|dlc)([ _-]?\\d+)?$")
        val UPDATE_DLC_TAG = Regex("(?i)\\[(upd|update|dlc)]")
    }
}
