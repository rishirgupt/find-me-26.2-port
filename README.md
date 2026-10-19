# Find Me – Minecraft 26.2 port (Fabric), source code only

> **This is an unofficial community port, not the original mod.**
> Find Me was created by **kuzhi**. The mod, its design and its art and sounds are theirs. This repository only contains the source code
> changes needed to run the Fabric version on Minecraft 26.2. **Please support the original mod and its author.**

## Original Find Me

| | |
|---|---|
| Modrinth | <https://modrinth.com/mod/find-me> |
| Source of the Forge / NeoForge versions | <https://github.com/Ku00115/find-me-1.3> |
| Author | kuzhi (Modrinth: Kuzhizaizheli) |

## Licensing

Modrinth lists Find Me as **LGPL-3.0-only**, and the source repository above is public. The Fabric jar this port started from, however,
declares "All Rights Reserved" in its `fabric.mod.json`. Because of that uncertainty this repository contains **only source code: no textures,
models, sounds or other art of the original mod, and no release jar.** If the author asks for this repository to be changed or removed,
it will be.

## What this port changes

- Fabric Loom 1.18 / Java 25 with the unobfuscated Mojang names and Fabric API 0.161.0+26.2.
- GUI, rendering, networking, entity and data component code ported to the 26.2 APIs.

## Building

```
./gradlew build
```

Requires Java 25. The original mod's `assets/` folder is not included and must be taken from the official Find Me jar.

## Reporting problems

Problems that only happen on 26.2 belong in this repository's issues. Please do not report them to the original author.

*Parts of this port were made with the help of Claude (Anthropic).*
