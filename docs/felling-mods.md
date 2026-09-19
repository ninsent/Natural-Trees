# Third-party felling mods (spec 14.2)

Status: **not tested yet.** Filled in from manual test P4-3.

Branches carry the `#minecraft:logs` block tag, so tag-driven felling mods are expected to recognise them. Two
likely problems are checked for each mod: a default size cap below a large tree's wood count (up to 400 wood
voxels, spec section 15), and the assumption that every log is a full cube.

When one of these mods is installed, built-in felling logs a notice and stays off unless `felling.force` is set.

| Mod | Mod id (checked against the jar) | Fells our trees whole? | Its block cap | Trouble with thin branches? | Notes |
|---|---|---|---|---|---|
| FallingTree | `fallingtree` (to confirm) | | | | |
| HT's TreeChop | `treechop` (to confirm) | | | | |
| Tree Harvester | `treeharvester` (to confirm) | | | | |
