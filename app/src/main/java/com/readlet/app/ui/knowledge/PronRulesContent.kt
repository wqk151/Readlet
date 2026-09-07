package com.readlet.app.ui.knowledge

/** 发音规律（英语发音音变规则速查表）：12 章自然语流音系变化。单元格 `**…**` = 原文档加粗强调。 */
val PronRulesSections = listOf(
    KbSection("一、元音间浊化（Intervocalic Voicing）", listOf(
        KbBlock.Lead("规则：清辅音夹在两个元音之间时，常浊化为对应的浊辅音。"),
        KbBlock.Table(
            head = listOf("清辅音", "示例", "说明"),
            w = listOf(1.6f, 4.6f, 3f),
            rows = listOf(
                listOf("/s/ → /z/", "**re**sign /rɪˈ**z**aɪn/", "re- + sign"),
                listOf("/s/ → /z/", "**re**sult /rɪˈ**z**ʌlt/", "re- + sult"),
                listOf("/s/ → /z/", "**re**sist /rɪˈ**z**ɪst/", "re- + sist"),
                listOf("/s/ → /z/", "**re**solve /rɪˈ**z**ɒlv/", "re- + solve"),
                listOf("/s/ → /z/", "**pre**sent (v.) /prɪˈ**z**ent/", "pre- + sent"),
                listOf("/s/ → /z/", "**de**sert (v.) /dɪˈ**z**ɜːt/", "de- + sert"),
                listOf("/t/ → /d/", "la**tt**er /ˈlæ**d**ə/", "美式英语中常见"),
                listOf("/t/ → /d/", "be**tt**er /ˈbe**d**ə/", "美式英语中常见"),
                listOf("/k/ → /g/", "ba**ck**er /ˈbæ**g**ə/", "非正式语流"),
            ),
        ),
        KbBlock.Tip("注意：并非所有元音间的清辅音都会浊化。派生词（前缀＋词根）中更常见，原生词可能保留清音，如 assign /əˈsaɪn/。"),
    )),

    KbSection("二、同化（Assimilation）", listOf(
        KbBlock.Lead("规则：相邻音素相互影响，使一个音变得与另一个音更相似。"),
        KbBlock.Sub("2.1 顺向同化（Progressive）——前一个音影响后一个音"),
        KbBlock.Table(
            head = listOf("示例", "音标变化", "说明"),
            w = listOf(1.9f, 3.4f, 4.2f),
            rows = listOf(
                listOf("**s**top", "/ˈ**s**tɒp/", "/s/ 后的 /t/ 不送气，接近 /d/"),
                listOf("**s**peak", "/ˈ**s**biːk/", "/s/ 后的 /p/ 不送气"),
                listOf("**s**chool", "/ˈ**s**kuːl/", "/s/ 后的 /k/ 不送气"),
            ),
        ),
        KbBlock.Sub("2.2 逆向同化（Regressive）——后一个音影响前一个音"),
        KbBlock.Table(
            head = listOf("示例", "音标变化", "说明"),
            w = listOf(2.6f, 4.4f, 4.2f),
            rows = listOf(
                listOf("ha**v**e to", "/hæ**f** tuː/", "/v/ 受 /t/ 影响变为 /f/"),
                listOf("u**s**e to", "/juː**s** tuː/", "/z/ 受 /t/ 影响变为 /s/"),
                listOf("fi**v**e pence", "/faɪ**f** pens/", "/v/ 受 /p/ 影响变为 /f/"),
                listOf("goo**d** night", "/ɡʊ**n** naɪt/", "/d/ 受 /n/ 影响变为 /n/"),
                listOf("ba**d** boy", "/bæ**b** bɔɪ/", "/d/ 受 /b/ 影响变为 /b/"),
                listOf("tha**t** car", "/ðæ**k** kɑː/", "/t/ 受 /k/ 影响变为 /k/"),
                listOf("i**n**complete", "/ɪ**ŋ** kəmˈpliːt/", "/n/ 受 /k/ 影响变为 /ŋ/"),
                listOf("ca**n** go", "/kæ**ŋ** ɡəʊ/", "/n/ 受 /ɡ/ 影响变为 /ŋ/"),
            ),
        ),
    )),

    KbSection("三、连读（Linking / Liaison）", listOf(
        KbBlock.Lead("规则：词尾辅音与后一词首元音连读，仿佛一个单词。"),
        KbBlock.Table(
            head = listOf("示例", "连读后音标", "说明"),
            w = listOf(2.8f, 4.2f, 4.4f),
            rows = listOf(
                listOf("pick **up**", "/ˈpɪ**kʌ**p/", "词尾 /k/ + 词首 /ʌ/"),
                listOf("turn **on**", "/ˈtɜː**nɒ**n/", "词尾 /n/ + 词首 /ɒ/"),
                listOf("an **apple**", "/ə**næ**pəl/", "词尾 /n/ + 词首 /æ/"),
                listOf("not **at** all", "/nɒ**tæ**tɔːl/", "词尾 /t/ + 词首 /æ/"),
                listOf("law **and** order", "/lɔː**ræ**ndɔːdə/", "词尾 /ɔː/ + 词首 /æ/（r 音连读）"),
            ),
        ),
    )),

    KbSection("四、省音（Elision / Deletion）", listOf(
        KbBlock.Lead("规则：语流中某些音素被省略，使发音更流畅。"),
        KbBlock.Sub("4.1 辅音省略"),
        KbBlock.Table(
            head = listOf("示例", "省音后", "说明"),
            w = listOf(3.2f, 4.6f, 2.6f),
            rows = listOf(
                listOf("nex**t** day", "/ˈneks deɪ/", "/t/ 省略"),
                listOf("las**t** night", "/ˈlɑːs naɪt/", "/t/ 省略"),
                listOf("frien**d**ship", "/ˈfrenʃɪp/", "/d/ 省略"),
                listOf("han**ds**ome", "/ˈhænsəm/", "/d/ 省略"),
                listOf("chris**t**mas", "/ˈkrɪsməs/", "/t/ 省略"),
                listOf("san**d**wich", "/ˈsænwɪdʒ/", "/d/ 省略"),
                listOf("go**v**ernment", "/ˈɡʌvənmənt/ 或 /ˈɡʌnmənt/", "/v/ 或 /ə/ 省略"),
                listOf("pro**b**ably", "/ˈprɒbli/ 或 /ˈprɒli/", "/b/ 或 /ə/ 省略"),
            ),
        ),
        KbBlock.Sub("4.2 元音省略"),
        KbBlock.Table(
            head = listOf("示例", "省音后", "说明"),
            w = listOf(3.2f, 4.4f, 2.6f),
            rows = listOf(
                listOf("int**e**resting", "/ˈɪntrəstɪŋ/", "/e/ 弱化为 /ə/ 或省略"),
                listOf("ev**e**ry", "/ˈevri/", "/e/ 省略"),
                listOf("diff**e**rent", "/ˈdɪfrənt/", "/e/ 省略"),
                listOf("fav**o**rite", "/ˈfeɪvrɪt/", "/ə/ 省略"),
                listOf("choc**o**late", "/ˈtʃɒklət/", "/ə/ 省略"),
            ),
        ),
    )),

    KbSection("五、弱读（Reduction）", listOf(
        KbBlock.Lead("规则：功能词（虚词）在句中非重读时，元音弱化为 /ə/ 或 /ɪ/。"),
        KbBlock.Table(
            head = listOf("词", "强读", "弱读", "示例"),
            w = listOf(1f, 1.9f, 1.9f, 4.2f),
            rows = listOf(
                listOf("a", "/eɪ/", "/ə/", "I have **a** book /ə bʊk/"),
                listOf("an", "/æn/", "/ən/", "**an** apple /ən æpəl/"),
                listOf("the", "/ðiː/", "/ðə/ 或 /ði/", "**the** book /ðə bʊk/"),
                listOf("to", "/tuː/", "/tə/", "go **to** school /ɡəʊ tə skuːl/"),
                listOf("for", "/fɔː/", "/fə/", "**for** you /fə juː/"),
                listOf("of", "/ɒv/", "/əv/", "a cup **of** tea /ə kʌp əv tiː/"),
                listOf("and", "/ænd/", "/ənd/ 或 /ən/", "bread **and** butter /bred ən bʌtə/"),
                listOf("can", "/kæn/", "/kən/", "I **can** go /aɪ kən ɡəʊ/"),
                listOf("have", "/hæv/", "/həv/ 或 /əv/", "I **have** seen /aɪ əv siːn/"),
                listOf("are", "/ɑː/", "/ə/", "you **are** right /ju ə raɪt/"),
                listOf("was", "/wɒz/", "/wəz/", "he **was** here /hi wəz hɪə/"),
                listOf("does", "/dʌz/", "/dəz/", "**does** he? /dəz hiː/"),
                listOf("at", "/æt/", "/ət/", "**at** home /ət həʊm/"),
                listOf("from", "/frɒm/", "/frəm/", "**from** here /frəm hɪə/"),
                listOf("some", "/sʌm/", "/səm/", "**some** water /səm ˈwɔːtə/"),
            ),
        ),
    )),

    KbSection("六、缩读（Contraction）", listOf(
        KbBlock.Lead("规则：常见缩略形式及其发音。"),
        KbBlock.Table(
            head = listOf("完整形式", "缩略形式", "发音", "示例"),
            w = listOf(2.1f, 1.6f, 2f, 3.4f),
            rows = listOf(
                listOf("I am", "I'm", "/aɪm/", "I'm fine."),
                listOf("you are", "you're", "/jɔː/ 或 /jʊə/", "You're right."),
                listOf("he is", "he's", "/hiːz/", "He's here."),
                listOf("she is", "she's", "/ʃiːz/", "She's nice."),
                listOf("it is", "it's", "/ɪts/", "It's cold."),
                listOf("they are", "they're", "/ðeə/", "They're happy."),
                listOf("we are", "we're", "/wɪə/", "We're ready."),
                listOf("I have", "I've", "/aɪv/", "I've done it."),
                listOf("you have", "you've", "/juːv/", "You've won."),
                listOf("would have", "would've", "/ˈwʊdəv/", "I would've come."),
                listOf("could have", "could've", "/ˈkʊdəv/", "I could've helped."),
                listOf("should have", "should've", "/ˈʃʊdəv/", "I should've known."),
                listOf("is not", "isn't", "/ˈɪzənt/", "He isn't here."),
                listOf("are not", "aren't", "/ɑːnt/", "We aren't ready."),
                listOf("do not", "don't", "/dəʊnt/", "I don't know."),
                listOf("does not", "doesn't", "/ˈdʌzənt/", "He doesn't care."),
                listOf("did not", "didn't", "/ˈdɪdənt/", "I didn't see."),
                listOf("cannot", "can't", "/kɑːnt/", "I can't go."),
                listOf("will not", "won't", "/wəʊnt/", "I won't leave."),
                listOf("would not", "wouldn't", "/ˈwʊdənt/", "I wouldn't mind."),
                listOf("could not", "couldn't", "/ˈkʊdənt/", "I couldn't hear."),
                listOf("should not", "shouldn't", "/ˈʃʊdənt/", "You shouldn't worry."),
                listOf("must not", "mustn't", "/ˈmʌsənt/", "You mustn't tell."),
                listOf("let us", "let's", "/lets/", "Let's go."),
                listOf("that is", "that's", "/ðæts/", "That's true."),
                listOf("there is", "there's", "/ðeəz/", "There's time."),
                listOf("there are", "there're", "/ðeərə/", "There're many."),
                listOf("here is", "here's", "/hɪəz/", "Here's your key."),
                listOf("how is", "how's", "/haʊz/", "How's it going?"),
                listOf("what is", "what's", "/wɒts/", "What's up?"),
                listOf("where is", "where's", "/weəz/", "Where's my bag?"),
                listOf("who is", "who's", "/huːz/", "Who's coming?"),
                listOf("why is", "why's", "/waɪz/", "Why's he late?"),
            ),
        ),
    )),

    KbSection("七、送气与不送气（Aspiration）", listOf(
        KbBlock.Lead("规则：/p, t, k/ 在词首或重读音节开头时送气，在 /s/ 后或词尾时不送气。"),
        KbBlock.Table(
            head = listOf("送气情况", "示例", "音标", "说明"),
            w = listOf(1.8f, 1.6f, 2.4f, 3f),
            rows = listOf(
                listOf("✅ 送气", "**p**en", "/ˈ**pʰ**en/", "词首 /p/"),
                listOf("✅ 送气", "**t**op", "/ˈ**tʰ**ɒp/", "词首 /t/"),
                listOf("✅ 送气", "**k**ey", "/ˈ**kʰ**iː/", "词首 /k/"),
                listOf("❌ 不送气", "s**t**op", "/ˈs**t**ɒp/", "/s/ 后的 /t/"),
                listOf("❌ 不送气", "s**p**eak", "/ˈs**p**iːk/", "/s/ 后的 /p/"),
                listOf("❌ 不送气", "s**k**ool", "/ˈs**k**uːl/", "/s/ 后的 /k/"),
                listOf("❌ 不送气", "ca**t**", "/kæ**t**/", "词尾 /t/"),
                listOf("❌ 不送气", "ba**ck**", "/bæ**k**/", "词尾 /k/"),
            ),
        ),
        KbBlock.Tip("💡 中文母语者常把不送气的 /p, t, k/ 听成 /b, d, g/，因为中文拼音的 b, d, g 就是不送气的。"),
    )),

    KbSection("八、/t/ 的音变（T-allophones）", listOf(
        KbBlock.Lead("规则：/t/ 在不同语境下有多种变体。"),
        KbBlock.Table(
            head = listOf("变体", "条件", "示例", "说明"),
            w = listOf(2.2f, 2.6f, 3.2f, 2.2f),
            rows = listOf(
                listOf("送气 [tʰ]", "词首/重读音节首", "**t**ime /ˈ**tʰ**aɪm/", "标准发音"),
                listOf("不送气 [t]", "/s/ 后", "s**t**op /ˈs**t**ɒp/", "接近中文「的」"),
                listOf("闪音 [ɾ]", "元音间（美式）", "be**tt**er /ˈbe**ɾ**ə/", "美式英语特征"),
                listOf("闪音 [ɾ]", "元音间（美式）", "ci**t**y /ˈsɪ**ɾ**i/", "美式英语特征"),
                listOf("喉塞音 [ʔ]", "元音前/词尾（英式）", "bu**tt**on /ˈbʌ**ʔ**ən/", "英式 Cockney 等"),
                listOf("喉塞音 [ʔ]", "词尾", "ca**t** /kæ**ʔ**/", "非正式语流"),
                listOf("省略 Ø", "辅音丛", "nex**t** day /ˈneks deɪ/", "快速语流"),
                listOf("浊化 [d]", "元音间（非正式）", "la**tt**er /ˈlæ**d**ə/", "见规则一"),
            ),
        ),
    )),

    KbSection("九、/r/ 音（Rhotic vs. Non-rhotic）", listOf(
        KbBlock.Table(
            head = listOf("类型", "规则", "示例"),
            w = listOf(3f, 4.4f, 3.4f),
            rows = listOf(
                listOf("**R 音方言**（美式等）", "所有位置的 /r/ 都发音", "car /kɑːr/（元音后 /r/ 发音）"),
                listOf("**非 R 音方言**（英式等）", "元音后 /r/ 不发音", "car /kɑː/（元音后 /r/ 省略）"),
                listOf("**连读 R**（非 R 音）", "元音后省略的 /r/ 在元音前恢复", "law and order /lɔː**r**ændɔːdə/（见规则三）"),
                listOf("**插入 R**（非 R 音）", "元音间插入 /r/ 防止元音冲突", "idea of /aɪˈdɪə**r**əv/（非正式语流）"),
            ),
        ),
    )),

    KbSection("十、其他常见音变", listOf(
        KbBlock.Sub("10.1 /h/ 省略"),
        KbBlock.Table(
            head = listOf("词", "弱读形式"),
            w = listOf(1.2f, 5.6f),
            rows = listOf(
                listOf("he", "/iː/（句中非重读时）"),
                listOf("her", "/ɜː/ 或 /ə/（弱读时）"),
                listOf("him", "/ɪm/（弱读时）"),
                listOf("his", "/ɪz/（弱读时）"),
                listOf("have", "/æv/ 或 /əv/（弱读时）"),
                listOf("had", "/æd/ 或 /əd/（弱读时）"),
            ),
        ),
        KbBlock.Tip("注意：/h/ 省略在标准英语中常见，但在正式场合或强调时保留。"),
        KbBlock.Sub("10.2 /j/ 省略（yod-dropping）"),
        KbBlock.Table(
            head = listOf("示例", "英式", "美式"),
            w = listOf(1.8f, 2.2f, 4f),
            rows = listOf(
                listOf("new", "/njuː/", "/nuː/（美式省略 /j/）"),
                listOf("tune", "/tjuːn/", "/tuːn/（美式省略 /j/）"),
                listOf("duty", "/ˈdjuːti/", "/ˈduːti/（美式省略 /j/）"),
                listOf("student", "/ˈstjuːdənt/", "/ˈstuːdənt/（美式省略 /j/）"),
                listOf("assume", "/əˈsjuːm/", "/əˈsuːm/（美式省略 /j/）"),
            ),
        ),
        KbBlock.Sub("10.3 /w/ 和 /j/ 作为滑音（Glide）"),
        KbBlock.Table(
            head = listOf("示例", "音标", "说明"),
            w = listOf(2.2f, 3.4f, 4.6f),
            rows = listOf(
                listOf("I am", "/aɪ**j**æm/", "/aɪ/ 后接 /æ/，加滑音 /j/"),
                listOf("you are", "/juː**w**ɑː/", "/uː/ 后接 /ɑː/，加滑音 /w/"),
                listOf("go out", "/ɡəʊ**w**aʊt/", "/əʊ/ 后接 /aʊ/，加滑音 /w/"),
            ),
        ),
    )),

    KbSection("十一、复数/第三人称 /-s/ 与过去式 /-ed/ 发音规则", listOf(
        KbBlock.Sub("11.1 复数/第三人称 -s、-es 的发音"),
        KbBlock.Table(
            head = listOf("词尾音素", "发音", "示例", "说明"),
            w = listOf(2.6f, 1.1f, 4f, 1.8f),
            rows = listOf(
                listOf("/s, z, ʃ, ʒ, tʃ, dʒ/", "/ɪz/", "bus**es** /ˈbʌsɪz/；watch**es** /ˈwɒtʃɪz/", "嘶擦音后"),
                listOf("/p, t, k, f, θ/", "/s/", "cap**s** /kæps/；book**s** /bʊks/", "清辅音后"),
                listOf("其他（元音、浊辅音）", "/z/", "dog**s** /dɒɡz/；car**s** /kɑːz/", "浊音后"),
            ),
        ),
        KbBlock.Sub("11.2 过去式 -ed 的发音"),
        KbBlock.Table(
            head = listOf("词尾音素", "发音", "示例", "说明"),
            w = listOf(2.6f, 1.1f, 4f, 1.8f),
            rows = listOf(
                listOf("/t, d/", "/ɪd/", "want**ed** /ˈwɒntɪd/；need**ed** /ˈniːdɪd/", "塞音后"),
                listOf("/p, k, f, s, ʃ, tʃ, θ/", "/t/", "walk**ed** /wɔːkt/；laugh**ed** /lɑːft/", "清辅音后"),
                listOf("其他（元音、浊辅音）", "/d/", "play**ed** /pleɪd/；call**ed** /kɔːld/", "浊音后"),
            ),
        ),
    )),

    KbSection("十二、总结速记口诀", listOf(
        KbBlock.Code(
            listOf(
                "元音间，s变z；",
                "s后清，不送气；",
                "have to，v变f；",
                "连读省，弱读轻；",
                "美式t，闪音化；",
                "英式r，后省略；",
                "清辅音，复数s；",
                "浊辅音，复数z。",
            ),
        ),
    )),
)
