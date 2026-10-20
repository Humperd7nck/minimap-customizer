# Publishing Minimap Customizer

## 1. Check the release

Run `gradle clean build` with Java 11, then `gradle run` to launch the development client. Check that the plugin loads as **Minimap Customizer**, the settings look right, and disabling it restores the normal minimap.

Before publishing, verify that you have permission to distribute the bundled icon artwork.

## 2. Publish the source with GitHub Desktop

1. Extract the source ZIP to a folder named `minimap-customizer`.
2. In GitHub Desktop, choose **File → Add local repository**. If the folder isn't a Git repository yet, choose **Create a Repository** when prompted.
3. Commit the files, then choose **Publish repository**. Keep the repository name `minimap-customizer` and make it **public**.
4. Wait for the **Java build** check under GitHub Actions to pass.

The repository will be at [github.com/Humperd7nck/minimap-customizer](https://github.com/Humperd7nck/minimap-customizer) once published.

## 3. Submit to RuneLite Plugin Hub

Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub) and create a branch. In your fork, add a file named `plugins/minimap-customizer` with these two lines:

```properties
repository=https://github.com/Humperd7nck/minimap-customizer.git
commit=REPLACE_WITH_FULL_COMMIT_SHA
```

Use the full 40-character SHA of the commit that passed the build check. You can copy it from your repository's commits page on GitHub, or run `git rev-parse HEAD` in the repository folder.

Open a pull request to the official Plugin Hub repository. RuneLite will run its own checks and review the source before listing the plugin. See the [Plugin Hub instructions](https://github.com/runelite/plugin-hub#submitting-a-plugin) for details.

*Submit the source repository and commit hash, not a local plugin JAR.*
