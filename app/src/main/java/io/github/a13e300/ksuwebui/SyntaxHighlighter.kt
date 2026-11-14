package io.github.a13e300.ksuwebui

import java.util.regex.Pattern

enum class Language {
    PLAIN,
    HTML,
    CSS,
    JS,
    JSON;

    companion object {
        fun forName(name: String): Language = when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm", "xml", "svg" -> HTML
            "css" -> CSS
            "js", "mjs", "cjs", "ts" -> JS
            "json" -> JSON
            else -> PLAIN
        }
    }
}

enum class TokenKind {
    COMMENT,
    STRING,
    KEYWORD,
    NUMBER,
    TAG,
    ATTRIBUTE,
    PROPERTY
}

class Token(val start: Int, val end: Int, val kind: TokenKind)

object SyntaxHighlighter {
    private val jsPattern = Pattern.compile(
        """(?<comment>//[^\n]*|/\*.*?\*/)|(?<string>"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)|(?<number>\b(?:0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\b)|(?<keyword>\b(?:var|let|const|function|return|if|else|for|while|do|switch|case|break|continue|new|delete|typeof|instanceof|in|of|this|class|extends|super|import|export|from|default|async|await|try|catch|finally|throw|null|undefined|true|false|void|yield|static)\b)""",
        Pattern.DOTALL
    )
    private val jsGroups = listOf(
        "comment" to TokenKind.COMMENT,
        "string" to TokenKind.STRING,
        "number" to TokenKind.NUMBER,
        "keyword" to TokenKind.KEYWORD
    )

    private val cssPattern = Pattern.compile(
        """(?<comment>/\*.*?\*/)|(?<string>"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')|(?<atrule>@[a-zA-Z-]+)|(?<number>#[0-9a-fA-F]{3,8}\b|-?\b\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|vmin|vmax|ms|s|deg|fr|ch|pt|cm|mm)?)|(?<property>[a-zA-Z-]+(?=\s*:))""",
        Pattern.DOTALL
    )
    private val cssGroups = listOf(
        "comment" to TokenKind.COMMENT,
        "string" to TokenKind.STRING,
        "atrule" to TokenKind.KEYWORD,
        "number" to TokenKind.NUMBER,
        "property" to TokenKind.PROPERTY
    )

    private val jsonPattern = Pattern.compile(
        """(?<property>"(?:\\.|[^"\\\n])*"(?=\s*:))|(?<string>"(?:\\.|[^"\\\n])*")|(?<number>-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b)|(?<keyword>\b(?:true|false|null)\b)"""
    )
    private val jsonGroups = listOf(
        "property" to TokenKind.PROPERTY,
        "string" to TokenKind.STRING,
        "number" to TokenKind.NUMBER,
        "keyword" to TokenKind.KEYWORD
    )

    fun tokenize(text: String, language: Language): List<Token> {
        val out = ArrayList<Token>()
        tokenizeInto(text, language, 0, out)
        return out
    }

    private fun tokenizeInto(text: String, language: Language, offset: Int, out: MutableList<Token>) {
        when (language) {
            Language.JS -> scan(text, jsPattern, jsGroups, offset, out)
            Language.CSS -> scan(text, cssPattern, cssGroups, offset, out)
            Language.JSON -> scan(text, jsonPattern, jsonGroups, offset, out)
            Language.HTML -> html(text, offset, out)
            Language.PLAIN -> Unit
        }
    }

    private fun scan(
        text: String,
        pattern: Pattern,
        groups: List<Pair<String, TokenKind>>,
        offset: Int,
        out: MutableList<Token>
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            for ((name, kind) in groups) {
                if (matcher.start(name) >= 0) {
                    out.add(Token(offset + matcher.start(), offset + matcher.end(), kind))
                    break
                }
            }
        }
    }

    private fun html(text: String, offset: Int, out: MutableList<Token>) {
        val n = text.length
        var i = 0
        while (i < n) {
            val lt = text.indexOf('<', i)
            if (lt < 0 || lt + 1 >= n) break
            if (text.startsWith("<!--", lt)) {
                val close = text.indexOf("-->", lt + 4)
                val end = if (close < 0) n else close + 3
                out.add(Token(offset + lt, offset + end, TokenKind.COMMENT))
                i = end
                continue
            }
            val next = text[lt + 1]
            if (!(next.isLetter() || next == '/' || next == '!' || next == '?')) {
                i = lt + 1
                continue
            }
            var j = lt + 1
            if (next == '/' || next == '!' || next == '?') j++
            val nameStart = j
            while (j < n && (text[j].isLetterOrDigit() || text[j] == '-' || text[j] == ':' || text[j] == '_')) j++
            val name = text.substring(nameStart, j).lowercase()
            val closing = next == '/'
            out.add(Token(offset + lt, offset + j, TokenKind.TAG))
            while (j < n && text[j] != '>') {
                val c = text[j]
                if (c == '"' || c == '\'') {
                    val close = text.indexOf(c, j + 1)
                    val end = if (close < 0) n else close + 1
                    out.add(Token(offset + j, offset + end, TokenKind.STRING))
                    j = end
                } else if (c.isLetter() || c == '_' || c == ':' || c == '@') {
                    val start = j
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '-' || text[j] == '_' || text[j] == ':' || text[j] == '.' || text[j] == '@')) j++
                    out.add(Token(offset + start, offset + j, TokenKind.ATTRIBUTE))
                } else {
                    j++
                }
            }
            var selfClosing = false
            if (j < n) {
                selfClosing = j > 0 && text[j - 1] == '/'
                out.add(Token(offset + j, offset + j + 1, TokenKind.TAG))
                j++
            }
            i = j
            if (!closing && !selfClosing && (name == "script" || name == "style")) {
                val close = text.indexOf("</$name", i, ignoreCase = true)
                val end = if (close < 0) n else close
                if (end > i) {
                    val inner = if (name == "script") Language.JS else Language.CSS
                    tokenizeInto(text.substring(i, end), inner, offset + i, out)
                }
                i = end
            }
        }
    }
}
