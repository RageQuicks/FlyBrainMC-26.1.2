# FlyBrainMC 26.1.2 port status

This tree is the first 26.1.2 migration pass from the original 1.21.1 project.

## Completed
- Minecraft target: 26.1.2
- Fabric Loader: 0.19.3+
- Fabric API: 0.155.0+26.1.2
- Fabric Loom: 1.15.5, non-remapping plugin
- Gradle: 9.4.0 wrapper target
- Java target: 25
- Official Mojang mappings / no Yarn mappings
- `fabric.mod.json` dependency metadata
- Creative tab API (`CreativeModeTabEvents`)
- Key mapping API (`KeyMappingHelper` + `KeyMapping.Category`)
- Networking payload registry (`clientboundPlay`)
- Model layer registry (`ModelLayerRegistry`)
- Level render API package migration
- HUD API migration to `HudElementRegistry` / `GuiGraphicsExtractor`
- Bundled male-CNS connectome retained unchanged

## Remaining 26.1 rendering migration
Minecraft's rendering architecture changed substantially before 26.1. Entity renderers/models now use render-state extraction, and world rendering uses the extraction/submission pipeline. The existing fly renderer, custom wing/glow layers, and brain hologram still use the pre-26.1 entity/world rendering APIs and require a dedicated render-state rewrite.

The neural engine, entity behavior, sensory encoders, motor decoder, commands, telemetry protocol, connectome loader, tests, and data are intentionally preserved.

A final production JAR must be built and tested against the actual 26.1.2 Minecraft/Fabric development dependencies; this workspace does not contain those binary dependencies and has Java 21 installed rather than Java 25.
