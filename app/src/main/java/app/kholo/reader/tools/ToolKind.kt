package app.kholo.reader.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kholo.reader.R

enum class ToolKind(@param:StringRes val title: Int, @param:StringRes val hint: Int, @param:DrawableRes val icon: Int) {
    Photos(R.string.tool_photos, R.string.tool_photos_hint, R.drawable.ic_image),
    Scan(R.string.tool_scan, R.string.tool_scan_hint, R.drawable.ic_camera),
    Merge(R.string.tool_merge, R.string.tool_merge_hint, R.drawable.ic_merge),
    Split(R.string.tool_split, R.string.tool_split_hint, R.drawable.ic_cut),
    Organize(R.string.tool_organize, R.string.tool_organize_hint, R.drawable.ic_swap),
    Compress(R.string.tool_compress, R.string.tool_compress_hint, R.drawable.ic_compress),
    Lock(R.string.tool_lock, R.string.tool_lock_hint, R.drawable.ic_lock),
    Unlock(R.string.tool_unlock, R.string.tool_unlock_hint, R.drawable.ic_lock_open),
    Text(R.string.tool_text, R.string.tool_text_hint, R.drawable.ic_description),
    ;

    val multipleInputs: Boolean get() = this == Photos || this == Scan || this == Merge
    val takesImages: Boolean get() = this == Photos || this == Scan
}

private const val BYTES_100_KB = 100L * 1024
private const val BYTES_200_KB = 200L * 1024
private const val BYTES_500_KB = 500L * 1024
private const val BYTES_1_MB = 1024L * 1024

enum class CompressTarget(val bytes: Long, @param:StringRes val label: Int) {
    Smallest(0L, R.string.compress_smallest),
    Kb100(BYTES_100_KB, R.string.compress_100),
    Kb200(BYTES_200_KB, R.string.compress_200),
    Kb500(BYTES_500_KB, R.string.compress_500),
    Mb1(BYTES_1_MB, R.string.compress_1000),
}
