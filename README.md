# EnderPortal 1.0.0
Purpur 1.21.4 / Java 21.

This is a starter implementation of the first-End-portal awakening event: delayed opening, purple beams, rift growth, global earthquake, flash, corruption crater/Chorus growth, portal curse and blocking subsequent portal creation.

Commands:
- `/enderportal` — admin menu
- `/enderportal reset` — reset stored event state

Build with `mvn clean package` or GitHub Actions.

## Resource pack
`resource-pack/` is a starter visual pack with EnderPortal assets. A permanently purple sky needs client-side shader/rendering work; the plugin can automatically send a hosted pack after the explosion when `purple-sky.resource-pack-url` is configured.
