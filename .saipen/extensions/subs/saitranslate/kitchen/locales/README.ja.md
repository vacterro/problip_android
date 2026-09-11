# Problip Android

[Problip](reference/windows/Problip.cs)（Windows 版）のネイティブ Android 移植 — 小さなランダムビープ音による瞑想タイマー。Windows 版は動作仕様の参照であり、実装の移植元ではありません。

- ブランド: **Problip**
- Google Play 上のタイトル: *Problip: Random Beep Timer*
- アプリケーション ID: `com.vacster.problip`

## 技術スタック

- Kotlin、Jetpack Compose、コルーチン、DataStore、AndroidX AppCompat（アプリごとのロケール設定）
- Play Billing 9.1.0（買い切り製品のみ — サブスクリプションなし）
- Gradle Kotlin DSL、バージョンカタログ
- compileSdk 36、targetSdk 36、minSdk 26
- `app` モジュール 1 個のみ

## 何をするアプリか

- **セッション**: START を押すと約 500 ms 後に最初のビープが鳴り、STOP するまで鳴り続けます。フォアグラウンドサービス、スケジューラ、オーディオエンジンは常に 1 つ — 2 つにはなりません。
- **間隔**: ランダム 4–7 秒（無料）、固定 5/10/15/20/30 秒（無料）、加えて 2 つのプレミアムプリセット — **MANUAL**（独自の FROM/TO 秒。1–3600 にクランプし、昇順に並べ替え）と **PULSE**（5 秒 → 新しいランダム 10–20 秒、交互に継続）。
- **サウンド**: 6 種類。1 つは無料。ランダムプールは実際に再生可能なサウンドからのみ選択します。
- **テーマ**: **15 の Wintage パレット**（無料 1 + プレミアム 14）。即時切替。うち 1 つはライト（Vintage Classic）。アーカイブの `Custom` プリセットは Golden Default と完全に重複していたため削除しました。保存されていた `theme_wintage_custom` は Golden Default に解決されます。
- **トライアル**: 未所有のプレミアムサウンド・テーマ・間隔プリセットは、**5 分間のトライアル** を再利用可能な形で実行できます。アイテムごとに壁時計時刻の有効期限として保存されます。クールダウンなし、アカウントなし、端末 ID なし、サーバー確認なし。
- **統計と報酬**: 実際に成功したビープのみをカウントします（今日 / 今週 / 今月 / 累計。設定画面と、Main の非表示可能な `BLIPS n` 行）。累計 **100,000** ビープで Premium がローカルで永続的に解放されます — Play の所有状態を変更せず、リセット・返金・Billing 更新によって取り消されない、独立した `EARNED` アクセスソースです。
- **Blip Glow**: 成功したビープごとに Main 背景に表示されるプレミアムの柔らかいアクセントパルス（最大アルファ 25%）。Customization Pack、独立した 5 分間の `feature_blip_glow` トライアル、Developer Access、または獲得報酬が対象。設定のデフォルトは ON で、この演出を隠す以外には何も隠しません。
- **Developer Access**: PROBLIP タイトルを約 20 秒間長押しし、押したまま START/STOP すると解放される、非公開の 7 日間グローバル解除。表示は `OWNED` ではなく `DEV` — 期限が切れるためです。購入は期限がありません。
- **ウィジェット**: 実際のセッション状態に追従するコンパクトなホーム画面 START/STOP ウィジェット。ポーリングなし、第 2 のスケジューラなし。基本サイズ 1x1（ブランドマーク + 状態 + 大きな START/STOP 面で、2x1/2x2 にリサイズ可能。ランチャーが報告する幅に応じて PROBLIP ワードマークを表示）。
- **WakeLock**: サービス所有の `PARTIAL_WAKE_LOCK`（`Problip:ActiveSession`）を RUNNING の間だけ保持します — これがないと、画面オフ時に CPU がサスペンドし、5 秒間隔が約 20 秒に伸びていました。
- **MINIMIZE**: セッションを止めずにアプリをバックグラウンドへ移す明示的なボタン。
- **EXIT**: 同じユーティリティレベルの明示的な終了ボタン。実行中セッション（STARTING/RUNNING）は通常のサービス経路で先に停止してからタスクを閉じます（`finishAndRemoveTask`）。STOPPED/ERROR はサービスに触れません。`System.exit` も `killProcess` も使いません。
- **ヘルプ**: Main ヘッダーの `?` が 1 つの Wintage ダイアログを開きます（内部スクロール可。Main 自体はスクロールしません）— クイックスタート、間隔、プレミアム/トライアル、バックグラウンド、ヒント、FAQ。非公開の Developer Access ジェスチャは意図的に文書化していません。
- **言語**: 英語（既定）、ロシア語、エストニア語、日本語、およびシステム既定。設定の LANGUAGE 行から `AppCompatDelegate.setApplicationLocales` で選択します（AppCompat が永続化。`generateLocaleConfig` 有効、パッケージするロケールは en/ru/et/ja に固定）。サービス・通知・ウィジェットも同じ上書き経由で文字列を解決します。

プレミアムのすべては 2 つの Play 製品（プレミアムサウンド群と 1 つの Customization Pack）に乗っており、アクセス判定は 1 箇所だけです:
`free || owned || activeTrial || developerAccess || earnedPremium`。

## 構成

```text
app/                    アプリケーションモジュール
    src/main/java/com/vacster/problip/
        core/           純粋なスケジューリングコア（Android 依存なし）
        trial/          5 分トライアル + Developer Access（純粋）
        billing/        Play Billing ラッパー + 権利判定ルール
        service/        フォアグラウンドサービス、通知、wake lock、コールドスタート
        widget/         ホーム画面 START/STOP ウィジェット
        ui/             Compose 画面 + 15 パレット
    src/test/java/      決定論的な JVM 単体テスト（Robolectric なし）
docs/behavior-contract.md   Windows オリジナルから取った製品動作
docs/release-checklist.md   唯一の権威あるゲート一覧: CODE/CONTENT/PHYSICAL/PLAY/POLICY/FINAL + versionCode ポリシー
docs/release-signing.md     リリース署名戦略（Play App Signing、追跡しない keystore.properties、秘密情報の Git 混入禁止）
docs/privacy-policy.md      公開プライバシーポリシー本文（現状の製品像。連絡先メールは人間のブロッカー）
docs/data-safety.md         Play Data Safety の回答 + 権限/SDK インベントリ（統計ストア対応の更新済み）
docs/play-fgs-declaration.md Play フォアグラウンドサービス宣言 + WakeLock 証拠 + デモ台本
docs/qa-audit-w12.md        QA 監査の判定 + 端末チェックリスト（W12/W12.1）
reference/windows/      オリジナルの Windows ソース + アセット（動作参照）
reference/wintage/      16 のソースパレット JSON（出荷する 15 パレット + リリースで重複プリセットを削除した custom.json）
plan/                   プロジェクトの基になったロードマップパック
```

`reference/wintage/` は依存ではなく文書です。パレットはリテラルカラーとして `ui/theme/Palettes.kt` にコンパイル済みで、実行時に JSON を読むコードはありません。

## ビルド

リリース識別: **versionName 1.0.0、versionCode 1**（`app/build.gradle.kts`）。
一度でも Play へアップロードしたら、以後のすべてのアップロードは厳密に大きい versionCode が必要です — `docs/release-checklist.md` を参照。

JDK 17+ と platform 36 を含む Android SDK が必要です。
`local.properties` に SDK を指定してください（`sdk.dir=...`）。

```bash
./gradlew assembleDebug
./gradlew test
./gradlew bundleRelease        # app/build/outputs/bundle/release/app-release.aab
```

リリース成果物は、追跡しない `keystore.properties` が署名秘密情報を提供しない限り**未署名**でビルドされます（`docs/release-signing.md`）— 未署名リリースはローカル検証には十分で、本番アップロードには決して使えません。最小化（R8）は v1.0.0 ではオフのまま。有効化は再検証すべき動作差分であって、リリース作業ではありません。

## ウェーブ状況

| ウェーブ | 範囲 | 状態 |
|------|-------|--------|
| W0 | リポジトリ/基盤 | done |
| W1 | 純粋スケジューリングコア | done |
| W2 | オーディオ（SoundPool + sound_original） | done |
| W3 | 設定（DataStore）+ メイン UI | done |
| W4 | フォアグラウンドサービス + 通知 | コード done。画面オフ/端末計測は未 |
| W5 | サウンドカタログ / ランダムプール | done（プレミアム WAV は合成プレースホルダ — scripts/ 参照） |
| W6 | テーマ | done — 15 Wintage パレット、無料 1 + 1 パックに 14（重複する Custom は削除） |
| W7 | Google Play Billing | コード done。実際の購入ゲートは Play Console 待ち |
| W9 | プライバシーポリシー + Data Safety | 出荷コードから起草済み（`docs/privacy-policy.md`、`docs/data-safety.md`）。公開は未 |
| W10 | Play FGS 宣言 | 宣言文 + デモ台本 done（`docs/play-fgs-declaration.md`）。動画は端末が必要 |
| W11 | 課金 UI | done — ユーティリティのメイン画面、Sounds/Themes/Settings はセカンダリ画面 |
| W12 | QA 強化 | done — 5 件の欠陥を修正（`docs/qa-audit-w12.md`）。3 行は端末のみ |
| W12.1 | 実行時正当性 + billing 基盤 | done — セッション状態機械、Play Billing 9.1.0、1 つの git 基点 |
| — | トライアル + ウィジェット | done — 再利用可能な 5 分トライアル、START/STOP ウィジェット |
| — | 画面オフの信頼性 | done — セッション WakeLock、MINIMIZE、デバッグ用ドリフトログ。端末受入は未 |
| — | Developer Access + MANUAL | done — 非公開の 7 日間解除、プレミアム手動間隔 |
| — | PULSE | done — 5 秒 / 10–20 秒交互のプレミアムプリセット |
| — | 操作の磨き | done — 再生ランプ、STARTING 状態、ハプティクス、押下/選択状態 |
| — | ローカライズ + ヘルプ + 終了 | done — en/ru/et/ja のアプリごとロケール、`?` ヘルプダイアログ、状態配慮の EXIT |
| — | 統計 + 100K 獲得 Premium + Blip Glow | done — 独自 DataStore 上の Today/Week/Month/Total（メモリ権威・一括ロード）、飽和カウンタ、しきい値自己修復、独立した Glow TRY 操作 |
| — | リリース内容と識別 | done — Problip マーク + アダプティブ/モノクロームランチャーアイコン、Main のブランドマーク、アルファマスク通知アイコン、1x1 優先のリサイズ可能ウィジェット、重複 Custom テーマの削除。最終版の音源はユーザーアセット待ち |
| — | Play 前のリリース工学（T-30） | done — versionName 1.0.0、bundleRelease 経路の検証、署名戦略 + リリースチェックリスト文書、プライバシー/Data Safety/FGS の整合（統計ストア、WakeLock 証拠、T-29 は未解消のまま） |

上の表の証拠: `gradlew test assembleDebug assembleRelease lint lintVitalRelease`
— **260 個の一意な JVM テスト、失敗 0、31 スイート**、lint クリア（エラー 0。残る警告は確認済み — タイポグラフィ/依存関係の古さ、意図的なセッション WakeLock、2 件のローカライズ勧告）、デバッグとリリースの APK をビルド済み。`test` は同じスイートの debug と release 両バリアントを実行するため、この数は各テストを 1 回だけ数えます。

このリポジトリでは供給できない、開いたままのゲート:

- W4 端末ゲート: 30/60/60 分の画面オン/バックグラウンド/ロック実行、Battery Saver、Doze、物理端末での BT/ヘッドホン（`plan/01_MASTER_ROADMAP.md` の W4 を参照）、加えて 20 秒の Developer Access コード、ロック状態での MANUAL セッション、PULSE の画面オンパターン、パレットごとの視覚確認。
- W7 実購入ゲート: 購入、PENDING、承認（acknowledgement）、復元、再インストール、返金 — Play Console 製品と内部テストトラックが必要（まず W8 開発者設定）。
- W9 公開: プライバシーポリシーの公開 URL と連絡先メール。

次のコードウェーブ: リリースパイプラインは準備済み（T-30）。残るのはユーザー支給の最終 WAV（プレミアムサウンドはまだ合成プレースホルダ — `scripts/make_placeholder_sounds.py`、台帳は `reference/audio/SOURCES.md`）、実在する連絡先メール（プライバシーポリシーのブロッカー）、署名認証情報（`docs/release-signing.md`）、物理端末（物理受入、T-014）、Play Console 席（T-009/T-010）、ホスティング判断（T-015）です。

プロジェクトの記憶（ウェーブ、チケット、証拠）は `.saipen/` にあります。

<!-- source-digest: README.md sha256:31ce45f99116e9fb -->
