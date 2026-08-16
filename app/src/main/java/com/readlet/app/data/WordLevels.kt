package com.readlet.app.data

import android.content.Context
import java.io.IOException

/**
 * 重点词级别表：词 → (级别, 音标, 释义)。
 * 数据来自 `assets/word_levels.tsv`（80,440 词：word<TAB>级别<TAB>英标<TAB>美标<TAB>释义<TAB>四级标志，
 * 源：本地柯林斯缓存 kd_data.db（英/美双音标）+ 开源考研/雅思词表）。
 * 启动时加载为 HashMap，查词 O(1)；查词带变形还原回退（sweeping→sweep、trotting→trot、cries→cry）。
 * 变形还原两层：规则变形由 [forms] 推导（-s/-es/-ies/-ed/-ing，含双写去一），
 * 不规则变形查 [IRREGULAR] 映射表（went→go、knelt→kneel、mice→mouse）——
 * 等价于 GoldenDict 的 Hunspell 形态层：标准 en_US 词典把不规则变形列为独立无标记词条，
 * 规则引擎推导不出 knelt→kneel，此处用确定性映射覆盖常见不规则动词/复数/比较级。
 */
class WordLevels internal constructor(
    private val map: Map<String, Entry>,
) {
    /** 词条：级别（六级/考研/雅思/专四/专八）+ 英/美音标（可空）+ 释义（可空）+ 是否四级基础词。 */
    data class Entry(
        val level: String,
        val phonetic: String,
        val meaning: String,
        val phoneticUs: String = "",
        val base: Boolean = false,
    )

    /** 查词（含规则/不规则变形还原回退）；未命中返回 null。 */
    fun lookup(token: String): Entry? {
        val w = token.trim().lowercase()
        if (w.isEmpty()) return null
        map[w]?.let { return it }
        for (baseForm in forms(w)) {
            map[baseForm]?.let { return it }
        }
        IRREGULAR[w]?.let { base ->
            map[base]?.let { return it }
        }
        return null
    }

    /**
     * 词的原形：`loomed` → `loom`、`cries` → `cry`、`went` → `go`、`knelt` → `kneel`。
     * 词表直接收录的变形词条同样参与推导（`crouched` → `crouch`）。
     * 无消歧，按最常见原型（found→find、wound→wind）；未命中返回 null。
     */
    fun lemma(token: String): String? {
        val w = token.trim().lowercase()
        if (w.isEmpty()) return null
        IRREGULAR[w]?.let { return it }
        for (baseForm in forms(w)) {
            if (map.containsKey(baseForm)) return baseForm
        }
        return null
    }

    /**
     * 音标查询：词形本身的音标为空时继续查变形原形（柯林斯缓存变形词条音标常为空，
     * 如 crouched 只有级别与释义，音标在 crouch 上），命中任一非空即返回。
     * [lookup] 的补充：lookup 命中即停，音标兜底需要跳过空音标条目。
     */
    fun phoneticOf(token: String): String? {
        val w = token.trim().lowercase()
        if (w.isEmpty()) return null
        map[w]?.phonetic?.takeIf { it.isNotBlank() }?.let { return it }
        for (baseForm in forms(w)) {
            map[baseForm]?.phonetic?.takeIf { it.isNotBlank() }?.let { return it }
        }
        IRREGULAR[w]?.let { base ->
            map[base]?.phonetic?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    /** 美式音标查询，规则同 [phoneticOf]（直接词条 → 规则变形原形 → 不规则原形）。 */
    fun phoneticUsOf(token: String): String? {
        val w = token.trim().lowercase()
        if (w.isEmpty()) return null
        map[w]?.phoneticUs?.takeIf { it.isNotBlank() }?.let { return it }
        for (baseForm in forms(w)) {
            map[baseForm]?.phoneticUs?.takeIf { it.isNotBlank() }?.let { return it }
        }
        IRREGULAR[w]?.let { base ->
            map[base]?.phoneticUs?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    companion object {
        /** 从资产加载（进程启动一次）。资产缺失时返回空表（不应发生）。 */
        fun load(context: Context): WordLevels {
            val map = HashMap<String, Entry>()
            try {
                context.assets.open("word_levels.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        val p = line.split('\t')
                        if (p.size >= 2 && p[0].isNotEmpty()) {
                            map[p[0]] = Entry(
                                level = p[1],
                                phonetic = normalizeIpa(p.getOrElse(2) { "" }),
                                phoneticUs = normalizeIpa(p.getOrElse(3) { "" }),
                                meaning = p.getOrElse(4) { "" },
                                base = p.getOrElse(5) { "0" } == "1",
                            )
                        }
                    }
                }
            } catch (_: IOException) {
                // 资产缺失：级别标记静默失效，不影响其余功能
            }
            return WordLevels(map)
        }

        /** 柯林斯方括号音标 [luːm] 归一为 /luːm/，与 LLM 输出格式一致。 */
        private fun normalizeIpa(raw: String): String {
            val t = raw.trim()
            return if (t.startsWith("[") && t.endsWith("]") && t.length > 2)
                "/${t.substring(1, t.length - 1).trim()}/"
            else t
        }

        /**
         * 不规则变形 → 原形（Hunspell 形态层的等价物）。标准 en_US 词典把不规则变形
         * （knelt/went/mice）列为独立无标记词条，规则引擎推导不出原形，只能靠映射表。
         * 只收录「变形 → 原形」单向映射；无消歧，按最常见原型（found→find、wound→wind）。
         */
        private val IRREGULAR: Map<String, String> = mapOf(
            // be / have / do
            "am" to "be", "is" to "be", "are" to "be", "was" to "be", "were" to "be", "been" to "be",
            "has" to "have", "had" to "have",
            "does" to "do", "did" to "do", "done" to "do",
            // 不规则动词：过去式 / 过去分词
            "went" to "go", "gone" to "go", "came" to "come", "became" to "become",
            "ran" to "run", "swam" to "swim", "sang" to "sing", "sung" to "sing",
            "rang" to "ring", "rung" to "ring", "began" to "begin", "begun" to "begin",
            "drank" to "drink", "drunk" to "drink", "sank" to "sink", "sunk" to "sink",
            "shrank" to "shrink", "shrunk" to "shrink", "sprang" to "spring", "sprung" to "spring",
            "stank" to "stink", "stunk" to "stink", "won" to "win", "spun" to "spin",
            "got" to "get", "gotten" to "get", "forgot" to "forget", "forgotten" to "forget",
            "shot" to "shoot", "hid" to "hide", "hidden" to "hide", "slid" to "slide",
            "sat" to "sit", "met" to "meet", "lit" to "light", "led" to "lead",
            "fed" to "feed", "fled" to "flee", "bled" to "bleed",
            "broke" to "break", "broken" to "break", "spoke" to "speak", "spoken" to "speak",
            "woke" to "wake", "woken" to "wake", "awoke" to "awake", "awoken" to "awake",
            "took" to "take", "taken" to "take", "mistaken" to "mistake",
            "shook" to "shake", "shaken" to "shake", "stole" to "steal", "stolen" to "steal",
            "drove" to "drive", "driven" to "drive", "rode" to "ride", "ridden" to "ride",
            "rose" to "rise", "risen" to "rise", "arose" to "arise", "arisen" to "arise",
            "wrote" to "write", "written" to "write", "rewrote" to "rewrite", "rewritten" to "rewrite",
            "chose" to "choose", "chosen" to "choose", "froze" to "freeze", "frozen" to "freeze",
            "gave" to "give", "given" to "give", "forgave" to "forgive", "forgiven" to "forgive",
            "ate" to "eat", "eaten" to "eat", "fell" to "fall", "fallen" to "fall",
            "beat" to "beat", "beaten" to "beat",
            "drew" to "draw", "drawn" to "draw", "flew" to "fly", "flown" to "fly",
            "grew" to "grow", "grown" to "grow", "threw" to "throw", "thrown" to "throw",
            "knew" to "know", "known" to "know", "blew" to "blow", "blown" to "blow",
            "saw" to "see", "seen" to "see", "foresaw" to "foresee", "foreseen" to "foresee",
            "wore" to "wear", "worn" to "wear", "tore" to "tear", "torn" to "tear",
            "bore" to "bear", "born" to "bear", "borne" to "bear", "swore" to "swear", "sworn" to "swear",
            "knelt" to "kneel", "laid" to "lay", "paid" to "pay", "said" to "say",
            "told" to "tell", "sold" to "sell", "held" to "hold", "beheld" to "behold",
            "withheld" to "withhold", "upheld" to "uphold", "heard" to "hear",
            "built" to "build", "felt" to "feel", "kept" to "keep", "slept" to "sleep",
            "wept" to "weep", "dealt" to "deal", "meant" to "mean",
            "left" to "leave", "lost" to "lose", "sent" to "send", "spent" to "spend",
            "found" to "find", "bound" to "bind", "wound" to "wind", "ground" to "grind",
            "hung" to "hang", "flung" to "fling", "clung" to "cling", "swung" to "swing",
            "stuck" to "stick", "struck" to "strike", "stricken" to "strike",
            "dug" to "dig", "sought" to "seek", "bought" to "buy", "brought" to "bring",
            "taught" to "teach", "caught" to "catch", "thought" to "think", "fought" to "fight",
            "bent" to "bend", "lent" to "lend", "crept" to "creep", "swept" to "sweep",
            "leapt" to "leap", "dreamt" to "dream", "burnt" to "burn", "learnt" to "learn",
            "spelt" to "spell", "spilt" to "spill", "spoilt" to "spoil", "dwelt" to "dwell",
            "smelt" to "smell", "leant" to "lean", "shone" to "shine", "sped" to "speed",
            "pled" to "plead", "slew" to "slay", "slain" to "slay", "smote" to "smite", "smitten" to "smite",
            "strode" to "stride", "stridden" to "stride", "strove" to "strive", "striven" to "strive",
            "trod" to "tread", "trodden" to "tread", "wove" to "weave", "woven" to "weave",
            "wrung" to "wring", "slung" to "sling", "strung" to "string", "stung" to "sting",
            "spat" to "spit", "forsook" to "forsake", "forsaken" to "forsake",
            "forbade" to "forbid", "forbidden" to "forbid", "misled" to "mislead",
            "proved" to "prove", "proven" to "prove", "sawed" to "saw", "sawn" to "saw",
            "sewed" to "sew", "sewn" to "sew", "showed" to "show", "shown" to "show",
            "shod" to "shoe", "sowed" to "sow", "sown" to "sow",
            "understood" to "understand", "misunderstood" to "misunderstand", "withstood" to "withstand",
            "overcame" to "overcome", "outran" to "outrun", "overran" to "overrun",
            "withdrew" to "withdraw", "withdrawn" to "withdraw", "undertook" to "undertake", "undertaken" to "undertake",
            "overtook" to "overtake", "overtaken" to "overtake", "overthrew" to "overthrow", "overthrown" to "overthrow",
            // 不规则复数
            "mice" to "mouse", "geese" to "goose", "feet" to "foot", "teeth" to "tooth",
            "men" to "man", "women" to "woman", "children" to "child", "oxen" to "ox",
            "people" to "person", "dice" to "die", "lice" to "louse",
            "indices" to "index", "appendices" to "appendix", "matrices" to "matrix",
            "vertices" to "vertex", "vortices" to "vortex", "stimuli" to "stimulus",
            "fungi" to "fungus", "cacti" to "cactus", "nuclei" to "nucleus", "radii" to "radius",
            "syllabi" to "syllabus", "termini" to "terminus", "foci" to "focus", "loci" to "locus",
            "curricula" to "curriculum", "memoranda" to "memorandum", "strata" to "stratum",
            "spectra" to "spectrum", "data" to "datum", "media" to "medium", "bacteria" to "bacterium",
            "criteria" to "criterion", "phenomena" to "phenomenon", "errata" to "erratum",
            "addenda" to "addendum", "desiderata" to "desideratum", "corrigenda" to "corrigendum",
            "analyses" to "analysis", "crises" to "crisis", "theses" to "thesis", "hypotheses" to "hypothesis",
            "axes" to "axis", "bases" to "basis", "oases" to "oasis", "diagnoses" to "diagnosis",
            "ellipses" to "ellipsis", "parentheses" to "parenthesis", "emphases" to "emphasis",
            "synopses" to "synopsis", "neuroses" to "neurosis",
            "formulae" to "formula", "larvae" to "larva", "antennae" to "antenna",
            "vertebrae" to "vertebra", "nebulae" to "nebula", "alumnae" to "alumna",
            "alumni" to "alumnus", "corpora" to "corpus", "genera" to "genus",
            "hippopotami" to "hippopotamus", "octopi" to "octopus",
            // 不规则比较级 / 最高级
            "better" to "good", "best" to "good", "worse" to "bad", "worst" to "bad",
            "more" to "much", "most" to "much", "less" to "little", "least" to "little",
            "further" to "far", "furthest" to "far", "elder" to "old", "eldest" to "old",
        )

        /**
         * 变形候选（原形优先，不含原词）：
         * - ing → strip / +e / 双写去一（trotting→trot）
         * - ed → strip / +e / 双写去一（hurtled→hurtle）
         * - ies → y（cries→cry）；es → strip / +e；s → strip
         */
        fun forms(w: String): List<String> {
            val out = ArrayList<String>(6)
            fun add(s: String) {
                if (s.length >= 2 && s != w && !out.contains(s)) out.add(s)
            }
            when {
                w.endsWith("ing") -> {
                    val b = w.dropLast(3)
                    add(b)
                    add(b + "e")
                    if (b.length >= 2 && b.last() == b[b.length - 2]) add(b.dropLast(1))
                }
                w.endsWith("ied") -> add(w.dropLast(3) + "y")
                w.endsWith("ed") -> {
                    val b = w.dropLast(2)
                    add(b)
                    add(b + "e")
                    if (b.length >= 2 && b.last() == b[b.length - 2]) add(b.dropLast(1))
                }
                w.endsWith("ies") -> add(w.dropLast(3) + "y")
                w.endsWith("es") -> {
                    add(w.dropLast(2))
                    add(w.dropLast(2) + "e")
                }
                w.endsWith("s") -> add(w.dropLast(1))
            }
            return out
        }
    }
}
