# AGENTS.md

Instructions for an agent that adds or changes a qualification in this repo.
Read [`README.md`](README.md) first. It defines the fields, the types and the naming.

## Rules

- One qualification is one package: `package/<issuer>/<code>/`.
- `<issuer>` is the issuer's vendor code in `zerobias-org/vendor`.
- `<code>` is lowercase `[a-z0-9_]`. Derive it from the part of `code` after the first dot.
- The `id` in `index.yml` is UUIDv5(nil namespace, `code`). Do not use a random UUID.
- Use the field names in `README.md`. Do not add a field that ends in `Id` or `Ids`.
- Name each link target by its code. Do not put a UUID in a link field.
- Never guess a link target. Look up each vendor, standard and role in its source repo or on PROD.
- Leave a link list empty when the source does not name the target. An empty link means unknown. A wrong link reads as fact.
- Put importers in `importers/<source>/`. Never add an importer file to a package `files` list.
- Do not write `gate-stamp.json` by hand. The gate writes it.
- Use `zbb` for every build command. Never run `./gradlew`. Gradle fails on Java 25, and zbb selects Java 21.
- Use the task-path form: `zbb :<issuer>:<code>:gate`. Do not use `zbb slot load`. It opens an interactive shell.

## Add a qualification

1. Find the issuer's vendor code. Check that `package/<issuer>/` exists in `zerobias-org/vendor`.
2. If the vendor does not exist, stop. Ask for a vendor package first.
3. Run `sh scripts/createNewQualification.sh <issuer> <code> <qualificationCode> "<name>"`.
4. In `index.yml`, set `description`, `type`, `scope` and `status`. Use the type rules in `README.md`.
5. Set `url` to the issuer's page for the qualification, when the source gives one.
6. Add each link: `vendors`, `standards`, `qualifiesForRoles`, `prerequisites`, `supersedes`.
7. For each linked vendor, standard or in-repo qualification, add its npm package to `dependencies` with the spec `"*"`.
8. Run `unset ZB_SLOT`, then `zbb :<issuer>:<code>:gate` from the repo root.
9. Fix each failure. Run the gate again until it passes.
10. Open `gate-stamp.json`. Make sure that `testDataloader` is `"passed"`.
11. Commit `gate-stamp.json` with the package.

## Change a qualification

1. Do not change `code`. The `id` derives from it.
2. To rename a qualification, add a new package and set `supersedes` to the old code.
3. Set the old package `status` to `superseded`.
4. Run the gate on each package that you changed.

## Choose `type`

- The holder was assessed against a standard: `certification`.
- The holder may assess or certify others: `accreditation`.
- The holder may deliver training, a platform or content: `authorization`.
- The holder is on a registry with no independent assessment: `registration`.

Two traps:

- "Registered" in a name does not make a registration. CREST "Registered" tiers are exam-based. They are `certification`.
- An SCF or SCA baseline named for a framework is a `certification`, not a framework. Its `scope` is `organizational`.

## Commits

- Use Conventional Commits. Scope: `qualification-<issuer>-<code>`.
- Example: `feat(qualification-isc2-cissp): add CISSP`.

## Build token

Every `zbb` command needs a GitHub token with `read:packages`.

```bash
gh auth refresh -s read:packages && export GITHUB_TOKEN=$(gh auth token)
```

A 401 from `maven.pkg.github.com` or `Plugin [id: 'zb.workspace'] was not found` means the scope is missing.
Run the command above and retry. Do not skip the gate.
