package com.jaecoo.voice

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Class helper traverse và tìm kiếm AccessibilityNodeInfo
 */
object NodeFinder {

    private const val TAG = "NodeFinder"

    /**
     * Tìm node theo text/contentDescription.
     * Thứ tự ưu tiên: text.equals -> text.contains -> contentDescription.
     * Bỏ qua các node không visible (isVisibleToUser == false).
     * Nếu node match không clickable -> trả về parent clickable gần nhất.
     */
    fun find(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null || text.isBlank()) return null

        // 1. Match exact text
        var matched = findInternal(root, text, exact = true, contains = false)
        if (matched != null) return getClickableParent(matched)

        // 2. Match contains text
        matched = findInternal(root, text, exact = false, contains = true)
        if (matched != null) return getClickableParent(matched)

        // 3. Match contentDescription
        matched = findByDesc(root, text)
        if (matched != null) return getClickableParent(matched)

        return null
    }

    private fun findInternal(
        node: AccessibilityNodeInfo,
        target: String,
        exact: Boolean,
        contains: Boolean
    ): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null

        val nodeText = node.text?.toString()
        if (!nodeText.isNullOrBlank()) {
            if (exact && nodeText.equals(target, ignoreCase = true)) {
                return node
            }
            if (contains && nodeText.contains(target, ignoreCase = true)) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findInternal(child, target, exact, contains)
            if (result != null) return result
        }
        return null
    }

    private fun findByDesc(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank() && desc.contains(target, ignoreCase = true)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findByDesc(child, target)
            if (result != null) return result
        }
        return null
    }

    private fun getClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }
        return node
    }

    /**
     * In toàn bộ cây UI vào Logcat theo format: [depth] class | text | desc | bounds | clickable
     */
    fun dumpToLog(root: AccessibilityNodeInfo?, depth: Int = 0) {
        if (root == null) {
            Log.d(TAG, "Node tree is NULL")
            return
        }
        val indent = " ".repeat(depth * 2)
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        val text = root.text ?: ""
        val desc = root.contentDescription ?: ""
        val className = root.className ?: "Unknown"
        val clickable = if (root.isClickable) "Clickable" else "NonClickable"
        val visible = if (root.isVisibleToUser) "Visible" else "Hidden"

        Log.d(TAG, "[$depth] $indent$className | text='$text' | desc='$desc' | bounds=$bounds | $clickable | $visible")

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            dumpToLog(child, depth + 1)
        }
    }
}
