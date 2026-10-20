# Minimap Customizer

Minimap Customizer lets you decide what appears on your RuneLite minimap, from map icons and entity markers to foliage and tree symbols.

## Features

- **Map icon filters:** Show or hide banks, shops, agility courses, quests, Sailing facilities, tutors, and other map icons by category.
- **Entity markers:** Use a direction arrow for your character, a sword for attackable NPCs, or a looting bag for ground items. Native dots remain available.
- **Dot colors:** Choose separate colors for eight types of native dots, including NPCs, players, friends, and ground items. Custom icon artwork keeps its original colors.
- **Trees and foliage:** Independently toggle ordinary foliage and rare tree symbols.

Everything is visible by default. Your character uses the direction arrow, while attackable NPCs and ground items use native dots. Dot colors start with RuneLite's original colors.

## Getting started

Once approved, the plugin will be available through RuneLite's Plugin Hub. Until then, you can build and run it from source with **Java 11** and **Gradle**:

```sh
gradle clean build
gradle run
```

These commands use the RuneLite development client. If your project has a Gradle wrapper, use `gradlew.bat` on Windows or `./gradlew` elsewhere.

## Known issues

Some tutor icons, including Fishing, Mining, and Hunter, may not follow the Tutors filter. Hunter Tutor may instead respond to Sailing facilities. These are still being investigated.

## Feedback

Please [open an issue](https://github.com/Humperd7nck/minimap-customizer/issues) if you find a bug or have a suggestion.

Licensed under [BSD 2-Clause](LICENSE).
