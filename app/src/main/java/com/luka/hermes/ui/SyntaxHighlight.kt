package com.luka.hermes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/**
 * Syntax highlighting for chat code blocks.
 * Two-pass approach: first isolate strings/comments, then highlight tokens.
 * One Dark Pro inspired color palette.
 */
object SyntaxHighlight {

    // ── One Dark Pro palette ─────────────────────────────────────────────
    private val KEYWORD = Color(0xFFC678DD)
    private val STRING = Color(0xFF98C379)
    private val COMMENT = Color(0xFF5C6370)
    private val NUMBER = Color(0xFFD19A66)
    private val TYPE = Color(0xFF61AFEF)
    private val ANNOTATION = Color(0xFFE5C07B)
    private val OPERATOR = Color(0xFF56B6C2)
    private val BUILTIN = Color(0xFF61AFEF)
    private val PLAIN = Color(0xFFABB2BF)

    private val STYLES = mapOf(
        "keyword" to SpanStyle(color = KEYWORD),
        "string" to SpanStyle(color = STRING),
        "comment" to SpanStyle(color = COMMENT, fontStyle = FontStyle.Italic),
        "number" to SpanStyle(color = NUMBER),
        "type" to SpanStyle(color = TYPE),
        "annotation" to SpanStyle(color = ANNOTATION),
        "operator" to SpanStyle(color = OPERATOR),
        "builtin" to SpanStyle(color = BUILTIN),
        "plain" to SpanStyle(color = PLAIN),
    )

    // ── Regexes ──────────────────────────────────────────────────────────
    private val STRING_DQ = Regex(""""(?:[^"\\]|\\.)*"""")
    private val STRING_SQ = Regex("""'(?:[^'\\]|\\.)*'""")
    private val STRING_TQ = Regex("""`(?:[^`\\]|\\.)*`""")
    private val STRING_TRIPLE = Regex("""(?s)""" + "\"\"\".*?\"\"\"" + """|'''.*?''''""")
    private val LINE_COMMENT = Regex("//.*|#.*", RegexOption.MULTILINE)
    private val BLOCK_COMMENT = Regex("""(?s)/\*.*?\*/""")
    private val HTML_COMMENT = Regex("""(?s)<!--.*?-->""")
    private val NUMBER_RE = Regex("""\b\d+(?:\.\d+)?(?:[fFLlDd]|[eE][+-]?\d+)?\b""")
    private val ANNOTATION_RE = Regex("""@[A-Za-z_]\w*""")
    private val IDENT_RE = Regex("[A-Za-z_]\\w*")

    // ── Keyword/type/builtin maps per language ───────────────────────────
    private val KEYWORDS = mapOf(
        "kotlin" to setOf("fun","val","var","if","else","when","for","while","do","return","class","interface","object","enum","sealed","data","open","abstract","override","private","public","internal","protected","companion","this","super","null","true","false","is","as","in","out","by","package","import","try","catch","finally","throw","suspend","typealias","where","init","companion","inline","noinline","crossinline","reified","operator","infix","tailrec","vararg","lateinit","const","get","set"),
        "java" to setOf("abstract","assert","boolean","break","byte","case","catch","char","class","const","continue","default","do","double","else","enum","extends","final","finally","float","for","goto","if","implements","import","instanceof","int","interface","long","native","new","package","private","protected","public","return","short","static","strictfp","super","switch","synchronized","this","throw","throws","transient","try","void","volatile","while","true","false","null","var","record","sealed","yield"),
        "javascript" to setOf("async","await","break","case","catch","class","const","continue","debugger","default","delete","do","else","export","extends","finally","for","function","if","import","in","instanceof","let","new","of","return","static","super","switch","this","throw","try","typeof","var","void","while","with","yield","true","false","null","undefined","from","as"),
        "typescript" to setOf("abstract","any","as","async","await","boolean","break","case","catch","class","const","continue","declare","default","delete","do","else","enum","export","extends","false","finally","for","from","function","if","implements","import","in","infer","instanceof","interface","is","keyof","let","module","namespace","never","new","null","of","private","protected","public","readonly","return","satisfies","static","string","super","switch","symbol","this","throw","true","try","type","typeof","undefined","unknown","var","void","while","with","yield"),
        "python" to setOf("False","None","True","and","as","assert","async","await","break","class","continue","def","del","elif","else","except","finally","for","from","global","if","import","in","is","lambda","nonlocal","not","or","pass","raise","return","self","try","while","with","yield","match","case","_"),
        "rust" to setOf("as","break","const","continue","crate","else","enum","extern","false","fn","for","if","impl","in","let","loop","match","mod","move","mut","pub","ref","return","self","Self","static","struct","super","trait","true","type","unsafe","use","where","while","async","await","dyn","abstract","become","box","do","final","macro","override","priv","try","typeof","unsized","virtual","yield"),
        "go" to setOf("break","case","chan","const","continue","default","defer","else","fallthrough","for","func","go","goto","if","import","interface","map","package","range","return","select","struct","switch","type","var","true","false","nil","iota"),
        "cpp" to setOf("alignas","alignof","auto","bool","break","case","catch","char","class","concept","const","constexpr","const_cast","continue","decltype","default","delete","do","double","dynamic_cast","else","enum","explicit","export","extern","false","float","for","friend","goto","if","inline","int","long","mutable","namespace","new","noexcept","nullptr","operator","override","private","protected","public","register","reinterpret_cast","requires","return","short","signed","sizeof","static","static_assert","static_cast","struct","switch","template","this","throw","true","try","typedef","typeid","typename","union","unsigned","using","virtual","void","volatile","while"),
        "swift" to setOf("associatedtype","async","await","break","case","catch","class","continue","default","defer","deinit","do","else","enum","extension","fallthrough","false","fileprivate","for","func","guard","if","import","in","init","inout","internal","is","let","nil","open","operator","override","private","protocol","public","repeat","rethrows","return","self","Self","static","struct","subscript","super","switch","throw","throws","true","try","typealias","var","where","while"),
        "sql" to setOf("SELECT","FROM","WHERE","AND","OR","NOT","IN","IS","NULL","LIKE","BETWEEN","EXISTS","ALL","ANY","JOIN","INNER","LEFT","RIGHT","OUTER","FULL","CROSS","ON","USING","GROUP","BY","ORDER","HAVING","LIMIT","OFFSET","UNION","INSERT","INTO","VALUES","UPDATE","SET","DELETE","CREATE","TABLE","INDEX","VIEW","DROP","ALTER","ADD","COLUMN","AS","DISTINCT","CASE","WHEN","THEN","ELSE","END","WITH","ASC","DESC","PRIMARY","KEY","FOREIGN","REFERENCES","select","from","where","and","or","not","null","join","inner","left","right","on","group","by","order","having","limit","offset","insert","into","values","update","set","delete","create","table","drop","alter","add","column","as","distinct","case","when","then","else","end","with","asc","desc","primary","key","foreign","true","false"),
        "ruby" to setOf("BEGIN","END","alias","and","begin","break","case","class","def","defined?","do","else","elsif","end","ensure","false","for","if","in","module","next","nil","not","or","redo","rescue","retry","return","self","super","then","true","undef","unless","until","when","while","yield"),
        "php" to setOf("abstract","and","array","as","break","callable","case","catch","class","clone","const","continue","declare","default","die","do","echo","else","elseif","empty","enddeclare","endfor","endforeach","endif","endswitch","endwhile","eval","exit","extends","final","finally","fn","for","foreach","function","global","goto","if","implements","include","include_once","instanceof","insteadof","interface","isset","list","match","namespace","new","or","print","private","protected","public","readonly","require","require_once","return","static","switch","throw","trait","try","unset","use","var","while","xor","yield","true","false","null","self","parent"),
        "scala" to setOf("abstract","case","catch","class","def","do","else","extends","false","final","finally","for","forSome","if","implicit","import","lazy","match","new","null","object","override","package","private","protected","return","sealed","super","this","throw","trait","true","try","type","val","var","while","with","yield"),
        "shell" to setOf("if","then","else","elif","fi","case","esac","for","in","do","done","while","until","function","return","break","continue","exit","export","local","read","echo","printf","set","unset","source","alias","true","false"),
        "yaml" to setOf("true","false","yes","no","on","off","null","True","False","Yes","No","On","Off","Null","TRUE","FALSE","YES","NO","ON","OFF"),
        "json" to setOf("true","false","null"),
    )

    private val TYPES = mapOf(
        "kotlin" to setOf("Int","Long","Short","Byte","Float","Double","Boolean","Char","String","Unit","Any","Nothing","List","Map","Set","Array","Sequence","Collection","Iterable","Pair","Triple","IntArray","BooleanArray","UByte","UInt","ULong","UShort","Result"),
        "java" to setOf("int","long","short","byte","float","double","boolean","char","String","Object","Integer","Long","Double","Float","Boolean","List","Map","Set","ArrayList","HashMap","Exception","RuntimeException","Throwable"),
        "typescript" to setOf("string","number","boolean","any","unknown","never","void","object","Array","Promise","Record","Partial","Required","Readonly","Pick","Omit","Date","Error","Map","Set"),
        "javascript" to setOf("Array","Object","Function","Promise","Date","RegExp","Map","Set","Error","Symbol","BigInt"),
        "python" to setOf("int","float","str","bool","list","dict","set","tuple","frozenset","bytes","bytearray","object","type","range","Optional","Any","Union","Callable","List","Dict","Set","Tuple"),
        "rust" to setOf("i8","i16","i32","i64","i128","isize","u8","u16","u32","u64","u128","usize","f32","f64","bool","char","str","String","Vec","Option","Result","Box","Rc","Arc","HashMap","HashSet"),
        "go" to setOf("int","int8","int16","int32","int64","uint","uint8","uint16","uint32","uint64","byte","rune","float32","float64","string","bool","error"),
        "swift" to setOf("Int","UInt","Int8","Int16","Int32","Int64","Float","Double","Bool","String","Character","Array","Dictionary","Set","Optional","Any","AnyObject","Void","Data","Date","URL"),
        "scala" to setOf("Int","Long","Short","Byte","Float","Double","Boolean","Char","String","Unit","Any","AnyVal","AnyRef","Nothing","List","Map","Set","Seq","Array","Option","Some","None","Future","Try"),
    )

    private val BUILTINS = mapOf(
        "kotlin" to setOf("println","print","readLine","listOf","setOf","mapOf","arrayOf","mutableListOf","mutableSetOf","mutableMapOf","emptyList","emptySet","emptyMap","require","check","error","TODO","let","run","also","apply","with","takeIf","takeUnless","repeat","forEach"),
        "python" to setOf("print","len","range","enumerate","zip","map","filter","reduce","sorted","reversed","sum","min","max","abs","round","isinstance","type","open","super","staticmethod","classmethod","repr","format","id","dir","help"),
        "javascript" to setOf("console","log","warn","error","Math","JSON","parseInt","parseFloat","isNaN","setTimeout","setInterval","clearTimeout","clearInterval","fetch","require","module"),
        "typescript" to setOf("console","log","warn","error","Math","JSON","parseInt","parseFloat","setTimeout","setInterval","fetch","require","module","Promise","async"),
        "rust" to setOf("println!","print!","format!","panic!","assert!","assert_eq!","vec!","matches!","unreachable!","unimplemented!","todo!","dbg!","write!","writeln!","eprintln!","eprint!","include_str!","include_bytes!","stringify!","concat!","cfg!","env!","option_env!","file!","line!","column!"),
        "go" to setOf("fmt","Println","Printf","Sprintf","Errorf","len","cap","make","new","append","copy","close","delete","panic","recover","close","complex","real","imag"),
        "swift" to setOf("print","debugPrint","dump","fatalError","precondition","preconditionFailure","assert"),
        "php" to setOf("echo","print","var_dump","print_r","json_encode","json_decode","count","array_map","array_filter","array_reduce","array_merge","array_keys","array_values","in_array","sort","rsort","explode","implode","str_replace","substr","strlen","strpos","trim","preg_match","preg_replace","file_get_contents","file_put_contents","curl_init","curl_exec","curl_close","htmlspecialchars","strip_tags"),
    )

    // ── Core highlight function ──────────────────────────────────────────

    /**
     * Syntax-highlight a code string for the given language.
     * Uses a two-pass approach: strings/comments first, then token highlighting.
     * Falls back to plain text for unknown languages.
     */
    fun highlightSyntax(code: String, language: String?): AnnotatedString {
        val lang = normalizeLanguage(language ?: "")
        val keywords = KEYWORDS[lang] ?: emptySet()
        val types = TYPES[lang] ?: emptySet()
        val builtins = BUILTINS[lang] ?: emptySet()
        val isStringLang = lang in setOf("html", "xml", "markdown", "css", "json", "yaml")

        return buildAnnotatedString {
            // Pass 1: extract strings and comments into tagged segments
            val segments = tokenize(code, lang)

            for (seg in segments) {
                when (seg.type) {
                    SegmentType.STRING -> withStyle(STYLES["string"]!!) { append(seg.text) }
                    SegmentType.COMMENT -> withStyle(STYLES["comment"]!!) { append(seg.text) }
                    SegmentType.CODE -> highlightCodeTokens(seg.text, keywords, types, builtins, isStringLang)
                }
            }
        }
    }

    // ── Tokenizer ────────────────────────────────────────────────────────

    private enum class SegmentType { STRING, COMMENT, CODE }

    private data class Segment(val type: SegmentType, val text: String)

    private fun tokenize(code: String, lang: String): List<Segment> {
        // Build combined delimiter regex for strings and comments
        val patterns = mutableListOf<Pair<Regex, SegmentType>>()

        when (lang) {
            "kotlin", "java", "scala", "swift", "cpp" -> {
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(BLOCK_COMMENT to SegmentType.COMMENT)
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
            }
            "python" -> {
                patterns.add(STRING_TRIPLE to SegmentType.STRING)
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(STRING_SQ to SegmentType.STRING)
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
            }
            "javascript", "typescript" -> {
                patterns.add(STRING_TQ to SegmentType.STRING)
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(STRING_SQ to SegmentType.STRING)
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
                patterns.add(BLOCK_COMMENT to SegmentType.COMMENT)
            }
            "go", "rust" -> {
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(STRING_TQ to SegmentType.STRING)
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
                patterns.add(BLOCK_COMMENT to SegmentType.COMMENT)
            }
            "ruby" -> {
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(STRING_SQ to SegmentType.STRING)
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
                patterns.add(BLOCK_COMMENT to SegmentType.COMMENT)
            }
            "php" -> {
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(STRING_SQ to SegmentType.STRING)
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
                patterns.add(BLOCK_COMMENT to SegmentType.COMMENT)
            }
            "shell" -> {
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(STRING_SQ to SegmentType.STRING)
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
            }
            "sql" -> {
                patterns.add(STRING_SQ to SegmentType.STRING)
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
            }
            "html", "xml" -> {
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(STRING_SQ to SegmentType.STRING)
                patterns.add(HTML_COMMENT to SegmentType.COMMENT)
            }
            "yaml" -> {
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
            }
            "css" -> {
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(BLOCK_COMMENT to SegmentType.COMMENT)
            }
            else -> {
                // Default: generic string and comment detection
                patterns.add(STRING_DQ to SegmentType.STRING)
                patterns.add(STRING_SQ to SegmentType.STRING)
                patterns.add(LINE_COMMENT to SegmentType.COMMENT)
                patterns.add(BLOCK_COMMENT to SegmentType.COMMENT)
            }
        }

        if (patterns.isEmpty()) {
            return listOf(Segment(SegmentType.CODE, code))
        }

        val combined = Regex(patterns.joinToString("|") { "(?:${it.first.pattern})" })

        val result = mutableListOf<Segment>()
        var pos = 0

        while (pos < code.length) {
            val match = combined.find(code, pos)
            if (match == null) {
                result.add(Segment(SegmentType.CODE, code.substring(pos)))
                break
            }
            if (match.range.first > pos) {
                result.add(Segment(SegmentType.CODE, code.substring(pos, match.range.first)))
            }
            // Determine type
            val matched = match.value
            val type = patterns.firstNotNullOfOrNull { (re, t) ->
                if (re.matches(matched)) t else null
            } ?: SegmentType.CODE
            result.add(Segment(type, matched))
            pos = match.range.last + 1
        }

        return result
    }

    // ── Token highlighting ──────────────────────────────────────────────

    private fun AnnotatedString.Builder.highlightCodeTokens(
        text: String,
        keywords: Set<String>,
        types: Set<String>,
        builtins: Set<String>,
        isStringLang: Boolean,
    ) {
        if (text.isEmpty()) return

        // For string-oriented languages (html, xml, markdown), use specialized highlighting
        if (isStringLang) {
            highlightStringLang(text)
            return
        }

        var pos = 0
        while (pos < text.length) {
            val c = text[pos]

            // Annotation: @something
            if (c == '@') {
                val m = ANNOTATION_RE.find(text, pos)
                if (m != null && m.range.first == pos) {
                    withStyle(STYLES["annotation"]!!) { append(m.value) }
                    pos = m.range.last + 1
                    continue
                }
            }

            // Number
            if (c.isDigit() || (c == '.' && pos + 1 < text.length && text[pos + 1].isDigit())) {
                val m = NUMBER_RE.find(text, pos)
                if (m != null && m.range.first == pos) {
                    withStyle(STYLES["number"]!!) { append(m.value) }
                    pos = m.range.last + 1
                    continue
                }
            }

            // Identifier (keyword, type, builtin, or plain)
            if (c == '_' || c.isLetter()) {
                val m = IDENT_RE.find(text, pos)
                if (m != null && m.range.first == pos) {
                    val word = m.value
                    val style = when {
                        word in keywords -> STYLES["keyword"]!!
                        word in types -> STYLES["type"]!!
                        word in builtins -> STYLES["builtin"]!!
                        word[0].isUpperCase() -> STYLES["type"]!!  // PascalCase → type
                        else -> STYLES["plain"]!!
                    }
                    withStyle(style) { append(word) }
                    pos = m.range.last + 1
                    continue
                }
            }

            // Operator
            if (c in "+-*/%=<>!&|^~?:") {
                val m = OPERATOR_RE.find(text, pos)
                if (m != null && m.range.first == pos) {
                    withStyle(STYLES["operator"]!!) { append(m.value) }
                    pos = m.range.last + 1
                    continue
                }
            }

            // Everything else: plain
            append(c)
            pos++
        }
    }

    private fun AnnotatedString.Builder.highlightStringLang(text: String) {
        var pos = 0
        while (pos < text.length) {
            val c = text[pos]

            // HTML/XML tags
            if (c == '<') {
                val tagMatch = Regex("</?[A-Za-z][A-Za-z0-9-]*(?:\\s[^>]*)?>").find(text, pos)
                if (tagMatch != null && tagMatch.range.first == pos) {
                    val tag = tagMatch.value
                    // Extract tag name
                    val nameMatch = Regex("[A-Za-z][A-Za-z0-9-]*").find(tag)
                    if (nameMatch != null) {
                        // Opening bracket
                        append("<")
                        // Tag name
                        withStyle(STYLES["keyword"]!!) { append(nameMatch.value) }
                        // Attributes
                        var attrPos = tag.indexOf(nameMatch.value) + nameMatch.value.length
                        while (attrPos < tag.length - 1) {
                            val attr = Regex("""[A-Za-z_:][A-Za-z0-9_.:-]*""").find(tag, attrPos)
                            if (attr != null && attr.range.first == attrPos) {
                                withStyle(STYLES["annotation"]!!) { append(attr.value) }
                                attrPos = attr.range.last + 1
                                // Equals sign + value
                                if (attrPos < tag.length && tag[attrPos] == '=') {
                                    withStyle(STYLES["operator"]!!) { append("=") }
                                    attrPos++
                                    val valMatch = Regex(""""[^"]*"|'[^']*'""").find(tag, attrPos)
                                    if (valMatch != null) {
                                        withStyle(STYLES["string"]!!) { append(valMatch.value) }
                                        attrPos = valMatch.range.last + 1
                                    }
                                }
                            } else {
                                append(tag[attrPos])
                                attrPos++
                            }
                        }
                        // Closing bracket
                        if (tag.endsWith("/>")) append("/>")
                        else if (tag.endsWith(">")) append(">")
                    } else {
                        append(tag)
                    }
                    pos = tagMatch.range.last + 1
                    continue
                }
            }

            // CSS specific
            if (c == '.') {
                val clsMatch = Regex("""\.[A-Za-z_][A-Za-z0-9_-]*""").find(text, pos)
                if (clsMatch != null && clsMatch.range.first == pos) {
                    withStyle(STYLES["type"]!!) { append(clsMatch.value) }
                    pos = clsMatch.range.last + 1
                    continue
                }
            }
            if (c == '#') {
                val idMatch = Regex("""#[A-Za-z_][A-Za-z0-9_-]*""").find(text, pos)
                if (idMatch != null && idMatch.range.first == pos) {
                    withStyle(STYLES["annotation"]!!) { append(idMatch.value) }
                    pos = idMatch.range.last + 1
                    continue
                }
            }

            // Property in CSS
            if (c.isLetter()) {
                val propMatch = Regex("""[A-Za-z-]+(?=\s*:)""").find(text, pos)
                if (propMatch != null && propMatch.range.first == pos) {
                    withStyle(STYLES["builtin"]!!) { append(propMatch.value) }
                    pos = propMatch.range.last + 1
                    continue
                }
            }

            // YAML keys
            val yamlKey = Regex("""[A-Za-z_][A-Za-z0-9_]*:(?=\s|$)""").find(text, pos)
            if (yamlKey != null && yamlKey.range.first == pos) {
                val keyStr = yamlKey.value.removeSuffix(":")
                withStyle(STYLES["annotation"]!!) { append(keyStr) }
                append(":")
                pos = yamlKey.range.last + 1
                continue
            }

            append(c)
            pos++
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private val OPERATOR_RE = Regex("[+\\-*/%=<>!&|^~?:]+")

    private fun normalizeLanguage(lang: String): String {
        val lower = lang.lowercase().trim()
        return when {
            lower in listOf("kt", "kotlin", "kts") -> "kotlin"
            lower in listOf("py", "python") -> "python"
            lower in listOf("js", "javascript", "ecmascript", "node", "jsx") -> "javascript"
            lower in listOf("ts", "typescript", "tsx") -> "typescript"
            lower in listOf("java") -> "java"
            lower in listOf("rs", "rust") -> "rust"
            lower in listOf("go", "golang") -> "go"
            lower in listOf("c", "h") -> "c"
            lower in listOf("cpp", "c++", "cc", "cxx", "hpp", "h++") -> "cpp"
            lower in listOf("swift") -> "swift"
            lower in listOf("sql") -> "sql"
            lower in listOf("rb", "ruby") -> "ruby"
            lower in listOf("php") -> "php"
            lower in listOf("scala") -> "scala"
            lower in listOf("sh", "bash", "zsh", "shell", "fish") -> "shell"
            lower in listOf("html", "htm", "xhtml") -> "html"
            lower in listOf("xml", "svg", "plist") -> "xml"
            lower in listOf("json") -> "json"
            lower in listOf("yaml", "yml") -> "yaml"
            lower in listOf("css", "scss", "sass", "less") -> "css"
            lower in listOf("md", "markdown") -> "markdown"
            else -> lang.lowercase()
        }
    }
}
