# Natural Trees

A Minecraft 1.21.1 mod for NeoForge and Fabric that makes trees look like trees: a trunk that thickens
with the tree's size, limbs that fork and curve, and foliage that follows the limbs. Every tree is
computed from its species parameters and a seed at the moment it is placed.

- Mod ID: `naturaltrees`
- Minecraft 1.21.1, Java 21, Mojang mappings
- Licence: Apache-2.0

**Status:** early development. The project skeleton builds on both loaders; nothing is playable yet.

## Layout

| Module | Contents |
|---|---|
| `treecore` | The generator: pure Java, no Minecraft classes, no dependencies. Compiled into each loader jar. |
| `common` | Loader-independent mod code. |
| `fabric`, `neoforge` | Thin loader modules. |
| `docs` | The specification (`natural-trees-spec.md`), plans, progress and notes. |

## Building

```
./gradlew build
```

Needs a JDK 21. The mod jars are written to `fabric/build/libs` and `neoforge/build/libs`.

## References

The tree skeleton follows J. Weber and J. Penn, "Creation and Rendering of Realistic Trees",
SIGGRAPH '95, [doi:10.1145/218380.218427](https://doi.org/10.1145/218380.218427). The paper is
copyrighted and is not part of this repository.
