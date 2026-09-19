# Natural Trees

A Minecraft 1.21.1 mod for NeoForge and Fabric that makes trees look like trees: a trunk that thickens
with the tree's size, limbs that fork and curve, and foliage that follows the limbs. Every tree is
computed from its species parameters and a seed at the moment it is placed.

- Mod ID: `naturaltrees`
- Minecraft 1.21.1, Java 21, Mojang mappings
- Licence: Apache-2.0

**Status:** in development. Oak and birch trees generate in the world and grow from saplings; the other woods
still use vanilla trees.

## What it changes

- **Branch blocks** for the eight overworld woods, plain and stripped: thin wooden blocks with explicit arms.
  As a block a branch is a log (leaves next to it do not decay, it burns, axes mine it); as an item it is worth half
  a log (2 planks, half a log's burn time) and is deliberately not accepted by "any log" recipes or as charcoal.
  Two logs stacked vertically craft into four branches. Shears toggle an arm on the face you click; an axe strips.
- **Trees.** The mod ships a built-in datapack, *Natural Trees world generation*, that supplies vanilla's oak, fancy
  oak, birch and tall birch tree features **by their vanilla ids**, and lowers the tree count of the forests made of
  them to about a third, because each tree is bigger. Biomes, saplings and villages keep using the same ids, so
  nothing else changes.
- **Commands** (permission level 2): `/naturaltrees place <feature> [seed]`, `/naturaltrees grid <feature> <n>`,
  `/naturaltrees stats <feature> [seeds]`.

### Turning the trees off

The datapack is optional and enabled by default. Untick it on the Create World screen, or run
`/datapack disable` with its name from `/datapack list`, and newly generated chunks and saplings grow vanilla trees
again. The blocks, recipes and commands stay. Chunks that already exist are never changed, in either direction.

### Other world generation packs

If another datapack supplies the same vanilla tree ids, the one higher in the pack list wins; move this mod's pack
up or down to choose. Packs and mods that add their own tree ids are not affected.

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
