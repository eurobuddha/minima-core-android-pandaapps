# Graph Report - pandaapps  (2026-08-05)

## Corpus Check
- 15 files · ~28,395 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 108 nodes · 185 edges · 12 communities (8 shown, 4 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 1 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `c6c09268`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- MainActivity
- Installer.java
- Ui
- .download
- Catalog
- ImageLoader
- gradlew
- Theme
- Button
- LinearLayout
- TextView

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 24 edges
2. `Ui` - 10 edges
3. `Catalog` - 8 edges
4. `ImageLoader` - 7 edges
5. `ApkDownloader` - 5 edges
6. `Cb` - 5 edges
7. `AppEntry` - 5 edges
8. `Installer` - 5 edges
9. `PackageUtil` - 5 edges
10. `Cb` - 4 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Import Cycles
- None detected.

## Communities (12 total, 4 thin omitted)

### Community 0 - "MainActivity"
Cohesion: 0.17
Nodes (8): MainActivity, AppCompatActivity, AppEntry, Bundle, Button, LinearLayout, Override, TextView

### Community 1 - "Installer.java"
Cohesion: 0.18
Nodes (6): Activity, Installer, Context, Context, PackageUtil, Intent

### Community 2 - "Ui"
Cohesion: 0.33
Nodes (6): Button, Context, LinearLayout, TextView, Ui, GradientDrawable

### Community 3 - ".download"
Cohesion: 0.27
Nodes (3): ApkDownloader, Cb, Context

### Community 4 - "Catalog"
Cohesion: 0.18
Nodes (5): AppEntry, Catalog, Cb, Src, JSONObject

### Community 5 - "ImageLoader"
Cohesion: 0.42
Nodes (4): ImageLoader, Bitmap, ImageView, LruCache

### Community 6 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `ImageLoader`?**
  _High betweenness centrality (0.135) - this node is a cross-community bridge._
- **Why does `AppEntry` connect `Catalog` to `.download`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._