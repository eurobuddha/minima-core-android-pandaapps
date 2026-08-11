# Graph Report - pandaapps  (2026-08-11)

## Corpus Check
- 19 files · ~31,543 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 180 nodes · 408 edges · 25 communities (10 shown, 15 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 11 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9f1eb0a5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- MainActivity
- Installer.java
- .dp
- AppDetailActivity
- AppEntry
- AppDetailActivity.java
- gradlew
- Theme
- Button
- LinearLayout
- TextView
- Downloads
- Blurb
- Button
- Context
- AppCompatActivity
- AppEntry
- Bitmap
- Bundle
- ImageView
- LinearLayout
- LruCache
- Override
- TextView

## God Nodes (most connected - your core abstractions)
1. `AppDetailActivity` - 28 edges
2. `MainActivity` - 23 edges
3. `AppEntry` - 16 edges
4. `Downloads` - 12 edges
5. `Ui` - 12 edges
6. `Blurb` - 10 edges
7. `Catalog` - 8 edges
8. `ImageLoader` - 7 edges
9. `Listener` - 6 edges
10. `ApkDownloader` - 5 edges

## Surprising Connections (you probably didn't know these)
- `AppDetailActivity` --references--> `AppEntry`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/pandaapps/AppDetailActivity.java → app/src/main/java/com/eurobuddha/pandaapps/AppEntry.java
- `AppDetailActivity` --references--> `Blurb`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/pandaapps/AppDetailActivity.java → app/src/main/java/com/eurobuddha/pandaapps/Blurb.java
- `AppDetailActivity` --implements--> `Listener`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/pandaapps/AppDetailActivity.java → app/src/main/java/com/eurobuddha/pandaapps/Downloads.java
- `MainActivity` --references--> `AppEntry`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/pandaapps/MainActivity.java → app/src/main/java/com/eurobuddha/pandaapps/AppEntry.java
- `MainActivity` --implements--> `Listener`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/pandaapps/MainActivity.java → app/src/main/java/com/eurobuddha/pandaapps/Downloads.java

## Import Cycles
- None detected.

## Communities (25 total, 15 thin omitted)

### Community 0 - "MainActivity"
Cohesion: 0.17
Nodes (4): android.widget.TextView, Groups, Override, MainActivity

### Community 1 - "Installer.java"
Cohesion: 0.17
Nodes (6): Activity, Installer, Context, Context, PackageUtil, Intent

### Community 2 - ".dp"
Cohesion: 0.22
Nodes (9): android.content.Context, android.graphics.drawable.GradientDrawable, android.widget.Button, android.widget.LinearLayout, LinearLayout, Ui, Button, Drawable (+1 more)

### Community 3 - "AppDetailActivity"
Cohesion: 0.20
Nodes (3): AppDetailActivity, Override, TextView

### Community 4 - "AppEntry"
Cohesion: 0.10
Nodes (9): ApkDownloader, Cb, Context, AppEntry, Catalog, Cb, Src, JSONObject (+1 more)

### Community 5 - "AppDetailActivity.java"
Cohesion: 0.27
Nodes (6): android.graphics.Bitmap, android.os.Bundle, android.util.LruCache, android.widget.ImageView, androidx.appcompat.app.AppCompatActivity, ImageLoader

### Community 6 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 12 - "Downloads"
Cohesion: 0.20
Nodes (3): Downloads, Listener, State

### Community 13 - "Blurb"
Cohesion: 0.29
Nodes (3): Blurb, Note, java.util.regex.Pattern

## Knowledge Gaps
- **15 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppDetailActivity` connect `AppDetailActivity` to `.dp`, `AppEntry`, `AppDetailActivity.java`, `Downloads`, `Blurb`?**
  _High betweenness centrality (0.207) - this node is a cross-community bridge._
- **Why does `AppEntry` connect `AppEntry` to `MainActivity`, `Installer.java`, `.dp`, `AppDetailActivity`, `Downloads`?**
  _High betweenness centrality (0.171) - this node is a cross-community bridge._
- **Why does `MainActivity` connect `MainActivity` to `Installer.java`, `.dp`, `AppEntry`, `AppDetailActivity.java`, `Downloads`?**
  _High betweenness centrality (0.121) - this node is a cross-community bridge._
- **Should `AppEntry` be split into smaller, more focused modules?**
  _Cohesion score 0.09885057471264368 - nodes in this community are weakly interconnected._