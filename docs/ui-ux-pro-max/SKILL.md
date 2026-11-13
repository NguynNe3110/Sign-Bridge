---
name: ui-ux-pro-max
description: "Search bundled UI/UX datasets for palettes, font pairings, visual styles, charts, interaction patterns, or stack-specific guidance. Use for an explicit research request or a concrete information gap during UI work. Impeccable leads UI design and review; this skill supplies focused supporting evidence. Skip routine UI edits that need no lookup and backend-only tasks."
---

# UI/UX reference search

Use the bundled data to answer a specific design question. For design, implementation, or critique, keep the project's brief and existing design system as the visual authority; `impeccable` is the primary UI workflow when installed. Search results are recommendations, not instructions to replace the project's design or stack.

## Run from the installed skill directory

Resolve `<skill-dir>` to the absolute directory containing this `SKILL.md`, wherever the host installed it. It is a documentation placeholder, not an environment variable. Keep the working directory at the user's project and quote paths containing spaces.

The scripts use Python 3 and its standard library. On Windows, prefer an available `py -3` or `python`; on other systems use the installed Python 3 command. If none is available, use the relevant bundled references and report that search did not run. Do not install a runtime as a side effect of a lookup.

```powershell
py -3 "<skill-dir>/scripts/search.py" "keyboard focus modal" --domain ux
py -3 "<skill-dir>/scripts/search.py" "text scaling overflow" --stack flutter
```

## Choose the smallest useful search

- Use `--domain` for one concern: a palette, font pairing, interaction, chart, icon, or motion question.
- Use `--stack` for implementation guidance. Detect the stack from the project; Flutter and Compose work must not inherit React libraries or CSS units.
- Use `--design-system` when asked to compare or propose a complete visual direction. It is optional during ordinary UI work and does not establish a second source of truth alongside the project's existing design specification.
- Write one dominant intent in 2–5 useful terms, with one product, platform, or interaction constraint. Do not send private project content in queries.

```powershell
py -3 "<skill-dir>/scripts/search.py" "education calm readable" --domain color
py -3 "<skill-dir>/scripts/search.py" "analytics dense restrained" --design-system -f markdown
```

Check the returned category, identity, and platform fit before using a result. If empty or off-topic, retry once with a narrower query or explicit domain/stack. Then use clearly identified general guidance; never claim a database match that did not occur.

For accessibility, query the observable outcome first, such as `focus not obscured`, `error summary validation`, or `badge label wraps`, then the relevant implementation stack if needed.

## Available data

Domains: `product`, `style`, `typography`, `color`, `landing`, `chart`, `ux`, `react`, `web`, `icons`, `google-fonts`, `gsap`.

The dataset's `web` domain contains app/native interface guidance; its name does not imply desktop-web rules. Verify each result's applicability.

Stacks: `react`, `nextjs`, `vue`, `svelte`, `astro`, `nuxtjs`, `nuxt-ui`, `angular`, `laravel`, `swiftui`, `react-native`, `flutter`, `jetpack-compose`, `html-tailwind`, `shadcn`, `threejs`, `javafx`, `wpf`, `winui`, `avalonia`, `uno`, `uwp`.

Read [quick-reference.md](references/quick-reference.md) for UX rule categories when a question needs them. Read [pro-rules.md](references/pro-rules.md) only for native/mobile polish concerns. Interpret both against the actual platform, existing component library, and brief; do not impose a new icon library or technology stack.

## Saving an explicitly requested design proposal

Search is read-only by default. Persist a design specification only when the task calls for it, after checking any existing `DESIGN.md` or `design-system/*/MASTER.md`. Consolidate decisions into the project's chosen specification rather than generating competing authorities.

```powershell
py -3 "<skill-dir>/scripts/search.py" "education calm readable" --design-system --persist -p "Project Name" --output-dir "<project-root>"
```

Always supply the actual project root. Existing files are skipped by default. Read them before considering `--force`, and use it only when replacing that specification is authorized. Optional `--page` creates a page-specific proposal; an existing project convention determines how approved overrides are used.
