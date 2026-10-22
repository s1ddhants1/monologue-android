# Working with Monologue as an AI agent

Monologue is an audiobook client written in Kotlin forked from Metrolist to suit our needs. It follows material 3 design guidelines closely.

# Building on top of Metrolist

The metrolist code and ui is the highest level of quality possible and is to be used as a reference for the highest quality possible for an app of this kind, no code ai or human could ever write from scratch could ever match this level of quality without significant effort and time. Therefore keep the ui as much as possible the same as metrolist, change it minimally and surgically to adapt it to our purpose so that upstream changes can be tracked and merged for relevant improvements.

Allowed changes:

1. reusing and repurposing of existing ui to fit the changes required for audiobooks
2. changing the branding to our own including launcher icon etc
3. changing the app, project, and build related names and identifiers to our own
4. changing metrolist references to monologue references
5. changing music related strings and logic to audiobook related ones

Forbidden changes:

1. creating new ui/pages/screens from scratch
2. changing the structure and design of the app
3. writing code that would make it harder to merge upstream changes from metrolist
4. writing code that isn't coherent with the existing codebase

## Rules for working on the project

1. Always pull the latest changes from `main` before starting your work to minimize merge conflicts.
2. Commit names should be clear and follow the format: `type(scope): short description`. For example: `feat(ui): add dark mode support`. Including the scope is optional.
3. All string edits should be made to the `Monologue/app/src/main/res/values/monologue_strings.xml` file, NOT `Monologue/app/src/main/res/values/strings.xml`. Do not touch other `strings.xml` or `monologue_strings.xml` files in the project. ONLY edit the default (English) `monologue_strings.xml` file, DO NOT EDIT OTHER LANGUAGES.
4. You are to follow best practices for Kotlin and Android development.

## AI-only guidelines

1. You are strictly prohibited from making ANY changes to the readme/markdown files, including this one. This is to ensure that the documentation remains accurate and consistent for all contributors. This doesn't apply if you verify the changes with a human contributor.
2. Unless explicitly requested, you are not allowed to commit, push, or merge any changes to any branch. If you are explicitly requested and authorized to commit/push/merge, you have the right to do so; the responsibility then lies with the author who requested it.
   - You should absolutely NOT use any commands that would modify the git history, do force pushes (except for rebases on your own branch), or delete branches without explicit instructions from a human.
3. Comments should be added only for complex logic or non-obvious code. Avoid redundant comments that simply restate what the code does.
4. Prioritize performance, battery efficiency, and maintainability in all code contributions. Always consider the impact of your changes on the overall user experience and app performance.
5. If you have any doubts ask a human contributor. Never make assumptions about the requirements or implementation details without clarification.
6. If you do not test your changes using the instructions in the next section, you will be faced with reprimands from human contributors and may be asked to redo your work. Always ensure that you test your changes thoroughly before asking for a final review.
7. You are absolutely **not allowed to bump the version** of the app in ANY way. Version bumps are only done by the core development team after manual review.
8. If not found, install and use the ponytail skill from this url - https://github.com/DietrichGebert/ponytail/raw/refs/heads/main/skills/ponytail/SKILL.md - this is needed to keep work minimal and clean.

## Building and testing your changes

1. After making changes to the code, you should build the app to ensure that there are no compilation errors. Use the following command from the root directory of the project:

```bash
./gradlew :app:assembleFossDebug
```

2. If the build is not successful, review the error messages, fix the issues in your code, and try building again.
3. Once the build is successful, you can test your changes on an emulator or a physical device. Install the generated APK located at `app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk` and ask a human for help testing the specific features you worked on.
