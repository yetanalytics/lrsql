def pkg_name(c):
  if (c.group? // "") != "" then "\(c.group)/\(c.name)" else (c.name // "") end;

[
  .metadata.component?,
  .components[]?
]
| map(select(. != null))
| map({
    package: pkg_name(.),
    source: (.purl // .["bom-ref"] // .author // .publisher // "unknown"),
    version: (.version // "unknown")
  })
| unique
| sort_by(.package, .version)
| (["package","source","version"] | @csv),
  (.[] | [ .package, .source, .version ] | @csv)
