# Graph Report - pandaapps  (2026-08-11)

## Corpus Check
- 19 files · ~32,128 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 188 nodes · 415 edges · 24 communities (10 shown, 14 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 10 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `3b1d3060`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- MainActivity
- .addDetails
- .dp
- .download
- AppEntry
- ImageLoader
- gradlew
- Theme
- Button
- LinearLayout
- TextView
- AppDetailActivity
- .render
- Button
- Context
- AppCompatActivity
- AppEntry
- Bitmap
- Bundle
- LinearLayout
- LruCache
- Override
- TextView

## God Nodes (most connected - your core abstractions)
1. `AppDetailActivity` - 30 edges
2. `MainActivity` - 24 edges
3. `Downloads` - 13 edges
4. `Ui` - 12 edges
5. `Blurb` - 9 edges
6. `AppEntry` - 8 edges
7. `Catalog` - 8 edges
8. `ImageLoader` - 7 edges
9. `Listener` - 6 edges
10. `ApkDownloader` - 5 edges

## Surprising Connections (you probably didn't know these)
- `MainActivity` --implements--> `Listener`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/pandaapps/MainActivity.java → app/src/main/java/com/eurobuddha/pandaapps/Downloads.java
- `AppDetailActivity` --implements--> `Listener`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/pandaapps/AppDetailActivity.java → app/src/main/java/com/eurobuddha/pandaapps/Downloads.java

## Import Cycles
- None detected.

## Communities (24 total, 14 thin omitted)

### Community 0 - "MainActivity"
Cohesion: 0.14
Nodes (5): android.widget.TextView, Groups, AppEntry, Override, MainActivity

### Community 1 - ".addDetails"
Cohesion: 0.15
Nodes (6): Activity, Installer, Context, Context, PackageUtil, Intent

### Community 2 - ".dp"
Cohesion: 0.20
Nodes (11): android.content.Context, android.graphics.drawable.GradientDrawable, android.widget.Button, android.widget.LinearLayout, LinearLayout, TextView, Ui, Button (+3 more)

### Community 3 - ".download"
Cohesion: 0.27
Nodes (3): ApkDownloader, Cb, Context

### Community 4 - "AppEntry"
Cohesion: 0.16
Nodes (6): AppEntry, Catalog, Cb, Src, JSONObject, org.json.JSONObject

### Community 5 - "ImageLoader"
Cohesion: 0.42
Nodes (4): android.graphics.Bitmap, android.util.LruCache, android.widget.ImageView, ImageLoader

### Community 6 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 12 - "AppDetailActivity"
Cohesion: 0.10
Nodes (12): android.os.Bundle, android.widget.ProgressBar, androidx.appcompat.app.AppCompatActivity, AppDetailActivity, AppEntry, Override, Downloads, AppEntry (+4 more)

### Community 13 - ".render"
Cohesion: 0.23
Nodes (3): Blurb, Note, java.util.regex.Pattern

## Knowledge Gaps
- **14 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppDetailActivity` connect `AppDetailActivity` to `MainActivity`, `.addDetails`, `.dp`, `.render`?**
  _High betweenness centrality (0.170) - this node is a cross-community bridge._
- **Why does `MainActivity` connect `MainActivity` to `.dp`, `AppDetailActivity`?**
  _High betweenness centrality (0.119) - this node is a cross-community bridge._
- **Why does `AppEntry` connect `AppEntry` to `MainActivity`, `.addDetails`, `.download`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **Should `MainActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.14153846153846153 - nodes in this community are weakly interconnected._
- **Should `.addDetails` be split into smaller, more focused modules?**
  _Cohesion score 0.14761904761904762 - nodes in this community are weakly interconnected._
- **Should `AppDetailActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.10338680926916222 - nodes in this community are weakly interconnected._