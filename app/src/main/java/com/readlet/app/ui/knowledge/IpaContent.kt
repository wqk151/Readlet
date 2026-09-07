package com.readlet.app.ui.knowledge

/** 音标手册（英语国际音标完整分类，DJ 音标 · 48 个）：章节结构还原原文档层级，`####` 分组降为 Sub。 */
val IpaSections = listOf(
    KbSection("一、音标总览", listOf(
        KbBlock.Table(
            head = listOf("大类", "数量", "说明"),
            w = listOf(1.4f, 1.4f, 6f),
            rows = listOf(
                listOf("**元音**", "20 个", "12 个单元音 + 8 个双元音"),
                listOf("**辅音**", "28 个", "按发音方式分为 6 小类"),
            ),
        ),
    )),

    KbSection("1. 单元音（Monophthongs）12 个", listOf(
        KbBlock.Sub("（1）前元音（舌前部抬起）"),
        KbBlock.Table(
            head = listOf("音标", "发音技巧", "样例单词"),
            w = listOf(1.2f, 5f, 3.4f),
            rows = listOf(
                listOf("/iː/", "舌尖抵下齿，舌前部尽量抬高，嘴唇向两侧伸展成扁平状，发音长而紧", "see /siː/, bee /biː/, tea /tiː/"),
                listOf("/ɪ/", "舌位比 /iː/ 稍低，口型稍开，发音短而松，嘴角不用拉太宽", "sit /sɪt/, big /bɪg/, fish /fɪʃ/"),
                listOf("/e/", "舌尖抵下齿，舌前部稍抬起，口型扁平，介于 /ɪ/ 和 /æ/ 之间", "bed /bed/, red /red/, pen /pen/"),
                listOf("/æ/", "舌尖抵下齿，舌前部稍抬起，口型张大，嘴角向两侧充分拉开", "cat /kæt/, bad /bæd/, map /mæp/"),
            ),
            firstBold = true,
        ),
        KbBlock.Sub("（2）中元音（舌中部抬起）"),
        KbBlock.Table(
            head = listOf("音标", "发音技巧", "样例单词"),
            w = listOf(1.2f, 5f, 3.4f),
            rows = listOf(
                listOf("/ɜː/", "舌中部抬高，口型自然张开，发音长而清晰，嘴唇放松呈自然状", "bird /bɜːd/, nurse /nɜːs/, work /wɜːk/"),
                listOf("/ə/", "舌身平放，舌中部轻微抬起，口型半开，发音轻而短，是英语中最弱的音", "about /əˈbaʊt/, sofa /ˈsəʊfə/, China /ˈtʃaɪnə/"),
                listOf("/ʌ/", "舌中部稍抬起，口型自然张开，发音短促有力，比 /ɑː/ 口型略小", "cup /kʌp/, sun /sʌn/, love /lʌv/"),
            ),
            firstBold = true,
        ),
        KbBlock.Sub("（3）后元音（舌后部抬起）"),
        KbBlock.Table(
            head = listOf("音标", "发音技巧", "样例单词"),
            w = listOf(1.2f, 5f, 3.4f),
            rows = listOf(
                listOf("/ɑː/", "舌后部压低，口型张大，发音长而饱满，嘴唇自然张开", "car /kɑː/, father /ˈfɑːðə/, heart /hɑːt/"),
                listOf("/ɒ/", "舌后部稍抬起，双唇收圆但开口较大，发音短促（英式发音）", "hot /hɒt/, dog /dɒg/, box /bɒks/"),
                listOf("/ɔː/", "舌后部抬高，双唇收圆突出，发音长而饱满", "door /dɔː/, more /mɔː/, ball /bɔːl/"),
                listOf("/ʊ/", "舌后部稍抬起，双唇收圆但不如 /uː/ 紧，发音短促", "book /bʊk/, good /gʊd/, put /pʊt/"),
                listOf("/uː/", "舌后部尽量抬高，双唇收圆突出成小圆形，发音长而紧", "food /fuːd/, blue /bluː/, moon /muːn/"),
            ),
            firstBold = true,
        ),
    )),

    KbSection("2. 双元音（Diphthongs）8 个", listOf(
        KbBlock.Lead("双元音由两个元音滑动组成，发音时口型有明显变化。"),
        KbBlock.Sub("（1）合口双元音（口型从开到合）"),
        KbBlock.Table(
            head = listOf("音标", "发音技巧", "样例单词"),
            w = listOf(1.2f, 5f, 3.4f),
            rows = listOf(
                listOf("/eɪ/", "从 /e/ 滑向 /ɪ/，口型由半开变为半合，舌尖始终抵下齿", "day /deɪ/, make /meɪk/, name /neɪm/"),
                listOf("/aɪ/", "从 /a/ 滑向 /ɪ/，口型由大变小，舌尖抵下齿", "my /maɪ/, time /taɪm/, eye /aɪ/"),
                listOf("/ɔɪ/", "从 /ɔ/ 滑向 /ɪ/，口型由圆变扁，双唇从收圆到展开", "boy /bɔɪ/, toy /tɔɪ/, voice /vɔɪs/"),
                listOf("/aʊ/", "从 /a/ 滑向 /ʊ/，口型由大变小，双唇从展开到收圆", "now /naʊ/, house /haʊs/, out /aʊt/"),
                listOf("/əʊ/", "从 /ə/ 滑向 /ʊ/，口型由半开变为收圆，双唇逐渐收圆突出（英式）", "go /gəʊ/, home /həʊm/, know /nəʊ/"),
            ),
            firstBold = true,
        ),
        KbBlock.Sub("（2）集中双元音（向中央元音 /ə/ 滑动）"),
        KbBlock.Table(
            head = listOf("音标", "发音技巧", "样例单词"),
            w = listOf(1.2f, 5f, 3.4f),
            rows = listOf(
                listOf("/ɪə/", "从 /ɪ/ 滑向 /ə/，口型由扁变自然，舌位由高降低", "ear /ɪə/, here /hɪə/, dear /dɪə/"),
                listOf("/eə/", "从 /e/ 滑向 /ə/，口型由半开变自然，舌位由中降低", "air /eə/, care /keə/, hair /heə/"),
                listOf("/ʊə/", "从 /ʊ/ 滑向 /ə/，口型由收圆变自然，舌位由高降低", "tour /tʊə/, poor /pʊə/, sure /ʃʊə/"),
            ),
            firstBold = true,
        ),
    )),

    KbSection("1. 按发音方式分类（6 小类）", listOf(
        KbBlock.Lead("辅音与元音并列，共 28 个；可按发音方式、声带振动、发音部位三种维度分类。"),
        KbBlock.Sub("（1）爆破音（Plosives）——气流在口腔中完全受阻后突然释放"),
        KbBlock.Table(
            head = listOf("音标", "清浊", "发音部位", "发音技巧", "样例单词"),
            w = listOf(1.2f, 1.4f, 2.1f, 3f, 2.2f),
            rows = listOf(
                listOf("/p/", "清", "双唇", "双唇紧闭，气流冲破双唇爆发而出，声带不振动，送气强", "pen /pen/, pig /pɪg/, apple /ˈæpl/"),
                listOf("/b/", "浊", "双唇", "双唇紧闭，气流冲破双唇爆发而出，声带振动，几乎不送气", "book /bʊk/, big /bɪg/, baby /ˈbeɪbi/"),
                listOf("/t/", "清", "齿龈", "舌尖抵上齿龈，气流冲破阻碍爆发而出，声带不振动，送气强", "tea /tiː/, ten /ten/, cat /kæt/"),
                listOf("/d/", "浊", "齿龈", "舌尖抵上齿龈，气流冲破阻碍爆发而出，声带振动，几乎不送气", "dog /dɒg/, red /red/, day /deɪ/"),
                listOf("/k/", "清", "软腭", "舌后部抵软腭，气流冲破阻碍爆发而出，声带不振动，送气强", "cat /kæt/, book /bʊk/, key /kiː/"),
                listOf("/g/", "浊", "软腭", "舌后部抵软腭，气流冲破阻碍爆发而出，声带振动，几乎不送气", "go /gəʊ/, egg /eg/, girl /gɜːl/"),
            ),
            firstBold = true,
        ),
        KbBlock.Sub("（2）摩擦音（Fricatives）——气流通过狭窄通道摩擦而出"),
        KbBlock.Table(
            head = listOf("音标", "清浊", "发音部位", "发音技巧", "样例单词"),
            w = listOf(1.2f, 1.4f, 2.1f, 3f, 2.2f),
            rows = listOf(
                listOf("/f/", "清", "唇齿", "上齿轻触下唇，气流摩擦而出", "fish /fɪʃ/, fine /faɪn/, coffee /ˈkɒfi/"),
                listOf("/v/", "浊", "唇齿", "上齿轻触下唇，气流摩擦而出，声带振动", "very /ˈveri/, love /lʌv/, voice /vɔɪs/"),
                listOf("/θ/", "清", "齿间", "舌尖轻触上齿背，气流摩擦", "think /θɪŋk/, three /θriː/, bath /bɑːθ/"),
                listOf("/ð/", "浊", "齿间", "舌尖轻触上齿背，气流摩擦，声带振动", "this /ðɪs/, that /ðæt/, mother /ˈmʌðə/"),
                listOf("/s/", "清", "齿龈", "舌尖靠近上齿龈，气流摩擦", "sun /sʌn/, see /siː/, bus /bʌs/"),
                listOf("/z/", "浊", "齿龈", "舌尖靠近上齿龈，气流摩擦，声带振动", "zoo /zuː/, zero /ˈzɪərəʊ/, easy /ˈiːzi/"),
                listOf("/ʃ/", "清", "齿龈后", "舌端靠近齿龈后部，双唇稍圆", "she /ʃiː/, ship /ʃɪp/, wash /wɒʃ/"),
                listOf("/ʒ/", "浊", "齿龈后", "舌端靠近齿龈后部，双唇稍圆，声带振动", "measure /ˈmeʒə/, vision /ˈvɪʒn/, pleasure /ˈpleʒə/"),
                listOf("/h/", "清", "声门", "气流从声门摩擦而出，声带不振动", "he /hiː/, happy /ˈhæpi/, house /haʊs/"),
            ),
            firstBold = true,
        ),
        KbBlock.Sub("（3）破擦音（Affricates）——先阻塞后摩擦，爆破音 + 摩擦音的组合动作"),
        KbBlock.Table(
            head = listOf("音标", "清浊", "发音部位", "发音技巧", "样例单词"),
            w = listOf(1.2f, 1.4f, 2.1f, 3f, 2.2f),
            rows = listOf(
                listOf("/tʃ/", "清", "齿龈后", "舌尖抵齿龈后部形成阻塞，然后摩擦释放，声带不振动", "chair /tʃeə/, child /tʃaɪld/, watch /wɒtʃ/"),
                listOf("/dʒ/", "浊", "齿龈后", "舌尖抵齿龈后部形成阻塞，然后摩擦释放，声带振动", "job /dʒɒb/, just /dʒʌst/, orange /ˈɒrɪndʒ/"),
            ),
            firstBold = true,
        ),
        KbBlock.Sub("（4）鼻音（Nasals）——口腔封闭，气流从鼻腔释放，全部为浊辅音"),
        KbBlock.Table(
            head = listOf("音标", "发音部位", "发音技巧", "样例单词"),
            w = listOf(1.2f, 1.4f, 3.8f, 3.2f),
            rows = listOf(
                listOf("/m/", "双唇", "双唇紧闭，软腭下降，气流从鼻腔出", "man /mæn/, moon /muːn/, time /taɪm/"),
                listOf("/n/", "齿龈", "舌尖抵上齿龈，软腭下降，气流从鼻腔出", "no /nəʊ/, sun /sʌn/, name /neɪm/"),
                listOf("/ŋ/", "软腭", "舌后部抵软腭，软腭下降，气流从鼻腔出", "sing /sɪŋ/, long /lɒŋ/, think /θɪŋk/"),
            ),
            firstBold = true,
        ),
        KbBlock.Sub("（5）边音（Laterals）——舌尖抵上齿龈，气流从舌头两侧流出，浊辅音"),
        KbBlock.Table(
            head = listOf("音标", "发音技巧", "样例单词"),
            w = listOf(1.2f, 5f, 3.4f),
            rows = listOf(
                listOf("/l/", "舌尖抵上齿龈，气流从舌两侧流出；在元音前发清晰音，在词尾发模糊音", "like /laɪk/, ball /bɔːl/, school /skuːl/"),
            ),
            firstBold = true,
        ),
        KbBlock.Sub("（6）半元音（Approximants / Semivowels）——发音短暂带元音色彩，迅速滑向后面的元音，浊辅音"),
        KbBlock.Table(
            head = listOf("音标", "发音技巧", "样例单词"),
            w = listOf(1.2f, 5f, 3.4f),
            rows = listOf(
                listOf("/w/", "双唇收圆突出，舌后部向软腭抬起，迅速滑向元音", "we /wiː/, water /ˈwɔːtə/, what /wɒt/"),
                listOf("/j/", "舌前部向硬腭抬起，迅速滑向元音", "yes /jes/, you /juː/, yellow /ˈjeləʊ/"),
                listOf("/r/", "舌尖向上齿龈后部卷起（卷舌音），气流摩擦而出", "red /red/, right /raɪt/, rain /reɪn/"),
            ),
            firstBold = true,
        ),
    )),

    KbSection("2. 清浊辅音对照", listOf(
        KbBlock.Lead("清辅音 vs 浊辅音按声带是否振动划分，与发音方式分类是交叉关系：发音部位和方式完全相同，唯一区别是声带是否振动。"),
        KbBlock.Sub("（1）清浊成对辅音（10 对）"),
        KbBlock.Table(
            head = listOf("清辅音", "浊辅音", "发音部位", "样例对比"),
            w = listOf(1.4f, 1.4f, 1.5f, 3.2f),
            rows = listOf(
                listOf("/p/", "/b/", "双唇", "pen /pen/ vs ben /ben/"),
                listOf("/t/", "/d/", "齿龈", "ten /ten/ vs den /den/"),
                listOf("/k/", "/g/", "软腭", "cap /kæp/ vs gap /gæp/"),
                listOf("/f/", "/v/", "唇齿", "fine /faɪn/ vs vine /vaɪn/"),
                listOf("/θ/", "/ð/", "齿间", "think /θɪŋk/ vs this /ðɪs/"),
                listOf("/s/", "/z/", "齿龈", "sip /sɪp/ vs zip /zɪp/"),
                listOf("/ʃ/", "/ʒ/", "齿龈后", "ship /ʃɪp/ vs measure /ˈmeʒə/"),
                listOf("/tʃ/", "/dʒ/", "齿龈后", "chair /tʃeə/ vs jar /dʒɑː/"),
                listOf("/ts/", "/dz/", "齿龈", "cats /kæts/ vs beds /bedz/"),
                listOf("/tr/", "/dr/", "齿龈后", "tree /triː/ vs dream /driːm/"),
            ),
            firstBold = true,
        ),
        KbBlock.Sub("（2）全部清辅音（9 个）"),
        KbBlock.Code(
            listOf("/p/  /t/  /k/  /f/  /θ/  /s/  /ʃ/  /h/  /tʃ/"),
        ),
        KbBlock.Sub("（3）全部浊辅音（15 个）"),
        KbBlock.Code(
            listOf(
                "/b/  /d/  /g/  /v/  /ð/  /z/  /ʒ/  /r/  /dʒ/",
                "/m/  /n/  /ŋ/  /l/  /w/  /j/",
            ),
        ),
        KbBlock.Sub("（4）辨别清浊辅音的实用技巧"),
        KbBlock.Table(
            head = listOf("方法", "操作", "清辅音表现", "浊辅音表现"),
            w = listOf(1.5f, 2.6f, 2.2f, 2.2f),
            rows = listOf(
                listOf("**摸喉法**", "手指轻按喉结处", "无振动感", "明显振动感"),
                listOf("**纸张测试**", "一张纸放在嘴前", "气流强，能吹动纸张", "气流弱，纸张不动"),
                listOf("**镜像观察**", "对着镜子看喉咙", "声带处无明显起伏", "可见轻微震动"),
            ),
        ),
    )),

    KbSection("四、发音技巧速查", listOf(
        KbBlock.Sub("1. 爆破音核心技巧"),
        KbBlock.Table(
            head = listOf("技巧", "说明", "示例"),
            w = listOf(1.6f, 3.6f, 3.4f),
            rows = listOf(
                listOf("**失去爆破**", "两个爆破音相邻时，前一个只做好发音姿势，不真正爆破", "black cat /blæ**k** kæt/ → /blæ(k) kæt/"),
                listOf("**不完全爆破**", "爆破音后接摩擦音 /f, v, θ, ð, s, z, ʃ, ʒ/ 或破擦音时，只做轻微爆破", "picture /ˈpɪ**k**tʃə/ → /ˈpɪ(k)tʃə/"),
                listOf("**鼻腔爆破**", "爆破音后接鼻音 /m, n, ŋ/ 时，气流从鼻腔释放", "good morning /gʊ**d** ˈmɔːnɪŋ/ → /gʊ(d)ˈmɔːnɪŋ/"),
                listOf("**舌侧爆破**", "爆破音后接 /l/ 时，气流从舌侧释放", "bottle /ˈbɒ**t**l/ → /ˈbɒ(t)l/"),
            ),
        ),
        KbBlock.Sub("2. 元音核心技巧"),
        KbBlock.Table(
            head = listOf("技巧", "说明"),
            w = listOf(1.6f, 5.6f),
            rows = listOf(
                listOf("**长元音 vs 短元音**", "/iː/ /ɑː/ /ɔː/ /uː/ /ɜː/ 为长元音，发音饱满拉长；其余为短元音，发音短促"),
                listOf("**双元音滑动**", "双元音必须从一个音滑向另一个音，口型有明显变化，不可发成两个独立音"),
                listOf("**弱读 /ə/**", "/ə/ 是英语中最弱的音，在非重读音节中，很多元音都会弱化为 /ə/"),
            ),
        ),
    )),

    KbSection("附录：分类层级图", listOf(
        KbBlock.Code(
            listOf(
                "英语音标（48个）",
                "│",
                "├── 元音（20个）",
                "│   ├── 单元音（12个）",
                "│   │   ├── 前元音：/iː/ /ɪ/ /e/ /æ/",
                "│   │   ├── 中元音：/ɜː/ /ə/ /ʌ/",
                "│   │   └── 后元音：/ɑː/ /ɒ/ /ɔː/ /ʊ/ /uː/",
                "│   └── 双元音（8个）",
                "│       ├── 合口双元音：/eɪ/ /aɪ/ /ɔɪ/ /aʊ/ /əʊ/",
                "│       └── 集中双元音：/ɪə/ /eə/ /ʊə/",
                "│",
                "└── 辅音（28个）",
                "    ├── 按发音方式分类：",
                "    │   ├── 爆破音：/p/ /b/ /t/ /d/ /k/ /g/",
                "    │   ├── 摩擦音：/f/ /v/ /θ/ /ð/ /s/ /z/ /ʃ/ /ʒ/ /h/ /r/",
                "    │   ├── 破擦音：/tʃ/ /dʒ/",
                "    │   ├── 鼻音：/m/ /n/ /ŋ/",
                "    │   ├── 边音：/l/",
                "    │   └── 半元音：/w/ /j/",
                "    └── 按声带振动分类：",
                "        ├── 清辅音（9个）：/p/ /t/ /k/ /f/ /θ/ /s/ /ʃ/ /h/ /tʃ/",
                "        └── 浊辅音（15个）：/b/ /d/ /g/ /v/ /ð/ /z/ /ʒ/ /r/ /dʒ/ /m/ /n/ /ŋ/ /l/ /w/ /j/",
            ),
        ),
    )),

    KbSection("记忆口诀", listOf(
        KbBlock.Code(
            listOf(
                "元音分单双，辅音是大纲；",
                "爆破摩擦破擦音，鼻边半元音排成行；",
                "清浊另分家，声带振不振，两套标准莫混为一谈。",
            ),
        ),
    )),
)
