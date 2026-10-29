package com.cheesecomer.cartmate.domain.suggest

/** 予測とサジェストのパラメータ（docs/design/suggestions.md「パラメータ」）。MVP では定数で、UI からは変えない */
data class SuggestionParams(
    /** 周期の予測を始める購入回数（購入した日の数） */
    val minEvents: Int = 3,
    /** 中央値に使う直近の間隔の数 */
    val maxIntervals: Int = 10,
    /** 時期が近い商品として出す経過率の下限 */
    val dueRatio: Double = 0.8,
    /** これを超えたら「買うのをやめた」とみなして出さない */
    val staleRatio: Double = 3.0,
    /**
     * 時期が近い商品の順位がいちばん高くなる経過率。これを超えると、超えた分だけ順位を下げる
     * （予定日を大きく過ぎた商品が、今ちょうど必要な商品より上に並ばないように）
     */
    val overduePeakRatio: Double = 1.5,
    /** 早すぎる再購入とみなす経過率 */
    val earlyRatio: Double = 0.5,
    /** 中央絶対偏差 / 中央値 がこれを超えたら、周期が不安定（時期が近い商品にも、早すぎる再購入の注意にも使わない） */
    val maxDispersion: Double = 0.5,
    /** 周期が分からないか不安定な商品で、早すぎる再購入として知らせる前回の購入からの日数 */
    val recentPurchaseDays: Long = 3,
    /** 類似度を計算するリストの最小の商品数 */
    val minProfileSize: Int = 5,
    val minIntersection: Int = 3,
    /** 重複係数の閾値 */
    val minOverlap: Double = 0.5,
    val maxDue: Int = 10,
    /** よく買う商品として出す、直近365日に買った日の数の下限 */
    val minFrequentDays: Int = 2,
    val maxFrequent: Int = 5,
    val maxCompletions: Int = 8,
)
