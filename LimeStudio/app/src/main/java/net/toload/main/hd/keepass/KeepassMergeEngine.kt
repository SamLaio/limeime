package net.toload.main.hd.keepass

import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.modifyContent
import app.keemobile.kotpass.models.CustomDataValue
import app.keemobile.kotpass.models.CustomIcon
import app.keemobile.kotpass.models.DeletedObject
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import java.time.Instant
import java.util.UUID

class KeepassMergeEngine {
    fun merge(
        base: KeePassDatabase,
        local: KeePassDatabase,
        remote: KeePassDatabase,
    ): KeePassDatabase {
        val context = MergeContext.create(base, local, remote)
        val mergedRoot =
            mergeRootGroup(
                base = base.content.group,
                local = local.content.group,
                remote = remote.content.group,
                context = context,
            )
        val mergedDeletedObjects =
            (base.content.deletedObjects + local.content.deletedObjects + remote.content.deletedObjects)
                .groupBy { it.id }
                .map { (_, deletions) -> deletions.maxBy { it.deletionTime } }

        return local.modifyContent {
            copy(
                meta = mergeMeta(base.content.meta, local.content.meta, remote.content.meta),
                group = mergedRoot,
                deletedObjects = mergedDeletedObjects,
            )
        }
    }

    private fun mergeRootGroup(
        base: Group,
        local: Group,
        remote: Group,
        context: MergeContext,
    ): Group {
        val selected =
            selectExisting(
                base = base,
                local = local,
                remote = remote,
                modifiedAt = { group -> group.modifiedAt() },
            ) ?: local
        return selected.copy(
            customData = mergeCustomData(base.customData, local.customData, remote.customData, selected.customData),
            entries = mergeEntriesForParent(selected.uuid, context),
            groups = mergeGroupsForParent(selected.uuid, context),
        )
    }

    private fun mergeGroupById(id: UUID, context: MergeContext): Group? {
        val base = context.baseGroups[id]?.item
        val local = context.localGroups[id]?.item
        val remote = context.remoteGroups[id]?.item
        val selected =
            selectItem(
                base = base,
                local = local,
                remote = remote,
                localDeletionTime = context.localDeleted[id],
                remoteDeletionTime = context.remoteDeleted[id],
                modifiedAt = { group -> group.modifiedAt() },
            ) ?: return null

        return selected.copy(
            customData = mergeCustomData(
                base = base?.customData.orEmpty(),
                local = local?.customData.orEmpty(),
                remote = remote?.customData.orEmpty(),
                fallback = selected.customData,
            ),
            entries = mergeEntriesForParent(selected.uuid, context),
            groups = mergeGroupsForParent(selected.uuid, context),
        )
    }

    private fun mergeGroupsForParent(parentId: UUID, context: MergeContext): List<Group> {
        return context.groupIds
            .filter { id -> selectGroupParent(id, context) == parentId }
            .mapNotNull { id -> mergeGroupById(id, context) }
    }

    private fun mergeEntriesForParent(parentId: UUID, context: MergeContext): List<Entry> {
        return context.entryIds
            .filter { id -> selectEntryParent(id, context) == parentId }
            .mapNotNull { id -> mergeEntryById(id, context) }
    }

    private fun mergeEntryById(id: UUID, context: MergeContext): Entry? {
        val base = context.baseEntries[id]?.item
        val local = context.localEntries[id]?.item
        val remote = context.remoteEntries[id]?.item
        val selected =
            selectItem(
                base = base,
                local = local,
                remote = remote,
                localDeletionTime = context.localDeleted[id],
                remoteDeletionTime = context.remoteDeleted[id],
                modifiedAt = { entry -> entry.modifiedAt() },
            ) ?: return null

        if (base == null || local == null || remote == null) {
            return selected.withMergedHistoryAndCustomData(base, local, remote)
        }
        if (local == remote || local == base || remote == base) {
            return selected.withMergedHistoryAndCustomData(base, local, remote)
        }

        return selected.copy(
            fields = mergeFields(
                base = base.fields,
                local = local.fields,
                remote = remote.fields,
                fallback = selected.fields,
            ),
            tags = (base.tags + local.tags + remote.tags + selected.tags).distinct(),
            binaries = (base.binaries + local.binaries + remote.binaries + selected.binaries).distinct(),
            history = mergeEntryHistory(base, local, remote, selected),
            customData = mergeCustomData(base.customData, local.customData, remote.customData, selected.customData),
        )
    }

    private fun Entry.withMergedHistoryAndCustomData(
        base: Entry?,
        local: Entry?,
        remote: Entry?,
    ): Entry {
        return copy(
            history = mergeEntryHistory(base, local, remote, this),
            customData = mergeCustomData(
                base = base?.customData.orEmpty(),
                local = local?.customData.orEmpty(),
                remote = remote?.customData.orEmpty(),
                fallback = customData,
            ),
        )
    }

    private fun mergeEntryHistory(
        base: Entry?,
        local: Entry?,
        remote: Entry?,
        selected: Entry,
    ): List<Entry> {
        return (base?.history.orEmpty() + local?.history.orEmpty() + remote?.history.orEmpty() + selected.history)
            .distinctBy { entry -> entry.historyKey() }
            .sortedByDescending { entry -> entry.modifiedAt() }
            .take(selected.history.size.coerceAtLeast(10))
    }

    private fun mergeFields(
        base: EntryFields,
        local: EntryFields,
        remote: EntryFields,
        fallback: EntryFields,
    ): EntryFields {
        val keys = (base.keys + local.keys + remote.keys + fallback.keys).distinct()
        val mergedFields = mutableMapOf<String, EntryValue>()
        val conflictFields = mutableMapOf<String, EntryValue>()
        keys.forEach { key ->
            val selected =
                selectField(
                    key = key,
                    base = base[key],
                    local = local[key],
                    remote = remote[key],
                    fallback = fallback[key],
                    conflictFields = conflictFields,
                )
            if (selected != null) {
                mergedFields[key] = selected
            }
        }
        conflictFields.forEach { (key, value) ->
            if (!mergedFields.containsKey(key)) {
                mergedFields[key] = value
            }
        }
        return EntryFields(mergedFields)
    }

    private fun selectField(
        key: String,
        base: EntryValue?,
        local: EntryValue?,
        remote: EntryValue?,
        fallback: EntryValue?,
        conflictFields: MutableMap<String, EntryValue>,
    ): EntryValue? {
        return when {
            local.sameContentAs(remote) -> local ?: remote
            local.sameContentAs(base) -> remote
            remote.sameContentAs(base) -> local
            else -> fallback.also {
                addConflictFields(key, local, remote, it, conflictFields)
            }
        }
    }

    private fun addConflictFields(
        key: String,
        local: EntryValue?,
        remote: EntryValue?,
        fallback: EntryValue?,
        sink: MutableMap<String, EntryValue>,
    ) {
        if (local != null && !local.sameContentAs(fallback)) {
            sink[conflictFieldKey("local", key)] = local
        }
        if (remote != null && !remote.sameContentAs(fallback)) {
            sink[conflictFieldKey("remote", key)] = remote
        }
    }

    private fun conflictFieldKey(side: String, key: String): String {
        return "Conflict/$side/$key"
    }

    private fun mergeMeta(base: Meta, local: Meta, remote: Meta): Meta {
        val selected =
            selectExisting(
                base = base,
                local = local,
                remote = remote,
                modifiedAt = { meta -> meta.settingsChanged ?: Instant.EPOCH },
            ) ?: local
        return selected.copy(
            customIcons = mergeCustomIcons(base.customIcons, local.customIcons, remote.customIcons, selected.customIcons),
            customData = mergeCustomData(base.customData, local.customData, remote.customData, selected.customData),
        )
    }

    private fun mergeCustomIcons(
        base: Map<UUID, CustomIcon>,
        local: Map<UUID, CustomIcon>,
        remote: Map<UUID, CustomIcon>,
        fallback: Map<UUID, CustomIcon>,
    ): Map<UUID, CustomIcon> {
        val keys = (base.keys + local.keys + remote.keys + fallback.keys).distinct()
        return keys.mapNotNull { key ->
            val selected =
                selectExisting(
                    base = base[key],
                    local = local[key],
                    remote = remote[key],
                    modifiedAt = { icon -> icon.lastModified ?: Instant.EPOCH },
                ) ?: fallback[key]
            selected?.let { key to it }
        }.toMap()
    }

    private fun mergeCustomData(
        base: Map<String, CustomDataValue>,
        local: Map<String, CustomDataValue>,
        remote: Map<String, CustomDataValue>,
        fallback: Map<String, CustomDataValue>,
    ): Map<String, CustomDataValue> {
        val keys = (base.keys + local.keys + remote.keys + fallback.keys).distinct()
        return keys.mapNotNull { key ->
            val selected =
                selectExisting(
                    base = base[key],
                    local = local[key],
                    remote = remote[key],
                    modifiedAt = { value -> value.lastModified ?: Instant.EPOCH },
                ) ?: fallback[key]
            selected?.let { key to it }
        }.toMap()
    }

    private fun selectEntryParent(id: UUID, context: MergeContext): UUID? {
        return selectParent(
            base = context.baseEntries[id],
            local = context.localEntries[id],
            remote = context.remoteEntries[id],
            modifiedAt = { entry -> entry.modifiedAt() },
        )
    }

    private fun selectGroupParent(id: UUID, context: MergeContext): UUID? {
        return selectParent(
            base = context.baseGroups[id],
            local = context.localGroups[id],
            remote = context.remoteGroups[id],
            modifiedAt = { group -> group.modifiedAt() },
        )
    }

    private fun <T> selectParent(
        base: ItemSnapshot<T>?,
        local: ItemSnapshot<T>?,
        remote: ItemSnapshot<T>?,
        modifiedAt: (T) -> Instant,
    ): UUID? {
        return when {
            local == null && remote == null -> null
            local == null -> remote?.parentId
            remote == null -> local.parentId
            local.item == remote.item && local.parentId == remote.parentId -> local.parentId
            base != null && local.item == base.item && local.parentId == base.parentId -> remote.parentId
            base != null && remote.item == base.item && remote.parentId == base.parentId -> local.parentId
            modifiedAt(local.item) >= modifiedAt(remote.item) -> local.parentId
            else -> remote.parentId
        }
    }

    private fun <T> selectItem(
        base: T?,
        local: T?,
        remote: T?,
        localDeletionTime: Instant?,
        remoteDeletionTime: Instant?,
        modifiedAt: (T) -> Instant,
    ): T? {
        val selected =
            selectExisting(
                base = base,
                local = local,
                remote = remote,
                modifiedAt = modifiedAt,
            ) ?: return null
        val selectedModifiedAt = modifiedAt(selected)

        if (local == null && localDeletionTime != null && localDeletionTime >= selectedModifiedAt) {
            return null
        }
        if (remote == null && remoteDeletionTime != null && remoteDeletionTime >= selectedModifiedAt) {
            return null
        }
        return selected
    }

    private fun <T> selectExisting(
        base: T?,
        local: T?,
        remote: T?,
        modifiedAt: (T) -> Instant,
    ): T? {
        return when {
            local == null && remote == null -> null
            local == null -> remote
            remote == null -> local
            local == remote -> local
            base != null && local == base -> remote
            base != null && remote == base -> local
            modifiedAt(local) >= modifiedAt(remote) -> local
            else -> remote
        }
    }

    private fun List<DeletedObject>.latestDeletionMap(): Map<UUID, Instant> {
        return groupBy { it.id }
            .mapValues { (_, deletions) -> deletions.maxOf { it.deletionTime } }
    }

    private fun Group.modifiedAt(): Instant {
        return times?.lastModificationTime ?: times?.locationChanged ?: Instant.EPOCH
    }

    private fun Entry.modifiedAt(): Instant {
        return times?.lastModificationTime ?: times?.locationChanged ?: Instant.EPOCH
    }

    private fun Entry.historyKey(): String {
        return listOf(
            uuid.toString(),
            modifiedAt().toString(),
            fields.entries.joinToString("|") { (key, value) -> "$key=${value.content}" },
        ).joinToString("/")
    }

    private fun EntryValue?.sameContentAs(other: EntryValue?): Boolean {
        if (this == null || other == null) {
            return this == other
        }
        return content == other.content
    }

    private data class ItemSnapshot<T>(
        val item: T,
        val parentId: UUID,
    )

    private data class MergeContext(
        val baseEntries: Map<UUID, ItemSnapshot<Entry>>,
        val localEntries: Map<UUID, ItemSnapshot<Entry>>,
        val remoteEntries: Map<UUID, ItemSnapshot<Entry>>,
        val baseGroups: Map<UUID, ItemSnapshot<Group>>,
        val localGroups: Map<UUID, ItemSnapshot<Group>>,
        val remoteGroups: Map<UUID, ItemSnapshot<Group>>,
        val localDeleted: Map<UUID, Instant>,
        val remoteDeleted: Map<UUID, Instant>,
    ) {
        val entryIds: Set<UUID> = baseEntries.keys + localEntries.keys + remoteEntries.keys
        val groupIds: Set<UUID> = baseGroups.keys + localGroups.keys + remoteGroups.keys

        companion object {
            fun create(base: KeePassDatabase, local: KeePassDatabase, remote: KeePassDatabase): MergeContext {
                return MergeContext(
                    baseEntries = base.content.group.entrySnapshots(),
                    localEntries = local.content.group.entrySnapshots(),
                    remoteEntries = remote.content.group.entrySnapshots(),
                    baseGroups = base.content.group.groupSnapshots(),
                    localGroups = local.content.group.groupSnapshots(),
                    remoteGroups = remote.content.group.groupSnapshots(),
                    localDeleted = local.content.deletedObjects.latestDeletionMap(),
                    remoteDeleted = remote.content.deletedObjects.latestDeletionMap(),
                )
            }

            private fun Group.entrySnapshots(): Map<UUID, ItemSnapshot<Entry>> {
                val result = mutableMapOf<UUID, ItemSnapshot<Entry>>()
                collectEntrySnapshots(this, result)
                return result
            }

            private fun collectEntrySnapshots(
                group: Group,
                result: MutableMap<UUID, ItemSnapshot<Entry>>,
            ) {
                group.entries.forEach { entry ->
                    result[entry.uuid] = ItemSnapshot(entry, group.uuid)
                }
                group.groups.forEach { child ->
                    collectEntrySnapshots(child, result)
                }
            }

            private fun Group.groupSnapshots(): Map<UUID, ItemSnapshot<Group>> {
                val result = mutableMapOf<UUID, ItemSnapshot<Group>>()
                collectGroupSnapshots(this, result)
                return result
            }

            private fun collectGroupSnapshots(
                group: Group,
                result: MutableMap<UUID, ItemSnapshot<Group>>,
            ) {
                group.groups.forEach { child ->
                    result[child.uuid] = ItemSnapshot(child, group.uuid)
                    collectGroupSnapshots(child, result)
                }
            }

            private fun List<DeletedObject>.latestDeletionMap(): Map<UUID, Instant> {
                return groupBy { it.id }
                    .mapValues { (_, deletions) -> deletions.maxOf { it.deletionTime } }
            }
        }
    }
}
