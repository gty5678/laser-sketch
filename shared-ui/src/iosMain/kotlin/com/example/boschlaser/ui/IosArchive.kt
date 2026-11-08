@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import com.example.boschlaser.shared.model.Project
import com.example.boschlaser.shared.model.ProjectAsset
import com.example.boschlaser.shared.storage.ProjectSerializer
import kotlinx.cinterop.*
import platform.zlib.*

internal data class IosArchive(val project: Project, val assets: Map<String, ByteArray>)

/** ZIP32 interchange. No entry is extracted to a path; bounds are enforced before inflation. */
internal object IosProjectArchive {
    private const val TOTAL = 256 * 1024 * 1024
    private const val ASSET = 128 * 1024 * 1024
    private const val METADATA = 32 * 1024 * 1024
    private fun checkPath(name: String) {
        if (name !in ProjectSerializer.entries) ProjectAsset("entry", name, "application/octet-stream")
    }
    private fun crc(bytes: ByteArray): Long = if (bytes.isEmpty()) 0L else bytes.usePinned {
        crc32(0uL, it.addressOf(0).reinterpret(), bytes.size.toUInt()).toLong() and 0xffffffffL
    }
    private class Writer {
        private val chunks = mutableListOf<ByteArray>()
        var size = 0
            private set
        fun bytes(value: ByteArray) { require(size.toLong() + value.size <= TOTAL); chunks += value; size += value.size }
        fun number(value: Long, count: Int) { bytes(ByteArray(count) { (value ushr (it * 8)).toByte() }) }
        fun result(): ByteArray = ByteArray(size).also { out -> var at = 0; chunks.forEach { it.copyInto(out, at); at += it.size } }
    }
    fun write(project: Project, assets: Map<String, ByteArray>): ByteArray {
        require(assets.keys == project.assets.keys)
        val entries = linkedMapOf<String, ByteArray>()
        ProjectSerializer.encode(project).forEach { (name, text) -> entries[name] = text.encodeToByteArray() }
        project.assets.forEach { (id, asset) -> entries[asset.path] = assets.getValue(id) }
        require(entries.size <= 4096)
        val out = Writer()
        val central = Writer()
        var total = 0L
        entries.forEach { (name, bytes) ->
            checkPath(name)
            require(bytes.size <= if (name in ProjectSerializer.entries) METADATA else ASSET)
            total += bytes.size; require(total <= TOTAL)
            val encodedName = name.encodeToByteArray()
            require(encodedName.size <= 65535)
            val offset = out.size
            val checksum = crc(bytes)
            out.number(0x04034b50, 4); out.number(20, 2); out.number(0x800, 2); out.number(0, 2)
            out.number(0, 2); out.number(0x21, 2); out.number(checksum, 4)
            out.number(bytes.size.toLong(), 4); out.number(bytes.size.toLong(), 4)
            out.number(encodedName.size.toLong(), 2); out.number(0, 2); out.bytes(encodedName); out.bytes(bytes)
            central.number(0x02014b50, 4); central.number(20, 2); central.number(20, 2); central.number(0x800, 2)
            central.number(0, 2); central.number(0, 2); central.number(0x21, 2); central.number(checksum, 4)
            central.number(bytes.size.toLong(), 4); central.number(bytes.size.toLong(), 4)
            central.number(encodedName.size.toLong(), 2); central.number(0, 2); central.number(0, 2)
            central.number(0, 2); central.number(0, 2); central.number(0, 4); central.number(offset.toLong(), 4)
            central.bytes(encodedName)
        }
        val offset = out.size
        out.bytes(central.result())
        out.number(0x06054b50, 4); out.number(0, 2); out.number(0, 2)
        out.number(entries.size.toLong(), 2); out.number(entries.size.toLong(), 2)
        out.number(central.size.toLong(), 4); out.number(offset.toLong(), 4); out.number(0, 2)
        return out.result()
    }
    fun read(data: ByteArray): IosArchive {
        require(data.size in 22..TOTAL)
        fun number(at: Int, count: Int): Long {
            require(at >= 0 && at.toLong() + count <= data.size)
            return (0 until count).fold(0L) { n, i -> n or ((data[at + i].toLong() and 255) shl (8 * i)) }
        }
        fun n(at: Int, count: Int = 2) = number(at, count).also { require(it <= Int.MAX_VALUE) }.toInt()
        val end = (data.size - 22 downTo maxOf(0, data.size - 65557)).firstOrNull {
            number(it, 4) == 0x06054b50L && it + 22 + n(it + 20) == data.size
        } ?: error("Invalid ZIP directory")
        require(n(end + 4) == 0 && n(end + 6) == 0 && n(end + 8) == n(end + 10))
        val count = n(end + 10)
        require(count in 1..4096)
        val directorySize = n(end + 12, 4)
        val directory = n(end + 16, 4)
        require(directory.toLong() + directorySize == end.toLong())
        val entries = linkedMapOf<String, ByteArray>()
        val ranges = mutableListOf<IntRange>()
        var at = directory
        var total = 0L
        repeat(count) {
            require(number(at, 4) == 0x02014b50L)
            val flags = n(at + 8)
            val method = n(at + 10)
            require(flags and 0x0808.inv() == 0 && method in listOf(0, 8) && n(at + 34) == 0)
            val checksum = number(at + 16, 4)
            val compressed = n(at + 20, 4)
            val size = n(at + 24, 4)
            val nameLength = n(at + 28)
            val next = at.toLong() + 46 + nameLength + n(at + 30) + n(at + 32)
            require(next <= end)
            val name = data.copyOfRange(at + 46, at + 46 + nameLength).decodeToString(throwOnInvalidSequence = true)
            checkPath(name); require(name !in entries)
            require(size <= if (name in ProjectSerializer.entries) METADATA else ASSET)
            total += size; require(total <= TOTAL)
            val local = n(at + 42, 4)
            require(number(local, 4) == 0x04034b50L && n(local + 6) == flags && n(local + 8) == method)
            val start = local.toLong() + 30 + n(local + 26) + n(local + 28)
            require(start <= directory && start + compressed <= directory)
            require(data.copyOfRange(local + 30, local + 30 + n(local + 26))
                .decodeToString(throwOnInvalidSequence = true) == name)
            val range = local until (start.toInt() + compressed)
            require(ranges.none { range.first <= it.last && it.first <= range.last }); ranges += range
            val payload = data.copyOfRange(start.toInt(), start.toInt() + compressed)
            val bytes = if (method == 0) payload.also { require(it.size == size) } else inflateRaw(payload, size)
            require(crc(bytes) == checksum)
            entries[name] = bytes
            at = next.toInt()
        }
        require(at == end)
        val project = ProjectSerializer.decode(entries.filterKeys { it in ProjectSerializer.entries }
            .mapValues { it.value.decodeToString(throwOnInvalidSequence = true) })
        require(entries.keys == ProjectSerializer.entries + project.assets.values.map { it.path })
        return IosArchive(project, project.assets.mapValues { entries.getValue(it.value.path) })
    }
    private fun inflateRaw(input: ByteArray, size: Int): ByteArray = memScoped {
        require(input.isNotEmpty())
        val output = ByteArray(size + 1)
        val stream = alloc<z_stream>()
        platform.posix.memset(stream.ptr, 0, sizeOf<z_stream>().toULong())
        require(inflateInit2_(stream.ptr, -15, zlibVersion()?.toKString(), sizeOf<z_stream>().toInt()) == Z_OK)
        try {
            input.usePinned { source -> output.usePinned { target ->
                stream.next_in = source.addressOf(0).reinterpret(); stream.avail_in = input.size.toUInt()
                stream.next_out = target.addressOf(0).reinterpret(); stream.avail_out = output.size.toUInt()
                require(inflate(stream.ptr, Z_FINISH) == Z_STREAM_END)
                require(stream.total_out.toLong() == size.toLong() && stream.avail_in == 0u)
            } }
        } finally { inflateEnd(stream.ptr) }
        output.copyOf(size)
    }
}
